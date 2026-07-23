package games.brennan.tradeeverything.mixin;

import games.brennan.tradeeverything.config.TradeEverythingConfig;
import games.brennan.tradeeverything.trade.ItemValuation;
import games.brennan.tradeeverything.trade.OfferResync;
import games.brennan.tradeeverything.trade.RecipeValues;
import games.brennan.tradeeverything.trade.SyntheticOfferFactory;
import games.brennan.tradeeverything.trade.TradeExemptions;
import games.brennan.tradeeverything.trade.TradePricer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dynamic repricing of the synthetic offer, hooked at the single funnel point
 * every payment-slot mutation goes through: {@code MerchantContainer.updateSellItem()}
 * (both {@code setChanged()} and {@code MerchantMenu.slotsChanged} call it —
 * plain slot clicks never reach {@code slotsChanged}, which is why the hook
 * lives here and not on the menu).
 *
 * <p>HEAD injection: rewrite offer 0 to (N × inserted item → M × payout)
 * first, then let vanilla's own body compute the sell slot against the fixed
 * offer. Change-detection on the inserted item (ignoring count) keeps this
 * idempotent and packet-quiet.</p>
 */
@Mixin(MerchantContainer.class)
public abstract class MerchantContainerMixin {

    @Shadow
    @Final
    private Merchant merchant;

    @Unique
    private ItemStack tradeeverything$lastInput = ItemStack.EMPTY;

    @Inject(method = "updateSellItem", at = @At("HEAD"))
    private void tradeeverything$reprice(CallbackInfo ci) {
        if (!(merchant instanceof AbstractVillager villager)) return;
        if (villager.level().isClientSide()) return;
        if (villager.level().getServer() != null) {
            RecipeValues.ensureIndexed(villager.level().getServer());
        }

        MerchantOffers offers = villager.getOffers();
        if (offers.isEmpty() || !SyntheticOfferFactory.isSynthetic(offers.get(0))) return;

        MerchantContainer self = (MerchantContainer) (Object) this;
        ItemStack slotA = self.getItem(0);
        ItemStack input = slotA.isEmpty() ? self.getItem(1) : slotA;

        // The cost count depends only on item + components, not inserted count.
        if (ItemStack.isSameItemSameComponents(input, tradeeverything$lastInput)) return;
        tradeeverything$lastInput = input.isEmpty() ? ItemStack.EMPTY : input.copyWithCount(1);

        Item payout = ItemValuation.selectBuyItem(villager, offers);
        MerchantOffer replacement = input.isEmpty() || TradeExemptions.isExempt(input.getItem(), offers)
            ? SyntheticOfferFactory.placeholder(payout)
            : TradePricer.quote(input, payout, TradeEverythingConfig.get())
                .map(quote -> SyntheticOfferFactory.priced(input, quote.costCount(), payout, quote.resultCount()))
                .orElseGet(() -> SyntheticOfferFactory.placeholder(payout));

        offers.set(0, replacement);
        OfferResync.send(villager);
    }
}
