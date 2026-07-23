package games.brennan.tradeeverything.trade;

import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import games.brennan.tradeeverything.mixin.SmithingTransformRecipeAccessor;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recipe-derived item values: a craftable item is worth its cheapest crafting
 * recipe — the sum of its ingredients' values (recursively derived, config
 * overrides winning) divided by the output count. Cheapest-recipe pricing is
 * arbitrage-free: crafting then selling never beats selling the materials.
 *
 * <p>The recipe index rebuilds whenever the server's {@link RecipeManager}
 * instance changes (world load, {@code /reload}); derived values are memoised
 * until then. Cycles (e.g. iron ingot ⇄ iron block/nugget) resolve by treating
 * the in-progress item as unavailable for that path.</p>
 */
public final class RecipeValues {

    /** ingredients (as option arrays) + output count for one recipe. */
    private record IndexedRecipe(List<ItemStack[]> ingredientOptions, int resultCount) {}

    private static final int MAX_DEPTH = 16;

    private static volatile RecipeManager indexedManager;
    private static volatile Map<Item, List<IndexedRecipe>> recipesByResult = Map.of();
    private static final Map<Item, OptionalInt> MEMO = new ConcurrentHashMap<>();

    private RecipeValues() {}

    /** Rebuilds the index if the server's recipe manager changed. Cheap when unchanged. */
    public static void ensureIndexed(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        if (manager == indexedManager) return;
        synchronized (RecipeValues.class) {
            if (manager == indexedManager) return;
            Map<Item, List<IndexedRecipe>> index = new HashMap<>();
            for (RecipeHolder<?> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
                ItemStack result = holder.value().getResultItem(server.registryAccess());
                if (result.isEmpty()) continue;
                NonNullList<Ingredient> ingredients = holder.value().getIngredients();
                if (ingredients.isEmpty()) continue;
                addRecipe(index, result, ingredients);
            }
            // Smithing transforms (netherite gear): template + base + addition,
            // all consumed — without these a netherite sword prices as COMMON.
            for (RecipeHolder<?> holder : manager.getAllRecipesFor(RecipeType.SMITHING)) {
                if (!(holder.value() instanceof SmithingTransformRecipe smithing)) continue;
                ItemStack result = smithing.getResultItem(server.registryAccess());
                if (result.isEmpty()) continue;
                SmithingTransformRecipeAccessor accessor = (SmithingTransformRecipeAccessor) smithing;
                addRecipe(index, result, List.of(
                    accessor.tradeeverything$template(),
                    accessor.tradeeverything$base(),
                    accessor.tradeeverything$addition()
                ));
            }
            MEMO.clear();
            recipesByResult = index;
            indexedManager = manager;
        }
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

    /** Cheapest-recipe value in sixteenths, or empty if the item has no crafting recipe. */
    public static OptionalInt derivedValue(Item item) {
        return MEMO.computeIfAbsent(item, i -> compute(i, new ArrayDeque<>()));
    }

    private static OptionalInt compute(Item item, Deque<Item> visiting) {
        if (visiting.contains(item) || visiting.size() >= MAX_DEPTH) return OptionalInt.empty();
        List<IndexedRecipe> recipes = recipesByResult.get(item);
        if (recipes == null) return OptionalInt.empty();

        visiting.push(item);
        try {
            int best = Integer.MAX_VALUE;
            for (IndexedRecipe recipe : recipes) {
                long cost = 0;
                boolean priceable = true;
                for (ItemStack[] options : recipe.ingredientOptions()) {
                    int cheapest = Integer.MAX_VALUE;
                    for (ItemStack option : options) {
                        cheapest = Math.min(cheapest, ingredientValue(option.getItem(), visiting));
                    }
                    if (cheapest == Integer.MAX_VALUE) { priceable = false; break; }
                    cost += cheapest;
                }
                if (!priceable) continue;
                int value = (int) Math.min(Integer.MAX_VALUE, cost / recipe.resultCount());
                best = Math.min(best, value);
            }
            return best == Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of(best);
        } finally {
            visiting.pop();
        }
    }

    /**
     * Ingredient value inside recipe math: config/runtime overrides win, then
     * recursive recipe derivation, then rarity — mirroring the public
     * resolution order minus provider hooks (those see full stacks, not items).
     */
    private static int ingredientValue(Item item, Deque<Item> visiting) {
        ItemStack stack = new ItemStack(item);
        OptionalInt override = ItemValuation.overrideValue(stack);
        if (override.isPresent()) return override.getAsInt();
        OptionalInt derived = compute(item, visiting);
        int rarity = ItemValuation.rarityValue(stack);
        return derived.isPresent() ? Math.max(rarity, derived.getAsInt()) : rarity;
    }
}
