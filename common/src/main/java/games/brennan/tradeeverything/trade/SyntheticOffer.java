package games.brennan.tradeeverything.trade;

/**
 * Duck interface attached to {@code MerchantOffer} by
 * {@code MerchantOfferMixin} to flag the injected "Trade Anything" offer.
 * The flag deliberately never serialises — synthetic offers must never
 * reach villager NBT.
 */
public interface SyntheticOffer {

    boolean tradeeverything$isSynthetic();

    void tradeeverything$setSynthetic(boolean synthetic);
}
