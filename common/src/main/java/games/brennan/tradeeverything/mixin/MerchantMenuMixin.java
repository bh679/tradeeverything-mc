package games.brennan.tradeeverything.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import games.brennan.tradeeverything.trade.RepriceSuppression;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emerald blocks as big currency, broken only at purchase time.
 *
 * <p>{@code tryMoveItems} runs server-side when the player clicks a trade
 * row (vanilla then auto-fills the payment slots from the inventory). Before
 * that fill: if the clicked offer is emerald-priced and the player's loose
 * emeralds don't cover it, break the minimum number of emerald blocks in the
 * player's inventory into emeralds. Vanilla's auto-fill then moves exactly
 * what the trade needs; the rest stays in the inventory as change and
 * untouched blocks stay blocks.</p>
 */
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin {

    /**
     * Clicking the Trade Anything row (index 0) has vanilla eject the payment
     * slots before refilling them; without suppression the eject empties the
     * slot, repricing resets offer 0 to the unmatchable placeholder, and the
     * refill dead-ends — the row spat the payment out. Suppress repricing for
     * the whole tryMoveItems pass so the priced offer survives the round trip.
     */
    @Inject(method = "tryMoveItems", at = @At("HEAD"))
    private void tradeeverything$suppressBegin(int selectedIndex, CallbackInfo ci) {
        if (selectedIndex != 0) return;
        MerchantMenuAccessor accessor = (MerchantMenuAccessor) this;
        if (!(accessor.tradeeverything$getTrader() instanceof AbstractVillager villager)) return;
        if (villager.level().isClientSide()) return;
        MerchantOffers offers = villager.getOffers();
        if (offers.isEmpty() || !games.brennan.tradeeverything.trade.SyntheticOfferFactory.isSynthetic(offers.get(0))) return;
        RepriceSuppression.begin();
    }

    @Inject(method = "tryMoveItems", at = @At("RETURN"))
    private void tradeeverything$suppressEnd(int selectedIndex, CallbackInfo ci) {
        RepriceSuppression.end();
    }

    @Inject(method = "tryMoveItems", at = @At("HEAD"))
    private void tradeeverything$breakEmeraldBlocks(int selectedIndex, CallbackInfo ci) {
        MerchantMenuAccessor accessor = (MerchantMenuAccessor) this;
        if (!(accessor.tradeeverything$getTrader() instanceof AbstractVillager villager)) return;
        if (villager.level().isClientSide()) return;
        if (!(villager.getTradingPlayer() instanceof ServerPlayer player)) return;

        MerchantOffers offers = villager.getOffers();
        if (selectedIndex < 0 || selectedIndex >= offers.size()) return;
        MerchantOffer offer = offers.get(selectedIndex);

        int needed = tradeeverything$emeraldsNeeded(offer);
        if (needed <= 0) return;

        // Loose emeralds: inventory + whatever sits in the payment slots
        // (vanilla returns those to the inventory before refilling).
        int loose = player.getInventory().clearOrCountMatchingItems(
            stack -> stack.is(Items.EMERALD), 0, player.inventoryMenu.getCraftSlots());
        MerchantContainer tradeContainer = accessor.tradeeverything$getTradeContainer();
        for (int slot = 0; slot <= 1; slot++) {
            ItemStack inSlot = tradeContainer.getItem(slot);
            if (inSlot.is(Items.EMERALD)) loose += inSlot.getCount();
        }
        if (loose >= needed) return;

        int blocksNeeded = (needed - loose + 8) / 9;
        int broken = player.getInventory().clearOrCountMatchingItems(
            stack -> stack.is(Items.EMERALD_BLOCK), blocksNeeded, player.inventoryMenu.getCraftSlots());
        if (broken > 0) {
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, broken * 9));
        }
    }

    /** Emeralds this offer charges (discounted A-cost + B-cost). */
    private static int tradeeverything$emeraldsNeeded(MerchantOffer offer) {
        int needed = 0;
        ItemStack costA = offer.getCostA();
        if (costA.is(Items.EMERALD)) needed += costA.getCount();
        if (offer.getItemCostB().isPresent() && offer.getItemCostB().get().item().value() == Items.EMERALD) {
            needed += offer.getItemCostB().get().count();
        }
        return needed;
    }
}
