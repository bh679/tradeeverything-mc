package games.brennan.tradeeverything.api;

import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

/**
 * Highest-priority extension point for item valuation. Registered providers are
 * consulted in registration order; the first non-empty result wins, ahead of
 * config overrides and rarity defaults.
 */
@FunctionalInterface
public interface ItemValueProvider {

    /** Value of the stack in sixteenths of an emerald, or empty to defer. */
    OptionalInt value(ItemStack stack);
}
