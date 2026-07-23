package games.brennan.tradeeverything.mixin;

import games.brennan.tradeeverything.TradeEverything;
import games.brennan.tradeeverything.config.TradeEverythingConfig;
import games.brennan.tradeeverything.trade.ItemValuation;
import games.brennan.tradeeverything.trade.SyntheticOfferFactory;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Session-scoped injection of the synthetic "Trade Anything" offer.
 *
 * <p>EVERY inject body here is exception-guarded. The open path runs inside
 * {@code Villager.startTrading} after {@code tradingPlayer} is assigned but
 * before the menu opens — an escaping throw there leaves the villager in a
 * phantom trading session forever ({@code mobInteract} gates on
 * {@code isTrading()}), which presented in the field as "can't open the trade
 * menu any more".</p>
 *
 * <ul>
 *   <li>{@code setTradingPlayer(player)} — prepend the flagged offer at index 0
 *       when a session starts, remove it when it ends. The offer must live in
 *       the entity's REAL offers list: server-side trade matching
 *       ({@code MerchantOffers.getRecipeFor} + selection-hint indices) runs
 *       against that list. Runs after {@code updateSpecialPrices}, so
 *       gossip/hero discounts never touch it.</li>
 *   <li>{@code addAdditionalSaveData} — the {@code getOffers()} the save path
 *       encodes is redirected to a synthetic-free COPY, so the persisted NBT
 *       never contains the injected offer and the live list is never mutated
 *       during a save (throw-safe, autosave-mid-trade-safe).</li>
 *   <li>{@code notifyTrade} — reset uses so the slot never goes out of stock
 *       and never trips {@code needsToRestock()}.</li>
 * </ul>
 */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerTradingMixin {

    @Shadow
    @Nullable
    protected MerchantOffers offers;

    @Inject(method = "setTradingPlayer", at = @At("TAIL"))
    private void tradeeverything$onSetTradingPlayer(@Nullable Player player, CallbackInfo ci) {
        try {
            AbstractVillager self = (AbstractVillager) (Object) this;
            if (self.level().isClientSide()) return;

            if (player == null) {
                // Session end: always clean up, regardless of config (a live
                // config flip mid-session must not leak the offer).
                if (this.offers != null) {
                    this.offers.removeIf(SyntheticOfferFactory::isSynthetic);
                }
                return;
            }

            if (self instanceof WanderingTrader && !TradeEverythingConfig.get().enableWanderingTrader()) return;
            MerchantOffers current = self.getOffers();
            if (current.isEmpty() || !SyntheticOfferFactory.isSynthetic(current.get(0))) {
                Item payout = ItemValuation.selectBuyItem(self, current);
                current.add(0, SyntheticOfferFactory.placeholder(payout));
            }
        } catch (Throwable t) {
            // Degrade to vanilla trading — never propagate into startTrading.
            TradeEverything.LOGGER.warn("[TradeEverything] trade-slot injection failed; vanilla trading only", t);
        }
    }

    @Redirect(
        method = "addAdditionalSaveData",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/npc/AbstractVillager;getOffers()Lnet/minecraft/world/item/trading/MerchantOffers;")
    )
    private MerchantOffers tradeeverything$saveWithoutSynthetic(AbstractVillager self) {
        MerchantOffers real = self.getOffers();
        try {
            if (real.stream().noneMatch(SyntheticOfferFactory::isSynthetic)) return real;
            MerchantOffers filtered = new MerchantOffers();
            for (MerchantOffer offer : real) {
                if (!SyntheticOfferFactory.isSynthetic(offer)) filtered.add(offer);
            }
            return filtered;
        } catch (Throwable t) {
            TradeEverything.LOGGER.warn("[TradeEverything] save filter failed; saving offers as-is", t);
            return real;
        }
    }

    @Inject(method = "notifyTrade", at = @At("TAIL"))
    private void tradeeverything$afterTrade(MerchantOffer offer, CallbackInfo ci) {
        if (SyntheticOfferFactory.isSynthetic(offer)) {
            offer.resetUses();
        }
    }
}
