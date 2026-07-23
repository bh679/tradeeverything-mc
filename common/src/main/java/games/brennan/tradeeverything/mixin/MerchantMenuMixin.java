package games.brennan.tradeeverything.mixin;

import games.brennan.tradeeverything.config.TradeEverythingConfig;
import games.brennan.tradeeverything.trade.ItemValuation;
import games.brennan.tradeeverything.trade.OfferResync;
import games.brennan.tradeeverything.trade.SyntheticOfferFactory;
import games.brennan.tradeeverything.trade.TradePricer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dynamic repricing of the synthetic offer. Server-side, after vanilla's
 * {@code updateSellItem()}: read the payment slot, rewrite offer 0 to
 * (N × inserted item → M × payout), re-run the sell-item computation and
 * resync offers to the client. Change-detection on the inserted item
 * (ignoring count) keeps this idempotent and packet-quiet.
 */
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin {

    @Unique
    private ItemStack tradeeverything$lastInput = ItemStack.EMPTY;

    @Inject(method = "slotsChanged", at = @At("TAIL"))
    private void tradeeverything$reprice(Container container, CallbackInfo ci) {
        MerchantMenuAccessor accessor = (MerchantMenuAccessor) this;
        if (!(accessor.tradeeverything$getTrader() instanceof AbstractVillager villager)) return;
        if (villager.level().isClientSide()) return;

        MerchantOffers offers = villager.getOffers();
        if (offers.isEmpty() || !SyntheticOfferFactory.isSynthetic(offers.get(0))) return;

        MerchantContainer tradeContainer = accessor.tradeeverything$getTradeContainer();
        ItemStack slotA = tradeContainer.getItem(0);
        ItemStack input = slotA.isEmpty() ? tradeContainer.getItem(1) : slotA;

        // The cost count depends only on item + components, not inserted count.
        if (ItemStack.isSameItemSameComponents(input, tradeeverything$lastInput)) return;
        tradeeverything$lastInput = input.isEmpty() ? ItemStack.EMPTY : input.copyWithCount(1);

        Item payout = ItemValuation.selectBuyItem(villager, offers);
        MerchantOffer replacement = input.isEmpty()
            ? SyntheticOfferFactory.placeholder(payout)
            : TradePricer.quote(input, payout, TradeEverythingConfig.get())
                .map(quote -> SyntheticOfferFactory.priced(input, quote.costCount(), payout, quote.resultCount()))
                .orElseGet(() -> SyntheticOfferFactory.placeholder(payout));

        offers.set(0, replacement);
        tradeContainer.updateSellItem();
        OfferResync.send(((MerchantMenu) (Object) this).containerId, villager);
    }
}
