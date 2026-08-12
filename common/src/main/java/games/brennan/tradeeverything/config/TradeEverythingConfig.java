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
    boolean previewHeldItem
) {

    private static final Logger LOGGER = LoggerFactory.getLogger("TradeEverything");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "tradeeverything.json";

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
        overrides.put("minecraft:ender_pearl", 16);
        overrides.put("minecraft:blaze_rod", 16);
        overrides.put("minecraft:slime_ball", 4);
        overrides.put("minecraft:bookshelf", 3);
        // Uncraftable gear — no recipe to derive from, and vanilla rarity says COMMON.
        overrides.put("minecraft:trident", 128);
        overrides.put("minecraft:saddle", 64);
        overrides.put("minecraft:iron_horse_armor", 64);
        overrides.put("minecraft:golden_horse_armor", 48);
        overrides.put("minecraft:diamond_horse_armor", 256);
        overrides.put("minecraft:totem_of_undying", 128);
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

        // result_multiplier 0.75 = the villager's merchant margin: payouts are 75%
        // of value, so discount-driven buy/sell round-trips can't print emeralds.
        return new TradeEverythingConfig(
            Map.copyOf(rarity), Map.copyOf(overrides),
            0.75, 64, 64, true, true, true, 16, true, 40, true
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
        double multiplier = clamp(number(root, "result_multiplier", defaults.resultMultiplier()), 0.01, 100.0);
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
        return new TradeEverythingConfig(rarity, overrides, multiplier, maxCost, maxResult,
            undervalued, wandering, recipes, enchantPerLevel, cycleIcon, cycleTicks, previewHeld);
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
}
