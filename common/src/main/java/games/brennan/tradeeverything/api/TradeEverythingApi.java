package games.brennan.tradeeverything.api;

import games.brennan.tradeeverything.config.TradeEverythingConfig;
import games.brennan.tradeeverything.trade.ItemValuation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Public API for other mods (e.g. Dungeon Train) to inspect and tune
 * Trade Everything's valuation at runtime — no Trade Everything release
 * needed for a consumer to adjust values.
 */
public final class TradeEverythingApi {

    private TradeEverythingApi() {}

    /** Resolved value of the stack in sixteenths of an emerald. */
    public static int getValueSixteenths(ItemStack stack) {
        return ItemValuation.valueSixteenths(stack);
    }

    /** Runtime per-item override — wins over config overrides and rarity defaults. */
    public static void setItemOverride(ResourceLocation itemId, int sixteenths) {
        ItemValuation.setRuntimeOverride(itemId, sixteenths);
    }

    /** Registers a valuation hook consulted ahead of all overrides. */
    public static void registerValueProvider(ItemValueProvider provider) {
        ItemValuation.registerProvider(provider);
    }

    /** Registers a payout-item chooser consulted ahead of the built-in default. */
    public static void registerBuyItemSelector(BuyItemSelector selector) {
        ItemValuation.registerBuyItemSelector(selector);
    }

    /** Read-only view of the current config snapshot. */
    public static TradeEverythingConfig config() {
        return TradeEverythingConfig.get();
    }

    /** Re-reads the config file from disk. */
    public static void reload() {
        TradeEverythingConfig.reload();
    }
}
