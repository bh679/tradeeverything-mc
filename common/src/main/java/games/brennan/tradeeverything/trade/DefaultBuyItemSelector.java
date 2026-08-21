package games.brennan.tradeeverything.trade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

/**
 * Default payout choice: the first non-currency cost item across the
 * villager's real offers — i.e. something the villager is actually buying
 * (wheat, coal, paper…). Wandering traders only buy emeralds, so they fall
 * back to paying out emeralds.
 *
 * <p>Currency here means emeralds <em>and</em> emerald blocks, the same pair
 * {@link TradeExemptions} refuses. A cost row priced in emerald blocks (Dungeon
 * Train reprices expensive enchanted gear that way) is a price tag, not goods
 * the villager buys — picking it as the payout item made the slot hand out
 * 9-emerald blocks at goods-sized rates.</p>
 */
public final class DefaultBuyItemSelector {

    private DefaultBuyItemSelector() {}

    public static Item select(MerchantOffers offers) {
        for (MerchantOffer offer : offers) {
            if (SyntheticOfferFactory.isSynthetic(offer)) continue;
            Item costA = offer.getItemCostA().item().value();
            if (!isCurrency(costA)) return costA;
            Optional<ItemCost> costB = offer.getItemCostB();
            if (costB.isPresent()) {
                Item item = costB.get().item().value();
                if (!isCurrency(item)) return item;
            }
        }
        return Items.EMERALD;
    }

    /** Emeralds and emerald blocks are prices, never payout goods. */
    private static boolean isCurrency(Item item) {
        return item == Items.EMERALD || item == Items.EMERALD_BLOCK;
    }
}
