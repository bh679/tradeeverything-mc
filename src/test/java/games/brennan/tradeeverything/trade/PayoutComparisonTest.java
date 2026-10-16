package games.brennan.tradeeverything.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayoutComparisonTest {

    /** One emerald, in the value units {@code ItemValuation.valueUnits} returns. */
    private static final int EMERALD = 256;
    /** Paper at a librarian buying 24 per emerald. */
    private static final int PAPER = EMERALD / 24;

    @Test
    void valuationInGoodsBeatsAnEmeraldBuyback() {
        // 1 bookshelf → 7 paper (valuation) vs 1 bookshelf → 8 emeralds (buy-back).
        assertTrue(PayoutComparison.paysLess(1, 7, PAPER, 1, 8, EMERALD));
        assertFalse(PayoutComparison.paysLess(1, 8, EMERALD, 1, 7, PAPER));
    }

    @Test
    void buybackWinsWhenTheItemIsWorthMoreThanItsSellPrice() {
        // Valued at 12 emeralds, sold by the villager for 5 → buy-back pays 4.
        assertFalse(PayoutComparison.paysLess(1, 12, EMERALD, 1, 4, EMERALD));
        assertTrue(PayoutComparison.paysLess(1, 4, EMERALD, 1, 12, EMERALD));
    }

    @Test
    void batchSizesAreComparedPerInputItem() {
        // 4 items → 1 emerald is less per item than 1 item → 1 emerald…
        assertTrue(PayoutComparison.paysLess(4, 1, EMERALD, 1, 1, EMERALD));
        // …and 16 items → 8 emeralds is more per item than 4 items → 1 emerald.
        assertFalse(PayoutComparison.paysLess(16, 8, EMERALD, 4, 1, EMERALD));
    }

    @Test
    void anExactTieIsNotLess() {
        assertFalse(PayoutComparison.paysLess(2, 2, EMERALD, 1, 1, EMERALD));
        assertFalse(PayoutComparison.paysLess(1, 1, EMERALD, 2, 2, EMERALD));
    }

    @Test
    void largeCountsDoNotOverflow() {
        assertTrue(PayoutComparison.paysLess(64, 63, 1_048_576, 64, 64, 1_048_576));
    }
}
