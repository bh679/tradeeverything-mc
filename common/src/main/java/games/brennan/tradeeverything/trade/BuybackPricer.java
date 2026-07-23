package games.brennan.tradeeverything.trade;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

/**
 * Buy-back of the villager's own stock: when the inserted item is
 * component-identical to a real offer's result, the villager repurchases it
 * for the sell row's <b>live</b> price minus 10% (floored). Keying off the
 * discounted {@code getCostA()} — not the base cost — means the buy-back is
 * always at or below what the player just paid, so gossip/hero/cured
 * discounts can never turn sell→buy-back into a money loop.
 */
public final class BuybackPricer {

    private static final double MARGIN = 0.90;

    private BuybackPricer() {}

    public static Optional<MerchantOffer> buybackOffer(ItemStack input, MerchantOffers offers) {
        if (input.isEmpty()) return Optional.empty();
        for (MerchantOffer offer : offers) {
            if (SyntheticOfferFactory.isSynthetic(offer)) continue;
            if (!ItemStack.isSameItemSameComponents(input, offer.getResult())) continue;
            ItemStack liveCost = offer.getCostA(); // discount-adjusted price
            int payout = (int) Math.floor(liveCost.getCount() * MARGIN);
            if (payout < 1) return Optional.empty(); // too cheap to buy back
            int costCount = Math.max(1, offer.getResult().getCount());
            return Optional.of(SyntheticOfferFactory.priced(
                input, costCount, liveCost.getItem(), payout));
        }
        return Optional.empty();
    }
}
