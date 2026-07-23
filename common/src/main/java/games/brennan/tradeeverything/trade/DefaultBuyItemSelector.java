package games.brennan.tradeeverything.trade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

/**
 * Default payout choice: the first non-emerald cost item across the
 * villager's real offers — i.e. something the villager is actually buying
 * (wheat, coal, paper…). Wandering traders only buy emeralds, so they fall
 * back to paying out emeralds.
 */
public final class DefaultBuyItemSelector {

    private DefaultBuyItemSelector() {}

    public static Item select(MerchantOffers offers) {
        for (MerchantOffer offer : offers) {
            if (SyntheticOfferFactory.isSynthetic(offer)) continue;
            Item costA = offer.getItemCostA().item().value();
            if (costA != Items.EMERALD) return costA;
            Optional<ItemCost> costB = offer.getItemCostB();
            if (costB.isPresent()) {
                Item item = costB.get().item().value();
                if (item != Items.EMERALD) return item;
            }
        }
        return Items.EMERALD;
    }
}
