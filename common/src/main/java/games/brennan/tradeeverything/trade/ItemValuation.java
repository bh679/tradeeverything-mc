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
 * Value engine. Resolution order for a stack's value (in sixteenths of an
 * emerald): registered {@link ItemValueProvider}s → runtime API overrides →
 * config {@code item_overrides_sixteenths} → config rarity map via
 * {@link ItemStack#getRarity()}.
 */
public final class ItemValuation {

    private static final List<ItemValueProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<BuyItemSelector> BUY_ITEM_SELECTORS = new CopyOnWriteArrayList<>();
    private static final Map<ResourceLocation, Integer> RUNTIME_OVERRIDES = new ConcurrentHashMap<>();

    private ItemValuation() {}

    public static int valueSixteenths(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        for (ItemValueProvider provider : PROVIDERS) {
            OptionalInt value = provider.value(stack);
            if (value.isPresent()) return Math.max(1, value.getAsInt());
        }
        return Math.max(1, adjustForStack(baseValue(stack), stack));
    }

    private static int baseValue(ItemStack stack) {
        OptionalInt override = overrideValue(stack);
        if (override.isPresent()) return override.getAsInt();
        int rarity = rarityValue(stack);
        if (TradeEverythingConfig.get().deriveValuesFromRecipes()) {
            OptionalInt derived = RecipeValues.derivedValue(stack.getItem());
            if (derived.isPresent()) return Math.max(rarity, derived.getAsInt());
        }
        return rarity;
    }

    /**
     * Stack-specific adjustments: enchantment levels add value (config
     * per-level, stored book enchants included); durability scales it —
     * 10% when nearly broken, linearly up to 100% at ≥90% durability
     * ({@code factor = min(1, 0.1 + remainingFraction)}).
     */
    private static int adjustForStack(int base, ItemStack stack) {
        int value = base + enchantmentBonus(stack);
        if (stack.isDamageableItem() && stack.isDamaged()) {
            double remaining = 1.0 - (double) stack.getDamageValue() / stack.getMaxDamage();
            double factor = Math.min(1.0, 0.1 + remaining);
            value = (int) Math.round(value * factor);
        }
        return value;
    }

    private static int enchantmentBonus(ItemStack stack) {
        int perLevel = TradeEverythingConfig.get().enchantmentValuePerLevelSixteenths();
        if (perLevel <= 0) return 0;
        int levels = totalLevels(stack.get(DataComponents.ENCHANTMENTS))
            + totalLevels(stack.get(DataComponents.STORED_ENCHANTMENTS));
        return levels * perLevel;
    }

    private static int totalLevels(ItemEnchantments enchantments) {
        if (enchantments == null) return 0;
        int sum = 0;
        for (var entry : enchantments.entrySet()) {
            sum += entry.getIntValue();
        }
        return sum;
    }

    /** Runtime API override, else config override. Package-visible for RecipeValues. */
    static OptionalInt overrideValue(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Integer runtime = RUNTIME_OVERRIDES.get(id);
        if (runtime != null) return OptionalInt.of(runtime);
        Integer override = TradeEverythingConfig.get().itemOverridesSixteenths().get(id.toString());
        return override != null ? OptionalInt.of(override) : OptionalInt.empty();
    }

    /** Config rarity-tier value for the stack. Package-visible for RecipeValues. */
    static int rarityValue(ItemStack stack) {
        String rarity = stack.getRarity().name().toLowerCase(Locale.ROOT);
        return Math.max(1, TradeEverythingConfig.get().rarityValuesSixteenths().getOrDefault(rarity, 1));
    }

    /** Payout item for the villager: API selectors first, then the built-in default. */
    public static Item selectBuyItem(AbstractVillager villager, MerchantOffers offers) {
        for (BuyItemSelector selector : BUY_ITEM_SELECTORS) {
            Optional<Item> chosen = selector.selectBuyItem(villager, offers);
            if (chosen.isPresent()) return chosen.get();
        }
        return DefaultBuyItemSelector.select(offers);
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
