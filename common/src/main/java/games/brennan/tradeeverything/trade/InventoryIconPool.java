package games.brennan.tradeeverything.trade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The player-specific half of the "Trade Anything" icon cycle: the items the
 * player is carrying right now that this villager would actually accept.
 *
 * <p>Cycling these instead of the whole registry ({@link IconCyclePool}) turns
 * the row from "any item goes here" into "any of <em>these</em>, right now" —
 * every icon the player sees is a trade they could make without closing the
 * screen. Carrying nothing tradeable gives an empty list, and the caller falls
 * back to the registry-wide cycle.</p>
 *
 * <p>Tradeability is decided by {@link OfferQuoter#quote} — the same call that
 * prices the row once an item is in the slot — so the icon can never advertise
 * a trade the slot would then refuse.</p>
 */
public final class InventoryIconPool {

    private InventoryIconPool() {}

    /**
     * Distinct items from the player's main inventory and offhand that this
     * villager would quote, in inventory order (hotbar first, as vanilla stores
     * it), skipping anything in {@code excluded}.
     *
     * <p>Rebuilt per call rather than cached: it depends on the inventory, which
     * changes under the open screen. The work is bounded by the 37 stacks
     * scanned — deduplicated to at most 37 {@code quote} calls, each a handful
     * of lookups into pre-indexed value maps.</p>
     */
    public static List<Item> candidates(ServerPlayer player,
                                        AbstractVillager villager,
                                        MerchantOffers offers,
                                        Set<Item> excluded) {
        List<Item> candidates = new ArrayList<>();
        Set<Item> seen = new HashSet<>();
        collect(player.getInventory().items, villager, offers, excluded, seen, candidates);
        collect(player.getInventory().offhand, villager, offers, excluded, seen, candidates);
        return candidates;
    }

    private static void collect(List<ItemStack> stacks,
                                AbstractVillager villager,
                                MerchantOffers offers,
                                Set<Item> excluded,
                                Set<Item> seen,
                                List<Item> out) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            // Deduplicate on the item alone: the icon is an item, so a second
            // stack of the same item — however differently enchanted or damaged
            // — would only repeat a picture the cycle already shows.
            if (excluded.contains(item) || !seen.add(item)) continue;
            if (OfferQuoter.quote(villager, stack, offers).isPresent()) out.add(item);
        }
    }
}
