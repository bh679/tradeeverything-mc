package games.brennan.tradeeverything.trade;

import games.brennan.tradeeverything.api.BuyItemSelector;
import games.brennan.tradeeverything.api.ItemValueProvider;
import games.brennan.tradeeverything.config.TradeEverythingConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Integer runtime = RUNTIME_OVERRIDES.get(id);
        if (runtime != null) return runtime;
        TradeEverythingConfig config = TradeEverythingConfig.get();
        Integer override = config.itemOverridesSixteenths().get(id.toString());
        if (override != null) return override;
        String rarity = stack.getRarity().name().toLowerCase(Locale.ROOT);
        return Math.max(1, config.rarityValuesSixteenths().getOrDefault(rarity, 1));
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
