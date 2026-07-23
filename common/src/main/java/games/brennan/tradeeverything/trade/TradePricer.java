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

    private TradePricer() {}

    /**
     * Upgrades the payout to emeralds when a single input item is worth more
     * than a full stack of the preferred payout — otherwise the stack cap
     * would silently eat the difference (e.g. a diamond pickaxe capped at
     * 64 chicken instead of its ~9 emeralds).
     */
    public static Item payoutFor(ItemStack input, Item preferred, TradeEverythingConfig config) {
        if (input.isEmpty() || preferred == Items.EMERALD) return preferred;
        double singleValue = ItemValuation.valueSixteenths(input) * config.resultMultiplier();
        int payoutValue = ItemValuation.valueSixteenths(new ItemStack(preferred));
        int cap = Math.min(Math.min(64, new ItemStack(preferred).getMaxStackSize()), config.maxResultCount());
        return singleValue > (double) payoutValue * cap ? Items.EMERALD : preferred;
    }

    public static Optional<Quote> quote(ItemStack input, Item payout, TradeEverythingConfig config) {
        int valueIn = ItemValuation.valueSixteenths(input);
        int valueOut = ItemValuation.valueSixteenths(new ItemStack(payout));
        if (valueIn <= 0 || valueOut <= 0) return Optional.empty();

        int maxCost = Math.min(Math.min(64, input.getMaxStackSize()), config.maxCostCount());
        int maxResult = Math.min(Math.min(64, new ItemStack(payout).getMaxStackSize()), config.maxResultCount());
        double multiplier = config.resultMultiplier();

        double bestScore = Double.MAX_VALUE;
        int bestCost = -1;
        int bestResult = -1;
        for (int n = 1; n <= maxCost; n++) {
            double inValue = n * (double) valueIn * multiplier;
            int m = (int) Math.floor(inValue / valueOut);
            if (m < 1) continue;
            m = Math.min(m, maxResult);
            double score = (inValue - m * (double) valueOut) / inValue; // relative overpay
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
