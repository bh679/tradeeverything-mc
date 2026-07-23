package games.brennan.tradeeverything;

import games.brennan.tradeeverything.config.TradeEverythingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common init. Loads the config; everything else is mixin-driven
 * (see {@code games.brennan.tradeeverything.mixin}).
 */
public final class TradeEverything {

    public static final String MOD_ID = "tradeeverything";
    public static final Logger LOGGER = LoggerFactory.getLogger("TradeEverything");

    private TradeEverything() {}

    public static void init() {
        TradeEverythingConfig config = TradeEverythingConfig.reload();
        LOGGER.info("[TradeEverything] initialised — {} rarity tiers, {} item overrides",
            config.rarityValuesSixteenths().size(), config.itemOverridesSixteenths().size());
    }
}
