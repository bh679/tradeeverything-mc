package games.brennan.tradeeverything.trade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Items the "Trade Anything" slot refuses: emeralds, anything the villager
 * already buys through a real offer (those belong to the vanilla trades,
 * which auto-match correctly once the synthetic offer stays on its
 * unmatchable placeholder), and anything the villager <b>sells</b> — buying
 * back its own goods would let gossip/hero discounts turn its sell-side
 * prices into an emerald printer (buy discounted axe → sell axe for coal →
 * coal → emerald → repeat).
 */
public final class TradeExemptions {

    private TradeExemptions() {}

    public static boolean isExempt(ItemStack input, MerchantOffers offers) {
        Item item = input.getItem();
        if (item == Items.EMERALD || item == Items.EMERALD_BLOCK) return true;
        for (MerchantOffer offer : offers) {
            if (SyntheticOfferFactory.isSynthetic(offer)) continue;
            // Buy-side: item-level — commodities the villager buys belong to
            // its real trade rows regardless of components.
            if (offer.getItemCostA().item().value() == item) return true;
            if (offer.getItemCostB().isPresent() && offer.getItemCostB().get().item().value() == item) return true;
        }
        // The villager's own stock is NOT exempt — it's bought back at a 10%
        // margin under its live sell price (see BuybackPricer), which keeps
        // discount round-trips unprofitable while letting players return goods.
        return false;
    }
}
