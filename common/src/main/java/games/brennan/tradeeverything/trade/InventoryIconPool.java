package games.brennan.tradeeverything.trade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The items in the player's own inventory that this villager would actually
 * take in the "Trade Anything" slot.
 *
 * <p>These are what the placeholder icon cycles through first: an icon the
 * player can act on right now reads as an invitation, where a random registry
 * item reads as decoration. {@link IconCyclePool} stays the fallback for an
 * empty-handed player (or one carrying nothing tradeable), so the row still
 * says "any item goes here" when there is nothing better to show.</p>
 *
 * <p>Order is inventory order, deduplicated by item, so the sequence is stable
 * while the inventory is — no state to keep between ticks.</p>
 *
 * <p>The quote each candidate had to pass to get in is kept rather than thrown
 * away, so the row can show what the item actually fetches instead of a flat
 * placeholder count. Pricing the pool therefore costs nothing extra.</p>
 */
public final class InventoryIconPool {

    private InventoryIconPool() {}

    /**
     * A quote per distinct item in {@code player}'s inventory that
     * {@link OfferQuoter} can price for {@code villager}, skipping
     * {@code excluded} (everything the villager already trades). Empty if the
     * player has nothing tradeable.
     *
     * <p>Each quote prices that player's actual stack, so a damaged or enchanted
     * tool is valued as the one they are carrying.</p>
     */
    public static List<MerchantOffer> tradeable(ServerPlayer player, AbstractVillager villager,
                                                MerchantOffers offers, Set<Item> excluded) {
        Inventory inventory = player.getInventory();
        Set<Item> seen = new LinkedHashSet<>();
        List<MerchantOffer> candidates = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (excluded.contains(item) || !seen.add(item)) continue;
            OfferQuoter.quote(villager, stack, offers).ifPresent(candidates::add);
        }
        return candidates;
    }

    /** The quote shown for cycle {@code step}, offset by {@code salt} (the villager id). */
    public static MerchantOffer at(List<MerchantOffer> candidates, long step, int salt) {
        return candidates.get((int) Math.floorMod(step + salt, candidates.size()));
    }
}
