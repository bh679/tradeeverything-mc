package games.brennan.tradeeverything.trade;

import games.brennan.tradeeverything.api.BuyItemSelector;
import games.brennan.tradeeverything.api.ItemValueProvider;
import games.brennan.tradeeverything.config.TradeEverythingConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Value engine. Resolution order for a stack's value: registered
 * {@link ItemValueProvider}s → runtime API overrides → config
 * {@code item_overrides_sixteenths} → config rarity map via
 * {@link ItemStack#getRarity()}.
 *
 * <p>Internally values are carried in <b>units of 1/{@value #PRECISION} of a
 * sixteenth</b> — 1/256 emerald — while every configured and API-supplied
 * number stays in sixteenths and is scaled on the way in. Whole sixteenths
 * were too coarse to price a craft chain: a stick derives to a quarter of a
 * plank, which derives to a quarter of a log, and both rounded up to the same
 * 1 sixteenth, so a log crafted into 8 sticks was worth 8× the log it came
 * from. Every valuation is compared against others in the same unit, so only
 * the config and API boundaries convert.</p>
 */
public final class ItemValuation {

    /** Internal value units per configured sixteenth — the fixed-point precision. */
    public static final int PRECISION = 16;

    private static final List<ItemValueProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<BuyItemSelector> BUY_ITEM_SELECTORS = new CopyOnWriteArrayList<>();
    private static final Map<ResourceLocation, Integer> RUNTIME_OVERRIDES = new ConcurrentHashMap<>();
    private static final java.util.Set<Object> FAILED_HOOKS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Value retained by a fully worn-out damageable item, as a fraction of its pristine value. */
    private static final double MIN_DURABILITY_FACTOR = 0.1;

    private ItemValuation() {}

    /** The stack's value in internal units (see {@link #PRECISION}). */
    public static int valueUnits(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        for (ItemValueProvider provider : PROVIDERS) {
            // A throwing external provider must degrade to default valuation,
            // never propagate into the trading packet handlers (a propagated
            // throw wedges villagers in a phantom trading session).
            try {
                OptionalInt value = provider.value(stack);
                // The provider contract is sixteenths — scale it like config.
                if (value.isPresent()) return Math.max(1, value.getAsInt() * PRECISION);
            } catch (Throwable t) {
                warnOnce(provider, "value provider", t);
            }
        }
        return Math.max(1, adjustForStack(baseValue(stack), stack));
    }

    /** The stack's value in sixteenths of an emerald — the unit the API speaks. */
    public static int valueSixteenths(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return Math.max(1, Math.round(valueUnits(stack) / (float) PRECISION));
    }

    /**
     * Overrides win outright; otherwise a craftable item is worth its cheapest
     * recipe and everything else its rarity value. The derived value is
     * deliberately NOT floored at rarity — flooring it is what let an item cost
     * as much as the material it is crafted from, so crafting up multiplied
     * value (see {@link RecipeValues}).
     */
    private static int baseValue(ItemStack stack) {
        OptionalInt override = overrideValue(stack);
        if (override.isPresent()) return override.getAsInt();
        if (TradeEverythingConfig.get().deriveValuesFromRecipes()) {
            OptionalInt derived = RecipeValues.derivedValue(stack.getItem());
            if (derived.isPresent()) return derived.getAsInt();
        }
        return rarityValue(stack);
    }

    /**
     * Stack-specific adjustments: enchantment levels add value (config
     * per-level, stored book enchants included); durability scales it linearly
     * between {@link #MIN_DURABILITY_FACTOR} at fully worn and 100% at pristine
     * ({@code factor = MIN + (1 - MIN) × remainingFraction}). The curve is
     * strictly increasing on purpose — an earlier {@code min(1, 0.1 + remaining)}
     * plateaued at full value for anything above 90% durability, so a lightly
     * used tool priced identically to an unused one.
     */
    private static int adjustForStack(int base, ItemStack stack) {
        int value = base + enchantmentBonus(stack);
        if (stack.isDamageableItem() && stack.isDamaged()) {
            // Clamped: out-of-range NBT damage must not drive the factor
            // negative (a negative value would flip the whole trade).
            double remaining = Math.clamp(
                1.0 - (double) stack.getDamageValue() / stack.getMaxDamage(), 0.0, 1.0);
            double factor = MIN_DURABILITY_FACTOR + (1.0 - MIN_DURABILITY_FACTOR) * remaining;
            value = (int) Math.round(value * factor);
        }
        return value;
    }

    private static int enchantmentBonus(ItemStack stack) {
        int perLevel = TradeEverythingConfig.get().enchantmentValuePerLevelSixteenths();
        if (perLevel <= 0) return 0;
        int levels = totalLevels(stack.get(DataComponents.ENCHANTMENTS))
            + totalLevels(stack.get(DataComponents.STORED_ENCHANTMENTS));
        return levels * perLevel * PRECISION;
    }

    /**
     * Effective level units: doubles per level (1, 2, 4, 8, 16…) so a
     * Sharpness V is worth 16× a Sharpness I, mirroring how enchant cost
     * and power scale. Capped to avoid overflow on absurd NBT levels.
     */
    private static int totalLevels(ItemEnchantments enchantments) {
        if (enchantments == null) return 0;
        int sum = 0;
        for (var entry : enchantments.entrySet()) {
            int level = Math.min(entry.getIntValue(), 12);
            if (level > 0) sum += 1 << (level - 1);
        }
        return sum;
    }

    /**
     * Runtime API override, else config override, in internal units — both maps
     * are configured in sixteenths. Package-visible for RecipeValues.
     */
    static OptionalInt overrideValue(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Integer runtime = RUNTIME_OVERRIDES.get(id);
        if (runtime != null) return OptionalInt.of(runtime * PRECISION);
        Integer override = TradeEverythingConfig.get().itemOverridesSixteenths().get(id.toString());
        return override != null ? OptionalInt.of(override * PRECISION) : OptionalInt.empty();
    }

    /** Config rarity-tier value for the stack, in internal units. Package-visible for RecipeValues. */
    static int rarityValue(ItemStack stack) {
        String rarity = stack.getRarity().name().toLowerCase(Locale.ROOT);
        return Math.max(1, TradeEverythingConfig.get().rarityValuesSixteenths().getOrDefault(rarity, 1)) * PRECISION;
    }

    /** Payout item for the villager: API selectors first, then the built-in default. */
    public static Item selectBuyItem(AbstractVillager villager, MerchantOffers offers) {
        for (BuyItemSelector selector : BUY_ITEM_SELECTORS) {
            try {
                Optional<Item> chosen = selector.selectBuyItem(villager, offers);
                if (chosen.isPresent()) return chosen.get();
            } catch (Throwable t) {
                warnOnce(selector, "buy-item selector", t);
            }
        }
        return DefaultBuyItemSelector.select(offers);
    }

    /** One warning per registered hook object — a broken hook must not spam every click. */
    private static void warnOnce(Object hook, String kind, Throwable t) {
        if (FAILED_HOOKS.add(hook)) {
            games.brennan.tradeeverything.TradeEverything.LOGGER.warn(
                "[TradeEverything] registered {} {} threw — ignoring it from now on", kind, hook.getClass().getName(), t);
        }
    }

    public static void registerProvider(ItemValueProvider provider) {
        PROVIDERS.add(provider);
    }

    public static void registerBuyItemSelector(BuyItemSelector selector) {
        BUY_ITEM_SELECTORS.add(selector);
    }

    public static void setRuntimeOverride(ResourceLocation itemId, int sixteenths) {
        if (sixteenths > 0) {
            RUNTIME_OVERRIDES.put(itemId, sixteenths);
        } else {
            RUNTIME_OVERRIDES.remove(itemId);
        }
    }
}
