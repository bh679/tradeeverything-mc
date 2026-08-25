package games.brennan.tradeeverything.trade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 */
public final class InventoryIconPool {

    private InventoryIconPool() {}

    /**
     * Distinct items from {@code player}'s inventory that {@link OfferQuoter}
     * can price for {@code villager}, skipping {@code excluded} (everything the
     * villager already trades). Empty if the player has nothing tradeable.
     */
    public static List<Item> tradeable(ServerPlayer player, AbstractVillager villager,
                                       MerchantOffers offers, Set<Item> excluded) {
        Inventory inventory = player.getInventory();
        Set<Item> seen = new LinkedHashSet<>();
        List<Item> candidates = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (excluded.contains(item) || !seen.add(item)) continue;
            if (OfferQuoter.quote(villager, stack, offers).isPresent()) candidates.add(item);
        }
        return candidates;
    }

    /** The item shown for cycle {@code step}, offset by {@code salt} (the villager id). */
    public static Item at(List<Item> candidates, long step, int salt) {
        return candidates.get((int) Math.floorMod(step + salt, candidates.size()));
    }
}
