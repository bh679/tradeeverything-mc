package games.brennan.tradeeverything.mixin;

import games.brennan.tradeeverything.TradeEverything;
import games.brennan.tradeeverything.trade.OfferQuoter;
import games.brennan.tradeeverything.trade.OfferResync;
import games.brennan.tradeeverything.trade.RepriceSuppression;
import games.brennan.tradeeverything.trade.SyntheticOfferFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Two server-side jobs around {@code MerchantMenu.tryMoveItems} — the method
 * that runs when the player clicks a trade row (vanilla ejects the payment
 * slots, then auto-fills them from the inventory against the row's cost).
 *
 * <ul>
 *   <li><b>Click-to-fill the Trade Anything row.</b> While its slot is empty the
 *       row previews an item the player is carrying ({@code PlaceholderIconCycle}),
 *       but its cost is the unmatchable named-barrier placeholder, so vanilla's
 *       fill finds nothing. Swap offer 0 to the real quote for that item before
 *       the fill runs; the quote's predicate matches the player's own stack and
 *       vanilla moves it into the slot exactly as it would for a real row.</li>
 *   <li><b>Emerald blocks as big currency</b>, broken only at purchase time: if
 *       the clicked offer is emerald-priced and the player's loose emeralds don't
 *       cover it, break the minimum number of emerald blocks first. Vanilla's
 *       fill then moves exactly what the trade needs; the rest stays in the
 *       inventory as change and untouched blocks stay blocks.</li>
 * </ul>
 */
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin {

    /** Set while a click-fill swapped offer 0, so the RETURN hook knows to resync. */
    @Unique
    private boolean tradeeverything$filledFromPreview;

    /**
     * Clicking the Trade Anything row (index 0) has vanilla eject the payment
     * slots before refilling them; without suppression the eject empties the
     * slot, repricing resets offer 0 to the unmatchable placeholder, and the
     * refill dead-ends — the row spat the payment out. Suppress repricing for
     * the whole tryMoveItems pass so the priced offer survives the round trip.
     * Then, if the row is still a placeholder, price the previewed item so the
     * fill has something to match.
     */
    @Inject(method = "tryMoveItems", at = @At("HEAD"))
    private void tradeeverything$suppressBegin(int selectedIndex, CallbackInfo ci) {
        if (selectedIndex != 0) return;
        MerchantMenuAccessor accessor = (MerchantMenuAccessor) this;
        if (!(accessor.tradeeverything$getTrader() instanceof AbstractVillager villager)) return;
        if (villager.level().isClientSide()) return;
        MerchantOffers offers = villager.getOffers();
        if (offers.isEmpty() || !SyntheticOfferFactory.isSynthetic(offers.get(0))) return;
        RepriceSuppression.begin();
        try {
            tradeeverything$fillFromPreview(villager, offers);
        } catch (Throwable t) {
            // Never propagate into the select-trade packet handler.
            TradeEverything.LOGGER.warn("[TradeEverything] click-to-fill failed; row left as-is", t);
        }
    }

    @Inject(method = "tryMoveItems", at = @At("RETURN"))
    private void tradeeverything$suppressEnd(int selectedIndex, CallbackInfo ci) {
        RepriceSuppression.end();
        if (!tradeeverything$filledFromPreview) return;
        tradeeverything$filledFromPreview = false;
        MerchantMenuAccessor accessor = (MerchantMenuAccessor) this;
        if (accessor.tradeeverything$getTrader() instanceof AbstractVillager villager) {
            OfferResync.send(villager);
        }
    }

    /**
     * Offer 0 is a placeholder showing {@code icon}: if the player carries that
     * item, quote their stack (the first one in inventory order — the same stack
     * {@code InventoryIconPool} previewed) and install the quote as offer 0.
     * A registry-fallback icon the player doesn't carry, an exempt item, or the
     * flat chest placeholder leaves the row untouched and vanilla finds nothing,
     * exactly as before.
     */
    @Unique
    private void tradeeverything$fillFromPreview(AbstractVillager villager, MerchantOffers offers) {
        MerchantOffer current = offers.get(0);
        if (!SyntheticOfferFactory.isPlaceholder(current)) return;
        if (!(villager.getTradingPlayer() instanceof ServerPlayer player)) return;

        Item icon = current.getItemCostA().item().value();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(icon)) continue;
            Optional<MerchantOffer> quote = OfferQuoter.quote(villager, stack, offers);
            if (quote.isEmpty()) return;
            offers.set(0, quote.get());
            tradeeverything$filledFromPreview = true;
            return;
        }
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
