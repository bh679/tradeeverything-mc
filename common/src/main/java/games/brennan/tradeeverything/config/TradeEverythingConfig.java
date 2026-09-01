package games.brennan.tradeeverything.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import games.brennan.tradeeverything.ConfigDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable config snapshot, loaded from {@code <config>/tradeeverything.json}.
 *
 * <p>Item values are integer fixed-point in <b>sixteenths of an emerald</b>:
 * a COMMON item defaults to 1 (16 items = 1 emerald), an emerald itself is 16.
 * Unknown keys are dropped, invalid values clamped, missing keys filled from
 * defaults, and the normalised file is written back on load.</p>
 *
 * <p>{@code payout_multipliers} is the one fractional map: a per-payout-item
 * factor on top of {@link #resultMultiplier()}, so a premium currency can hand
 * over a fraction of face value.</p>
 */
public record TradeEverythingConfig(
    Map<String, Integer> rarityValuesSixteenths,
    Map<String, Integer> itemOverridesSixteenths,
    double resultMultiplier,
    int maxCostCount,
    int maxResultCount,
    boolean allowUndervaluedTrades,
    boolean enableWanderingTrader,
    boolean deriveValuesFromRecipes,
    int enchantmentValuePerLevelSixteenths,
    boolean cyclePlaceholderIcon,
    int placeholderIconIntervalTicks,
    boolean previewHeldItem,
    boolean preferSingleItemTrades,
    Map<String, Double> payoutMultipliers
) {

    private static final Logger LOGGER = LoggerFactory.getLogger("TradeEverything");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "tradeeverything.json";

    /** Bounds shared by {@code result_multiplier} and every {@code payout_multipliers} entry. */
    private static final double MIN_MULTIPLIER = 0.01;
    private static final double MAX_MULTIPLIER = 100.0;

    private static volatile TradeEverythingConfig instance = defaults();

    public static TradeEverythingConfig get() {
        return instance;
    }

    /** Loads (or creates) the config file and swaps in a fresh immutable snapshot. */
    public static TradeEverythingConfig reload() {
        Path path = ConfigDir.get().resolve(FILE_NAME);
        TradeEverythingConfig loaded = load(path);
        instance = loaded;
        return loaded;
    }

    public static TradeEverythingConfig defaults() {
        Map<String, Integer> rarity = new LinkedHashMap<>();
        rarity.put("common", 1);
        rarity.put("uncommon", 8);
        rarity.put("rare", 32);
        rarity.put("epic", 128);

        // Vanilla Rarity is a weak value signal (nearly everything is COMMON), so
        // ship sensible overrides for the notable economy items. All editable.
        Map<String, Integer> overrides = new LinkedHashMap<>();
        overrides.put("minecraft:emerald", 16);
        overrides.put("minecraft:emerald_block", 144);
        overrides.put("minecraft:diamond", 64);
        overrides.put("minecraft:diamond_block", 576);
        overrides.put("minecraft:iron_ingot", 8);
        overrides.put("minecraft:iron_block", 72);
        overrides.put("minecraft:gold_ingot", 12);
        overrides.put("minecraft:gold_block", 108);
        overrides.put("minecraft:copper_ingot", 2);
        overrides.put("minecraft:netherite_ingot", 1024);
        overrides.put("minecraft:netherite_upgrade_smithing_template", 512);
        overrides.put("minecraft:netherite_scrap", 224);
        overrides.put("minecraft:ancient_debris", 256);
        overrides.put("minecraft:lapis_lazuli", 2);
        overrides.put("minecraft:redstone", 2);
        overrides.put("minecraft:quartz", 4);
        overrides.put("minecraft:amethyst_shard", 4);
        overrides.put("minecraft:obsidian", 4);
        overrides.put("minecraft:ender_pearl", 16);
        overrides.put("minecraft:blaze_rod", 16);
        overrides.put("minecraft:slime_ball", 4);
        // A book derives to ~7 (3 paper at 1 + leather, itself floored at 4 by the
        // rabbit-hide recipe) — far too rich for a librarian staple. Pin it at a
        // third of that: 2 → 8 books per emerald.
        overrides.put("minecraft:book", 2);
        overrides.put("minecraft:bookshelf", 6);
        // Uncraftable gear — no recipe to derive from, and vanilla rarity says COMMON.
        overrides.put("minecraft:trident", 128);
        overrides.put("minecraft:saddle", 64);
        overrides.put("minecraft:iron_horse_armor", 64);
        overrides.put("minecraft:golden_horse_armor", 48);
        overrides.put("minecraft:diamond_horse_armor", 256);
        // 3 emerald blocks (144 each) — a totem shouldn't be a casual buy.
        overrides.put("minecraft:totem_of_undying", 432);
        overrides.put("minecraft:heavy_core", 256);
        overrides.put("minecraft:breeze_rod", 16);
        overrides.put("minecraft:shulker_shell", 64);
        overrides.put("minecraft:nautilus_shell", 32);
        overrides.put("minecraft:heart_of_the_sea", 256);
        // Anvil damage states are separate items with no recipe — price the decay
        // explicitly (≈11 → 4 → 1 emeralds after the payout margin).
        overrides.put("minecraft:chipped_anvil", 88);
        overrides.put("minecraft:damaged_anvil", 22);
        overrides.put("minecraft:turtle_scute", 16);
        overrides.put("minecraft:armadillo_scute", 8);
        overrides.put("minecraft:phantom_membrane", 16);

        // Premium payout currencies hand over a FRACTION of face value: paid in
        // diamonds, a trade is worth half what the same trade pays in ordinary
        // goods. Any item id can be added; 1.0 disables the penalty for that one.
        Map<String, Double> payoutMultipliers = new LinkedHashMap<>();
        payoutMultipliers.put("minecraft:diamond", 0.5);

        // result_multiplier 0.75 = the villager's merchant margin: payouts are 75%
        // of value, so discount-driven buy/sell round-trips can't print emeralds.
        // prefer_single_item_trades: quote one item at a time whenever one item can
        // afford a payout unit, rather than batching to shave the rounding loss.
        return new TradeEverythingConfig(
            Map.copyOf(rarity), Map.copyOf(overrides),
            0.75, 64, 64, true, true, true, 16, true, 40, true, true,
            Map.copyOf(payoutMultipliers)
        );
    }

    static TradeEverythingConfig load(Path path) {
        TradeEverythingConfig defaults = defaults();
        TradeEverythingConfig result = defaults;
        if (Files.exists(path)) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                result = fromJson(root, defaults);
            } catch (IOException | RuntimeException e) {
                LOGGER.error("[TradeEverything] failed to read {} — using defaults ({})", path, e.toString());
                return defaults; // don't overwrite a malformed file the user may want to fix
            }
        }
        writeNormalised(path, result);
        return result;
    }

    private static TradeEverythingConfig fromJson(JsonObject root, TradeEverythingConfig defaults) {
        Map<String, Integer> rarity = intMap(root, "rarity_values_sixteenths", defaults.rarityValuesSixteenths());
        Map<String, Integer> overrides = intMap(root, "item_overrides_sixteenths", defaults.itemOverridesSixteenths());
        double multiplier = clamp(number(root, "result_multiplier", defaults.resultMultiplier()),
            MIN_MULTIPLIER, MAX_MULTIPLIER);
        int maxCost = (int) clamp(number(root, "max_cost_count", defaults.maxCostCount()), 1, 64);
        int maxResult = (int) clamp(number(root, "max_result_count", defaults.maxResultCount()), 1, 64);
        boolean undervalued = bool(root, "allow_undervalued_trades", defaults.allowUndervaluedTrades());
        boolean wandering = bool(root, "enable_wandering_trader", defaults.enableWanderingTrader());
        boolean recipes = bool(root, "derive_values_from_recipes", defaults.deriveValuesFromRecipes());
        int enchantPerLevel = (int) clamp(number(root, "enchantment_value_per_level_sixteenths",
            defaults.enchantmentValuePerLevelSixteenths()), 0, 100_000);
        boolean cycleIcon = bool(root, "cycle_placeholder_icon", defaults.cyclePlaceholderIcon());
        int cycleTicks = (int) clamp(number(root, "placeholder_icon_interval_ticks",
            defaults.placeholderIconIntervalTicks()), 1, 200);
        boolean previewHeld = bool(root, "preview_held_item", defaults.previewHeldItem());
        boolean singleItem = bool(root, "prefer_single_item_trades", defaults.preferSingleItemTrades());
        Map<String, Double> payoutMultipliers =
            doubleMap(root, "payout_multipliers", defaults.payoutMultipliers());
        return new TradeEverythingConfig(rarity, overrides, multiplier, maxCost, maxResult,
            undervalued, wandering, recipes, enchantPerLevel, cycleIcon, cycleTicks, previewHeld,
            singleItem, payoutMultipliers);
    }

    /**
     * Merges the user's file entries over the shipped {@code fallback} defaults:
     * new defaults (e.g. items added in a later version) appear automatically for
     * existing configs, while any value the user changed overrides its default.
     * The normalised file is rewritten on load, so the additions persist.
     */
    private static Map<String, Integer> intMap(JsonObject root, String key, Map<String, Integer> fallback) {
        JsonElement el = root.get(key);
        if (el == null || !el.isJsonObject()) return fallback;
        Map<String, Integer> out = new LinkedHashMap<>(fallback);
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            try {
                int v = e.getValue().getAsInt();
                if (v > 0) {
                    out.put(e.getKey(), v);
                } else {
                    LOGGER.warn("[TradeEverything] {}.{} must be > 0 — dropped", key, e.getKey());
                }
            } catch (RuntimeException ex) {
                LOGGER.warn("[TradeEverything] {}.{} is not an integer — dropped", key, e.getKey());
            }
        }
        return Map.copyOf(out);
    }

    /**
     * As {@link #intMap}, for the fractional {@code payout_multipliers} map: user
     * entries merge over the shipped defaults, non-numeric or non-positive entries
     * are dropped with a warning, and the rest are clamped to the same bounds
     * {@code result_multiplier} uses.
     */
    private static Map<String, Double> doubleMap(JsonObject root, String key, Map<String, Double> fallback) {
        JsonElement el = root.get(key);
        if (el == null || !el.isJsonObject()) return fallback;
        Map<String, Double> out = new LinkedHashMap<>(fallback);
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            try {
                double v = e.getValue().getAsDouble();
                if (v > 0.0) {
                    out.put(e.getKey(), clamp(v, MIN_MULTIPLIER, MAX_MULTIPLIER));
                } else {
                    LOGGER.warn("[TradeEverything] {}.{} must be > 0 — dropped", key, e.getKey());
                }
            } catch (RuntimeException ex) {
                LOGGER.warn("[TradeEverything] {}.{} is not a number — dropped", key, e.getKey());
            }
        }
        return Map.copyOf(out);
    }

    private static double number(JsonObject root, String key, double fallback) {
        JsonElement el = root.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber() ? el.getAsDouble() : fallback;
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        JsonElement el = root.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean() ? el.getAsBoolean() : fallback;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static void writeNormalised(Path path, TradeEverythingConfig config) {
        JsonObject root = new JsonObject();
        root.add("rarity_values_sixteenths", toJsonMap(config.rarityValuesSixteenths()));
        root.add("item_overrides_sixteenths", toJsonMap(config.itemOverridesSixteenths()));
        root.addProperty("result_multiplier", config.resultMultiplier());
        root.addProperty("max_cost_count", config.maxCostCount());
        root.addProperty("max_result_count", config.maxResultCount());
        root.addProperty("allow_undervalued_trades", config.allowUndervaluedTrades());
        root.addProperty("enable_wandering_trader", config.enableWanderingTrader());
        root.addProperty("derive_values_from_recipes", config.deriveValuesFromRecipes());
        root.addProperty("enchantment_value_per_level_sixteenths", config.enchantmentValuePerLevelSixteenths());
        root.addProperty("cycle_placeholder_icon", config.cyclePlaceholderIcon());
        root.addProperty("placeholder_icon_interval_ticks", config.placeholderIconIntervalTicks());
        root.addProperty("preview_held_item", config.previewHeldItem());
        root.addProperty("prefer_single_item_trades", config.preferSingleItemTrades());
        root.add("payout_multipliers", toJsonDoubleMap(config.payoutMultipliers()));
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[TradeEverything] failed to write {} ({})", path, e.toString());
        }
    }

    private static JsonObject toJsonMap(Map<String, Integer> map) {
        JsonObject obj = new JsonObject();
        map.forEach(obj::addProperty);
        return obj;
    }

    /** Erasure-distinct sibling of {@link #toJsonMap} — the two can't be overloads. */
    private static JsonObject toJsonDoubleMap(Map<String, Double> map) {
        JsonObject obj = new JsonObject();
        map.forEach(obj::addProperty);
        return obj;
    }
}
