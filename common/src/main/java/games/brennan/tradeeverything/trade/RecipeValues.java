package games.brennan.tradeeverything.trade;

import games.brennan.tradeeverything.config.TradeEverythingConfig;
import games.brennan.tradeeverything.mixin.SmithingTransformRecipeAccessor;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Recipe-derived item values: a craftable item is worth its cheapest recipe —
 * the sum of its ingredients' values divided by the output count. Cheapest-
 * recipe pricing is arbitrage-free: crafting then selling never beats selling
 * the materials.
 *
 * <p>ALL values are solved up front by fixed-point iteration whenever the
 * recipe index rebuilds (world load, {@code /reload}, config reload): each
 * pass prices every recipe whose ingredients are already resolved
 * (override → previously derived → rarity for uncraftable items), keeping
 * the minimum, until stable. Runtime lookups are a map get. A recursive
 * per-lookup derivation is deliberately avoided — without full memoisation
 * it explodes combinatorially on ingredient families like dyes/planks/wool
 * (froze the server thread for minutes on first valuation).</p>
 */
public final class RecipeValues {

    /** ingredients (as option arrays) + output count for one recipe. */
    private record IndexedRecipe(List<ItemStack[]> ingredientOptions, int resultCount) {}

    private static final Logger LOGGER = LoggerFactory.getLogger("TradeEverything");
    private static final int MAX_PASSES = 64;

    private static volatile RecipeManager indexedManager;
    private static volatile TradeEverythingConfig indexedConfig;
    private static volatile Map<Item, Integer> derivedValues = Map.of();
    /** result item → ingredient item → {mandatoryCount, resultCount} of its cheapest-in-that-ingredient recipe. */
    private static volatile Map<Item, Map<Item, int[]>> ingredientCounts = Map.of();

    private RecipeValues() {}

    /** Rebuilds index + value table if recipes or config changed. Cheap when unchanged. */
    public static void ensureIndexed(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        TradeEverythingConfig config = TradeEverythingConfig.get();
        if (manager == indexedManager && config == indexedConfig) return;
        synchronized (RecipeValues.class) {
            if (manager == indexedManager && config == indexedConfig) return;
            long start = System.nanoTime();
            try {
                Map<Item, List<IndexedRecipe>> index = buildIndex(server, manager);
                derivedValues = solve(index);
                ingredientCounts = buildIngredientCounts(index);
                LOGGER.info("[TradeEverything] derived {} item values from {} craftable items in {} ms",
                    derivedValues.size(), index.size(), (System.nanoTime() - start) / 1_000_000);
            } catch (Throwable t) {
                // Memoize the failure too — retrying a broken index on every
                // payment-slot click would hammer the server thread and rethrow
                // inside packet handling. Rarity fallback covers valuation.
                derivedValues = Map.of();
                ingredientCounts = Map.of();
                LOGGER.error("[TradeEverything] recipe value derivation failed — using rarity fallback", t);
            }
            indexedManager = manager;
            indexedConfig = config;
        }
    }

    /** Solved value in sixteenths, or empty if the item has no priceable recipe. */
    public static OptionalInt derivedValue(Item item) {
        Integer value = derivedValues.get(item);
        return value != null ? OptionalInt.of(value) : OptionalInt.empty();
    }

    /**
     * Largest payout of {@code ingredient} for {@code inputCount} of a crafted
     * {@code result} that stays strictly cheaper than crafting them — i.e. the
     * synthetic slot must never hand back as much of an item's own material as
     * the recipe consumes. Without this, a discounted villager buy-rate can make
     * an item sell for more of its material than it takes to make (an iron block
     * paid out 13 iron ingots at a weaponsmith, yet crafts from only 9), a free
     * material printer. Empty when {@code ingredient} is not a mandatory
     * ingredient of any recipe for {@code result} (no cap applies).
     */
    public static OptionalInt maxMaterialPayout(Item result, Item ingredient, int inputCount) {
        Map<Item, int[]> perResult = ingredientCounts.get(result);
        if (perResult == null) return OptionalInt.empty();
        int[] pair = perResult.get(ingredient);
        if (pair == null) return OptionalInt.empty();
        long mandatoryCount = pair[0];
        long resultCount = pair[1];
        // Largest M with M * resultCount < inputCount * mandatoryCount, so the
        // payout is always at least one unit under the crafting cost.
        long cap = ((long) inputCount * mandatoryCount - 1) / resultCount;
        return OptionalInt.of((int) Math.max(0, cap));
    }

    private static Map<Item, List<IndexedRecipe>> buildIndex(MinecraftServer server, RecipeManager manager) {
        Map<Item, List<IndexedRecipe>> index = new HashMap<>();
        int broken = 0;
        for (RecipeHolder<?> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            // One malformed modded recipe must not void the whole table.
            try {
                ItemStack result = holder.value().getResultItem(server.registryAccess());
                if (result.isEmpty()) continue;
                NonNullList<Ingredient> ingredients = holder.value().getIngredients();
                if (ingredients.isEmpty()) continue;
                addRecipe(index, result, ingredients);
            } catch (Throwable t) {
                broken++;
            }
        }
        // Smithing transforms (netherite gear): template + base + addition,
        // all consumed — without these a netherite sword prices as COMMON.
        for (RecipeHolder<?> holder : manager.getAllRecipesFor(RecipeType.SMITHING)) {
            try {
                if (!(holder.value() instanceof SmithingTransformRecipe smithing)) continue;
                ItemStack result = smithing.getResultItem(server.registryAccess());
                if (result.isEmpty()) continue;
                SmithingTransformRecipeAccessor accessor = (SmithingTransformRecipeAccessor) smithing;
                addRecipe(index, result, List.of(
                    accessor.tradeeverything$template(),
                    accessor.tradeeverything$base(),
                    accessor.tradeeverything$addition()
                ));
            } catch (Throwable t) {
                broken++;
            }
        }
        if (broken > 0) {
            LOGGER.warn("[TradeEverything] skipped {} unreadable recipes during value indexing", broken);
        }
        return index;
    }

