package games.brennan.tradeeverything.trade;

import games.brennan.tradeeverything.TradeEverything;
import games.brennan.tradeeverything.config.TradeEverythingConfig;
import games.brennan.tradeeverything.mixin.MerchantMenuAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps the "Trade Anything" row alive while its slot is empty.
 *
 * <p>Two states, both server-authoritative and both re-sent through
 * {@link OfferResync} (the same path repricing already uses):</p>
 * <ul>
 *   <li><b>Holding a tradeable item</b> — the row previews that exact trade,
 *       priced by {@link OfferQuoter}, so the player sees what they'd get
 *       before dropping it in. The cursor stack is server-side state, so this
 *       needs no client code and works on a vanilla client.</li>
 *   <li><b>Empty-handed</b> — the icon cycles through the tradeable items the
 *       player is actually carrying ({@link InventoryIconPool}), so the row
 *       shows what they could drop in right now; carrying nothing tradeable it
 *       falls back to cycling obtainable items at large ({@link IconCyclePool}),
 *       so the row still reads as "any item goes here". Either way everything
 *       the villager already trades is skipped.</li>
 * </ul>
 *
 * <p>Both are pure functions of state the tick can see (game time, villager id,
 * cursor stack, inventory contents), so there is nothing to track between ticks
 * and nothing to clean up — and once an item is actually in the slot, the row is
 * a real quote and this leaves it alone entirely.</p>
 */
public final class PlaceholderIconCycle {

    private PlaceholderIconCycle() {}

    /** Runs every server tick for every player; cheap until a merchant screen is open. */
    public static void tick(ServerPlayer player) {
        try {
            tickInner(player);
        } catch (Throwable t) {
            // Cosmetic feature — never propagate into the player tick loop.
            TradeEverything.LOGGER.warn("[TradeEverything] trade-slot preview failed", t);
        }
    }

    private static void tickInner(ServerPlayer player) {
        TradeEverythingConfig config = TradeEverythingConfig.get();
        if (!config.cyclePlaceholderIcon() && !config.previewHeldItem()) return;
        if (RepriceSuppression.isSuppressed()) return;
        if (!(player.containerMenu instanceof MerchantMenu menu)) return;
        MerchantMenuAccessor accessor = (MerchantMenuAccessor) menu;
        if (!(accessor.tradeeverything$getTrader() instanceof AbstractVillager villager)) return;

        MerchantOffers offers = villager.getOffers();
        if (offers.isEmpty()) return;
        MerchantOffer current = offers.get(0);
        if (!SyntheticOfferFactory.isSynthetic(current)) return;

        // An item in the payment slots means offer 0 is a real quote for it —
        // repricing owns the row from there.
        MerchantContainer container = accessor.tradeeverything$getTradeContainer();
        if (!container.getItem(0).isEmpty() || !container.getItem(1).isEmpty()) return;

        Set<Item> traded = tradedItems(offers);
        ItemStack carried = menu.getCarried();

        if (config.previewHeldItem() && !carried.isEmpty() && !traded.contains(carried.getItem())) {
            // Already previewing this exact stack (same item + components)? Done.
            if (!SyntheticOfferFactory.isPlaceholder(current) && current.getItemCostA().test(carried)) return;
            Optional<MerchantOffer> preview = OfferQuoter.quote(villager, carried, offers);
            if (preview.isPresent()) {
                offers.set(0, preview.get());
                OfferResync.send(villager);
                return;
            }
            // Untradeable in hand (exempt or worth too little) — fall through
            // and keep cycling rather than showing a dead row.
        }

        if (!config.cyclePlaceholderIcon()) {
            // Cycling off: still clear a stale preview once the hand empties.
            if (!SyntheticOfferFactory.isPlaceholder(current)) {
                offers.set(0, SyntheticOfferFactory.placeholder(ItemValuation.selectBuyItem(villager, offers)));
                OfferResync.send(villager);
            }
            return;
        }

        // A leftover preview from a hand that just emptied must be cleared now;
        // otherwise the icon only ever changes on the tick the cycle advances,
        // which is also the only tick worth scanning the inventory on.
        boolean stale = !SyntheticOfferFactory.isPlaceholder(current);
        long interval = Math.max(1, config.placeholderIconIntervalTicks());
        long time = villager.level().getGameTime();
        if (!stale && time % interval != 0) return;

        long step = time / interval;
        List<Item> owned = InventoryIconPool.tradeable(player, villager, offers, traded);
        Item icon = owned.isEmpty()
            ? IconCyclePool.at(step, villager.getId(), traded)
            : InventoryIconPool.at(owned, step, villager.getId());
        if (!stale && current.getItemCostA().item().value() == icon) return;

        offers.set(0, SyntheticOfferFactory.placeholder(ItemValuation.selectBuyItem(villager, offers), icon));
        OfferResync.send(villager);
    }

    /** Every item this villager already trades for or with — never previewed, never shown as an icon. */
    private static Set<Item> tradedItems(MerchantOffers offers) {
        Set<Item> items = new HashSet<>();
        for (MerchantOffer offer : offers) {
            if (SyntheticOfferFactory.isSynthetic(offer)) {
                // The synthetic row's own payout still counts — it is what the
                // villager pays with, so it must not double as the icon.
                add(items, offer.getResult());
                continue;
            }
            add(items, offer.getCostA());
            add(items, offer.getCostB());
            add(items, offer.getResult());
        }
        return items;
    }

    private static void add(Set<Item> items, ItemStack stack) {
        if (!stack.isEmpty()) items.add(stack.getItem());
    }
}
