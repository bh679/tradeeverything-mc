package games.brennan.tradeeverything.trade;

import games.brennan.tradeeverything.config.TradeEverythingConfig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

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
    public static Item payoutFor(ItemStack input, Item preferred, TradeEverythingConfig config) {
        if (input.isEmpty()) return preferred;
        double singleValue = ItemValuation.valueSixteenths(input) * config.resultMultiplier();
        if (preferred != Items.EMERALD && overflowsStack(singleValue, preferred, config)) {
            preferred = Items.EMERALD;
        }
        // Netherite armor exceeds even a stack of emeralds — escalate once more.
        if (preferred == Items.EMERALD && overflowsStack(singleValue, Items.EMERALD, config)) {
            preferred = Items.EMERALD_BLOCK;
        }
        return preferred;
    }

    private static boolean overflowsStack(double singleValue, Item payout, TradeEverythingConfig config) {
        int payoutValue = ItemValuation.valueSixteenths(new ItemStack(payout));
        int cap = Math.min(Math.min(64, new ItemStack(payout).getMaxStackSize()), config.maxResultCount());
        return singleValue > (double) payoutValue * cap;
    }

    public static Optional<Quote> quote(ItemStack input, Item payout, TradeEverythingConfig config) {
        int valueIn = ItemValuation.valueSixteenths(input);
        int valueOut = ItemValuation.valueSixteenths(new ItemStack(payout));
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
        return Optional.of(new Quote(bestCost, bestResult));
    }
}