    private static void addRecipe(Map<Item, List<IndexedRecipe>> index, ItemStack result, List<Ingredient> ingredients) {
        List<ItemStack[]> options = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            ItemStack[] items = ingredient.getItems();
            if (items.length > 0) options.add(items);
        }
        if (options.isEmpty()) return;
        index.computeIfAbsent(result.getItem(), k -> new ArrayList<>())
            .add(new IndexedRecipe(options, result.getCount()));
    }

    /**
     * Fixed-point solve. A recipe prices once every ingredient resolves
     * (override → derived-so-far → rarity for uncraftable items); the item
     * keeps min-over-recipes. Values only ever decrease, so iteration
     * terminates; unresolved cycles simply stay absent and fall back to rarity
     * at lookup time.
     *
     * <p>The result is deliberately NOT floored at the item's rarity value. A
     * floor breaks the arbitrage-free property this table exists for: with
     * planks and sticks both floored up to a COMMON item's value, one log
     * crafted into 8 sticks was worth 8 logs. Sub-sixteenth precision
     * ({@link ItemValuation#PRECISION}) is what leaves room under the floor —
     * a stick prices at a quarter of a plank, a plank at a quarter of a log.</p>
     */
    private static Map<Item, Integer> solve(Map<Item, List<IndexedRecipe>> index) {
        Map<Item, Integer> values = new HashMap<>();
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean changed = false;
            for (Map.Entry<Item, List<IndexedRecipe>> entry : index.entrySet()) {
                Item item = entry.getKey();
                long best = Long.MAX_VALUE;
                for (IndexedRecipe recipe : entry.getValue()) {
                    long cost = recipeCost(recipe, index, values);
                    if (cost >= 0) best = Math.min(best, cost / recipe.resultCount());
                }
                if (best == Long.MAX_VALUE) continue;
                int floored = (int) Math.min(Integer.MAX_VALUE, Math.max(1, best));
                Integer current = values.get(item);
                if (current == null || floored < current) {
                    values.put(item, floored);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return Map.copyOf(values);
    }

    /** Total ingredient cost, or -1 if any ingredient is still unresolved. */
    private static long recipeCost(IndexedRecipe recipe, Map<Item, List<IndexedRecipe>> index, Map<Item, Integer> values) {
        long cost = 0;
        for (ItemStack[] options : recipe.ingredientOptions()) {
            int cheapest = Integer.MAX_VALUE;
            for (ItemStack option : options) {
                int value = ingredientValue(option.getItem(), index, values);
                if (value >= 0) cheapest = Math.min(cheapest, value);
            }
            if (cheapest == Integer.MAX_VALUE) return -1;
            cost += cheapest;
        }
        return cost;
    }

    /**
     * Per (result, ingredient), the recipe consuming the FEWEST of that
     * ingredient per output — the cheapest crafting path a player could exploit,
     * so it bounds the arbitrage-free payout. Only mandatory slots count (an
     * ingredient with alternatives can be substituted away, so it never forces
     * that material). Comparison is on the ratio {@code mandatoryCount/resultCount}.
     */
    private static Map<Item, Map<Item, int[]>> buildIngredientCounts(Map<Item, List<IndexedRecipe>> index) {
        Map<Item, Map<Item, int[]>> out = new HashMap<>();
        for (Map.Entry<Item, List<IndexedRecipe>> entry : index.entrySet()) {
            Item result = entry.getKey();
            Map<Item, int[]> perResult = new HashMap<>();
            for (IndexedRecipe recipe : entry.getValue()) {
                Map<Item, Integer> mandatory = new HashMap<>();
                for (ItemStack[] options : recipe.ingredientOptions()) {
                    Item only = soleItem(options);
                    if (only != null && only != result) mandatory.merge(only, 1, Integer::sum);
                }
                int resultCount = recipe.resultCount();
                for (Map.Entry<Item, Integer> m : mandatory.entrySet()) {
                    int mandatoryCount = m.getValue();
                    int[] current = perResult.get(m.getKey());
                    // Keep the smallest mandatoryCount/resultCount ratio (cross-multiply).
                    if (current == null
                        || (long) mandatoryCount * current[1] < (long) current[0] * resultCount) {
                        perResult.put(m.getKey(), new int[]{mandatoryCount, resultCount});
                    }
                }
            }
            if (!perResult.isEmpty()) out.put(result, Map.copyOf(perResult));
        }
        return Map.copyOf(out);
    }

    /** The single item every option in this slot resolves to, or null if the slot admits alternatives. */
    private static Item soleItem(ItemStack[] options) {
        Item first = options[0].getItem();
        for (ItemStack option : options) {
            if (option.getItem() != first) return null;
        }
        return first;
    }

    /** Override → derived-so-far → rarity for uncraftable; -1 if not yet resolved. */
    private static int ingredientValue(Item item, Map<Item, List<IndexedRecipe>> index, Map<Item, Integer> values) {
        ItemStack stack = new ItemStack(item);
        OptionalInt override = ItemValuation.overrideValue(stack);
        if (override.isPresent()) return override.getAsInt();
        Integer derived = values.get(item);
        if (derived != null) return derived;
        return index.containsKey(item) ? -1 : ItemValuation.rarityValue(stack);
    }
}
