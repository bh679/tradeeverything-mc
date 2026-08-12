package games.brennan.tradeeverything.trade;

import games.brennan.tradeeverything.TradeEverything;
import games.brennan.tradeeverything.config.TradeEverythingConfig;
import games.brennan.tradeeverything.mixin.MerchantMenuAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.HashSet;
import java.util.Set;

/**
 * Cycles the icon on the "Trade Anything" row while the slot is empty, so the
 * row visibly advertises "any item goes here" instead of showing one fixed
 * item.
 *
 * <p>Driven from {@code ServerPlayer.doTick} — the cost icon is
 * server-authoritative, so a change means rewriting offer 0 and re-sending the
 * offer list ({@link OfferResync}, the same path repricing already uses).
 * Stateless: the icon is a pure function of game time, the villager id and the
 * items that villager already trades, so there is nothing to track or clean
 * up, and a re-entry mid-session picks up exactly where it left off.</p>
 */
public final class PlaceholderIconCycle {

    private PlaceholderIconCycle() {}

    /** Runs every server tick for every player; cheap until a merchant screen is open. */
    public static void tick(ServerPlayer player) {
        try {
            tickInner(player);
        } catch (Throwable t) {
            // Cosmetic feature — never propagate into the player tick loop.
            TradeEverything.LOGGER.warn("[TradeEverything] placeholder icon cycle failed", t);
        }
    }

    private static void tickInner(ServerPlayer player) {
        TradeEverythingConfig config = TradeEverythingConfig.get();
        if (!config.cyclePlaceholderIcon()) return;
        if (!(player.containerMenu instanceof MerchantMenu menu)) return;
        if (!(((MerchantMenuAccessor) menu).tradeeverything$getTrader() instanceof AbstractVillager villager)) return;

        MerchantOffers offers = villager.getOffers();
        if (offers.isEmpty()) return;
        MerchantOffer current = offers.get(0);
        // Only the "nothing inserted yet" row cycles — once the player drops an
        // item in, offer 0 is a real priced quote and must be left alone.
        if (!SyntheticOfferFactory.isPlaceholder(current)) return;

        long step = villager.level().getGameTime() / Math.max(1, config.placeholderIconIntervalTicks());
        Item icon = IconCyclePool.at(step, villager.getId(), tradedItems(offers));
        if (current.getItemCostA().item().value() == icon) return;

        offers.set(0, SyntheticOfferFactory.placeholder(current.getResult().getItem(), icon));
        OfferResync.send(villager);
    }

    /** Every item this villager already trades for or with — never shown as an icon. */
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
