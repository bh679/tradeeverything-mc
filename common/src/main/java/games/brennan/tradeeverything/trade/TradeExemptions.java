package games.brennan.tradeeverything.trade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Items the "Trade Anything" slot refuses: emeralds, and anything the
 * villager already buys through a real offer — those items belong to the
 * vanilla trades (which also auto-match correctly once the synthetic offer
 * stays on its unmatchable placeholder).
 */
public final class TradeExemptions {

    private TradeExemptions() {}

    public static boolean isExempt(Item item, MerchantOffers offers) {
        if (item == Items.EMERALD) return true;
        for (MerchantOffer offer : offers) {
            if (SyntheticOfferFactory.isSynthetic(offer)) continue;
            if (offer.getItemCostA().item().value() == item) return true;
            if (offer.getItemCostB().isPresent() && offer.getItemCostB().get().item().value() == item) return true;
        }
        return false;
    }
}
