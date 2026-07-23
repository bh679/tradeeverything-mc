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
                LOGGER.info("[TradeEverything] derived {} item values from {} craftable items in {} ms",
                    derivedValues.size(), index.size(), (System.nanoTime() - start) / 1_000_000);
            } catch (Throwable t) {
                // Memoize the failure too — retrying a broken index on every
                // payment-slot click would hammer the server thread and rethrow
                // inside packet handling. Rarity fallback covers valuation.
                derivedValues = Map.of();
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
     * keeps min-over-recipes, floored at its rarity value. Values only ever
     * decrease, so iteration terminates; unresolved cycles simply stay
     * absent and fall back to rarity at lookup time.
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
                int floored = (int) Math.min(Integer.MAX_VALUE,
                    Math.max(ItemValuation.rarityValue(new ItemStack(item)), best));
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
