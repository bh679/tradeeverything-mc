package games.brennan.tradeeverything.trade;

import games.brennan.tradeeverything.config.TradeEverythingConfig;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

/**
 * The single pricing path for the synthetic row: stack in → offer out.
 *
 * <p>Shared by the two places that need it — repricing when an item is put in
 * the slot ({@code MerchantContainerMixin}) and the held-item preview
 * ({@link PlaceholderIconCycle}) — so both quote a given item identically.</p>
 */
public final class OfferQuoter {

    private OfferQuoter() {}

    /**
     * The offer this input deserves, or {@link Optional#empty()} if it can't be
     * traded (empty, exempt, or worth too little to price).
     */
    public static Optional<MerchantOffer> quote(AbstractVillager villager, ItemStack input, MerchantOffers offers) {
        if (input.isEmpty() || TradeExemptions.isExempt(input, offers)) return Optional.empty();
        TradeEverythingConfig config = TradeEverythingConfig.get();
        Item preferred = ItemValuation.selectBuyItem(villager, offers);
        Item payout = TradePricer.payoutFor(input, preferred, offers, config);
        int payoutValue = TradePricer.payoutValueSixteenths(payout, offers);
        // The villager's own stock buys back at 10% under its live price.
        Optional<MerchantOffer> buyback = BuybackPricer.buybackOffer(input, offers);
        if (buyback.isPresent()) return buyback;
        return TradePricer.quote(input, payout, payoutValue, config)
            .map(quote -> SyntheticOfferFactory.priced(input, quote.costCount(), payout, quote.resultCount()));
    }

    /**
     * As {@link #quote}, falling back to the placeholder row — what the slot
     * shows when the input is absent or untradeable.
     */
    public static MerchantOffer quoteOrPlaceholder(AbstractVillager villager, ItemStack input, MerchantOffers offers) {
        return quote(villager, input, offers).orElseGet(() -> {
            Item preferred = ItemValuation.selectBuyItem(villager, offers);
            Item payout = input.isEmpty() ? preferred
                : TradePricer.payoutFor(input, preferred, offers, TradeEverythingConfig.get());
            return SyntheticOfferFactory.placeholder(payout);
        });
    }
}
