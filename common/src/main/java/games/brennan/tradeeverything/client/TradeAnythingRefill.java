package games.brennan.tradeeverything.client;

import games.brennan.tradeeverything.trade.SyntheticOfferFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Client-side space-bar refill of the Trade Anything slot.
 *
 * <p>Vanilla refills a trade's payment slots for you ({@code tryMoveItems} →
 * {@code moveFromInventoryToPaymentSlot}, matching the offer's {@code ItemCost}
 * against your inventory). The synthetic row can never benefit from that: with
 * the slot empty its cost is the deliberately unmatchable named-barrier
 * placeholder, so vanilla finds nothing to move. This replays the clicks a
 * player would make by hand instead — pick a stack up, drop it on payment slot
 * 0 — so every mutation still goes through the ordinary container-click packet
 * and is server-validated. No custom packets, no server-side counterpart.</p>
 *
 * <p>CLIENT ONLY. Reachable exclusively from the {@code client} mixin list in
 * {@code tradeeverything.mixins.json}; a dedicated server never loads it.</p>
 */
public final class TradeAnythingRefill {

    /** Payment slot A — where the synthetic offer takes its cost from. */
    private static final int PAYMENT_SLOT = 0;

    /**
     * Last item seen in the Trade Anything slot, count-normalised to 1: item +
     * components are the whole identity here, exactly as the server keys its
     * repricing on ({@code MerchantContainerMixin}). Static, so it survives
     * closing and reopening the screen within a client session.
     */
    private static ItemStack lastTraded = ItemStack.EMPTY;

    /** Container id the {@link #hasTradeSlot} latch below belongs to. */
    private static int latchedContainerId = -1;

    /**
     * Whether the open merchant is running a Trade Anything row. Latched while
     * the slot is empty (the only moment the row is identifiable client-side —
     * once priced, its cost is an ordinary item) and held for that container.
     */
    private static boolean hasTradeSlot;

    private TradeAnythingRefill() {}

    /**
     * Per-tick observation while a merchant screen is open: latch whether this
     * merchant has a Trade Anything row, and remember whatever is sitting in it.
     */
    public static void remember(MerchantMenu menu) {
        if (menu.containerId != latchedContainerId) {
            latchedContainerId = menu.containerId;
            hasTradeSlot = false;
        }

        MerchantOffers offers = menu.getOffers();
        if (!offers.isEmpty() && SyntheticOfferFactory.hasPlaceholderCost(offers.get(0))) {
            hasTradeSlot = true;
        }
        if (!hasTradeSlot) return;

        ItemStack inSlot = menu.getSlot(PAYMENT_SLOT).getItem();
        if (!inSlot.isEmpty()) {
            lastTraded = inSlot.copyWithCount(1);
        }
    }

    /**
     * Top the Trade Anything slot back up with the last item traded through it,
     * to the largest stack the player is carrying. No-op unless the slot is
     * genuinely refillable — wrong merchant, unknown item, occupied cursor, a
     * different item already in the slot or an already-full slot all bail out.
     */
    public static void refill(Minecraft minecraft, MerchantMenu menu) {
        Player player = minecraft.player;
        MultiPlayerGameMode gameMode = minecraft.gameMode;
        if (player == null || gameMode == null) return;
        if (!hasTradeSlot || lastTraded.isEmpty()) return;
        if (!menu.getCarried().isEmpty()) return;

        Slot target = menu.getSlot(PAYMENT_SLOT);
        ItemStack inSlot = target.getItem();
        if (!inSlot.isEmpty() && !ItemStack.isSameItemSameComponents(inSlot, lastTraded)) return;

        int capacity = target.getMaxStackSize(lastTraded);
        if (inSlot.getCount() >= capacity) return;

        for (int index = 0; index < menu.slots.size(); index++) {
            if (target.getItem().getCount() >= capacity) return;

            Slot source = menu.slots.get(index);
            if (source.container != player.getInventory()) continue;
            if (!ItemStack.isSameItemSameComponents(source.getItem(), lastTraded)) continue;

            click(gameMode, menu, index, player);          // stack onto the cursor
            click(gameMode, menu, PAYMENT_SLOT, player);   // as much of it as fits

            // Slot.safeInsert leaves the overflow on the cursor: the slot is
            // full, so hand the remainder back to where it came from and stop.
            if (!menu.getCarried().isEmpty()) {
                click(gameMode, menu, index, player);
                return;
            }
        }
    }

    private static void click(MultiPlayerGameMode gameMode, MerchantMenu menu, int slot, Player player) {
        gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, player);
    }
}
