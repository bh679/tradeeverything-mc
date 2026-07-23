package games.brennan.tradeeverything.mixin;

import games.brennan.tradeeverything.config.TradeEverythingConfig;
import games.brennan.tradeeverything.trade.ItemValuation;
import games.brennan.tradeeverything.trade.SyntheticOfferFactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Session-scoped injection of the synthetic "Trade Anything" offer.
 *
 * <ul>
 *   <li>{@code setTradingPlayer(player)} — prepend the flagged offer at index 0
 *       when a session starts, remove it when it ends. The offer must live in
 *       the entity's REAL offers list: server-side trade matching
 *       ({@code MerchantOffers.getRecipeFor} + selection-hint indices) runs
 *       against that list, so display-only injection would desync indices.
 *       Runs after {@code updateSpecialPrices}, so gossip/hero discounts never
 *       touch the synthetic offer.</li>
 *   <li>{@code addAdditionalSaveData} — strip on save, restore after, so the
 *       synthetic offer never reaches NBT (covers autosave mid-trade).</li>
 *   <li>{@code notifyTrade} — reset uses so the slot never goes out of stock
 *       and never trips {@code needsToRestock()}.</li>
 * </ul>
 */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerTradingMixin {

    @Shadow
    @Nullable
    protected MerchantOffers offers;

    @Unique
    @Nullable
    private MerchantOffer tradeeverything$stashed;

    @Inject(method = "setTradingPlayer", at = @At("TAIL"))
    private void tradeeverything$onSetTradingPlayer(@Nullable Player player, CallbackInfo ci) {
        AbstractVillager self = (AbstractVillager) (Object) this;
        if (self.level().isClientSide()) return;
        if (self instanceof WanderingTrader && !TradeEverythingConfig.get().enableWanderingTrader()) return;

        if (player != null) {
            MerchantOffers current = self.getOffers();
            if (current.isEmpty() || !SyntheticOfferFactory.isSynthetic(current.get(0))) {
                Item payout = ItemValuation.selectBuyItem(self, current);
                current.add(0, SyntheticOfferFactory.placeholder(payout));
            }
        } else if (this.offers != null) {
            this.offers.removeIf(SyntheticOfferFactory::isSynthetic);
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void tradeeverything$stripBeforeSave(CompoundTag tag, CallbackInfo ci) {
        if (this.offers != null && !this.offers.isEmpty() && SyntheticOfferFactory.isSynthetic(this.offers.get(0))) {
            tradeeverything$stashed = this.offers.remove(0);
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void tradeeverything$restoreAfterSave(CompoundTag tag, CallbackInfo ci) {
        if (tradeeverything$stashed != null && this.offers != null) {
            this.offers.add(0, tradeeverything$stashed);
        }
        tradeeverything$stashed = null;
    }

    @Inject(method = "notifyTrade", at = @At("TAIL"))
    private void tradeeverything$afterTrade(MerchantOffer offer, CallbackInfo ci) {
        if (SyntheticOfferFactory.isSynthetic(offer)) {
            offer.resetUses();
        }
    }
}
