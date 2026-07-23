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
import net.minecraft.world.item.Items;
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
        tradeeverything$makeChange(self, villager);
        ItemStack slotA = self.getItem(0);
        ItemStack input = slotA.isEmpty() ? self.getItem(1) : slotA;

        // The cost count depends only on item + components, not inserted count.
        if (ItemStack.isSameItemSameComponents(input, tradeeverything$lastInput)) return;
        tradeeverything$lastInput = input.isEmpty() ? ItemStack.EMPTY : input.copyWithCount(1);

        Item preferred = ItemValuation.selectBuyItem(villager, offers);
        Item payout = input.isEmpty() ? preferred : TradePricer.payoutFor(input, preferred, TradeEverythingConfig.get());
        MerchantOffer replacement = input.isEmpty() || TradeExemptions.isExempt(input.getItem(), offers)
            ? SyntheticOfferFactory.placeholder(payout)
            : TradePricer.quote(input, payout, TradeEverythingConfig.get())
                .map(quote -> SyntheticOfferFactory.priced(input, quote.costCount(), payout, quote.resultCount()))
                .orElseGet(() -> SyntheticOfferFactory.placeholder(payout));

        offers.set(0, replacement);
        OfferResync.send(villager);
    }

    /**
     * Emerald blocks are currency, not merchandise: a block placed in a
     * payment slot converts to 9 emeralds (max 7 blocks → 63, the stack cap),
     * so it spends in any emerald-priced trade with the remainder returned as
     * change; overflow blocks go back to the player's inventory. Guarded
     * against re-entry because setItem re-triggers updateSellItem.
     */
    @Unique
    private boolean tradeeverything$converting;

    @Unique
    private void tradeeverything$makeChange(MerchantContainer self, AbstractVillager villager) {
        if (tradeeverything$converting) return;
        for (int slot = 0; slot <= 1; slot++) {
            ItemStack stack = self.getItem(slot);
            if (!stack.is(Items.EMERALD_BLOCK)) continue;
            int blocks = stack.getCount();
            int convertible = Math.min(blocks, 7);
            tradeeverything$converting = true;
            try {
                self.setItem(slot, new ItemStack(Items.EMERALD, convertible * 9));
            } finally {
                tradeeverything$converting = false;
            }
            if (blocks > convertible && villager.getTradingPlayer() != null) {
                villager.getTradingPlayer().getInventory()
                    .placeItemBackInInventory(new ItemStack(Items.EMERALD_BLOCK, blocks - convertible));
            }
        }
    }
}
