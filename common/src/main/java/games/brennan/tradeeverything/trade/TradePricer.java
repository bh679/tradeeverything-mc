package games.brennan.tradeeverything.trade;

import games.brennan.tradeeverything.config.TradeEverythingConfig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Solves the exchange (N × input → M × payout) for the synthetic offer.
 * Picks the (N, M) pair that minimises the player's relative overpay,
 * with counts clamped to stack sizes and the config caps.
 */
public final class TradePricer {

    /** Cost count N and result count M for the synthetic offer. */
    public record Quote(int costCount, int resultCount) {}

    /** Max relative rounding loss accepted before trying a bigger batch. */
    private static final double ACCEPTABLE_OVERPAY = 0.10;

    private TradePricer() {}

    /**
     * Upgrades the payout to emeralds when a single input item is worth more
     * than a full stack of the preferred payout — otherwise the stack cap
     * would silently eat the difference (e.g. a diamond pickaxe capped at
     * 64 chicken instead of its ~9 emeralds).
     */
    public static Item payoutFor(ItemStack input, Item preferred, MerchantOffers offers, TradeEverythingConfig config) {
        if (input.isEmpty()) return preferred;
        double singleValue = ItemValuation.valueSixteenths(input) * config.resultMultiplier();
        if (preferred != Items.EMERALD
            && overflowsStack(singleValue, preferred, payoutValueSixteenths(preferred, offers), config)) {
            preferred = Items.EMERALD;
        }
        // Netherite armor exceeds even a stack of emeralds — escalate once more.
        if (preferred == Items.EMERALD
            && overflowsStack(singleValue, Items.EMERALD, ItemValuation.valueSixteenths(new ItemStack(Items.EMERALD)), config)) {
            preferred = Items.EMERALD_BLOCK;
        }
        return preferred;
    }

    private static boolean overflowsStack(double singleValue, Item payout, int payoutValue, TradeEverythingConfig config) {
        int cap = Math.min(Math.min(64, new ItemStack(payout).getMaxStackSize()), config.maxResultCount());
        return singleValue > (double) payoutValue * cap;
    }

    /**
     * Value of one payout item, preferring the villager's OWN exchange rate:
     * if the villager buys "N × item → M emeralds", one item is worth 16·M/N
     * sixteenths <em>to this villager</em>. Without this, a payout priced off
     * the global table can be worth half (or double) what the same villager
     * pays for it one row down — which reads as the slot short-changing the
     * player. Falls back to the global valuation when the villager doesn't
     * trade the item.
     */
    public static int payoutValueSixteenths(Item payout, MerchantOffers offers) {
        for (MerchantOffer offer : offers) {
            if (SyntheticOfferFactory.isSynthetic(offer)) continue;
            if (!offer.getResult().is(Items.EMERALD)) continue;
            if (offer.getItemCostB().isPresent()) continue;
            if (offer.getItemCostA().item().value() != payout) continue;
            int n = offer.getItemCostA().count();
            int m = offer.getResult().getCount();
            if (n > 0 && m > 0) return Math.max(1, m * 16 / n);
        }
        return ItemValuation.valueSixteenths(new ItemStack(payout));
    }

    public static Optional<Quote> quote(ItemStack input, Item payout, int payoutValue, TradeEverythingConfig config) {
        int valueIn = ItemValuation.valueSixteenths(input);
        int valueOut = payoutValue;
        if (valueIn <= 0 || valueOut <= 0) return Optional.empty();

        int maxCost = Math.min(Math.min(64, input.getMaxStackSize()), config.maxCostCount());
        int maxResult = Math.min(Math.min(64, new ItemStack(payout).getMaxStackSize()), config.maxResultCount());
        double multiplier = config.resultMultiplier();

        // Prefer the SMALLEST batch whose rounding loss is acceptable (1 anvil → 11
        // emeralds beats 5 anvils → 58), falling back to the least-lossy batch.
        double bestScore = Double.MAX_VALUE;
        int bestCost = -1;
        int bestResult = -1;
        for (int n = 1; n <= maxCost; n++) {
            double inValue = n * (double) valueIn * multiplier;
            int m = (int) Math.floor(inValue / valueOut);
            if (m < 1) continue;
            m = Math.min(m, maxResult);
            double score = (inValue - m * (double) valueOut) / inValue; // relative overpay
            if (score <= ACCEPTABLE_OVERPAY) {
                bestCost = n;
                bestResult = m;
                break;
            }
            if (score < bestScore - 1.0e-9) {
                bestScore = score;
                bestCost = n;
                bestResult = m;
            }
        }
        if (bestCost < 0) {
            // Input too cheap to ever afford one payout item within the caps.
            return config.allowUndervaluedTrades()
                ? Optional.of(new Quote(maxCost, 1))
                : Optional.empty();
        }
        // Never pay out an item's own crafting material at or above what the
        // recipe consumes — otherwise craft-then-sell prints materials (an iron
        // block must sell for fewer than the 9 iron ingots it's crafted from).
        // Only a plain, unmodified stack can feed that loop: enchanted or
        // custom-stat items have value beyond their materials (already priced in
        // by ItemValuation) and can't be freely re-crafted, so they're exempt.
        OptionalInt materialCap = isPlainStack(input)
            ? RecipeValues.maxMaterialPayout(input.getItem(), payout, bestCost)
            : OptionalInt.empty();
        if (materialCap.isPresent()) {
            bestResult = Math.min(bestResult, materialCap.getAsInt());
            if (bestResult < 1) return Optional.empty(); // capped below one unit — not a viable trade
        }
        return Optional.of(new Quote(bestCost, bestResult));
    }

    /**
     * True only for a freshly-crafted, unmodified stack — no enchantments,
     * damage, custom name, attribute modifiers, or any other non-default
     * component. Such a stack is interchangeable with its craftable base, so the
     * material-printer cap applies; anything modified is worth more than its raw
     * materials and is exempt.
     */
    private static boolean isPlainStack(ItemStack stack) {
        return stack.getComponentsPatch().isEmpty();
    }
}
