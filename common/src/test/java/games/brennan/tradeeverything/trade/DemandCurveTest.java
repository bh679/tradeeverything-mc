package games.brennan.tradeeverything.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the agreed curve: what a villager pays, in emeralds, as it buys more of one item. */
class DemandCurveTest {

    private static final DemandCurve CURVE = DemandCurve.defaults();
    private static final String SWORD = "minecraft:diamond_sword";
    private static final String WHEAT = "minecraft:wheat";
    private static final double DELTA = 1.0e-9;

    /** Emeralds paid for the {@code nth} item (1-based) when every sale is one item. */
    private static double priceOfNth(double value, int nth) {
        DemandLedger ledger = DemandLedger.EMPTY;
        for (int i = 1; i < nth; i++) ledger = ledger.withSale("x", 1, value, 0L, CURVE);
        return value * ledger.priceFraction("x", value, 0L, CURVE);
    }

    @Test
    void netheriteAxeEasesDownInsteadOfDroppingOffACliff() {
        // Paid at the 0.75 margin, in emerald blocks (9) then emeralds — the agreed shape.
        double axe = 108.0;
        String[] expected = {"9b", "6b", "3b", "2b", "1b", "1b", "7e", "4e", "3e", "2e", "1e"};
        for (int n = 1; n <= expected.length; n++) {
            double paid = priceOfNth(axe, n) * 0.75;
            String shown = paid >= 9 ? (int) (paid / 9) + "b" : (int) paid + "e";
            assertEquals(expected[n - 1], shown, "axe #" + n);
        }
    }

    @Test
    void diamondSwordLosesNinePercentPerStep() {
        double sword = 8.0;
        assertEquals(8.0, priceOfNth(sword, 1), DELTA);
        assertEquals(8.0 * 0.91, priceOfNth(sword, 2), DELTA);
        assertEquals(8.0 * 0.91, priceOfNth(sword, 3), DELTA);
        assertEquals(8.0 * 0.91 * 0.91, priceOfNth(sword, 4), DELTA);
        assertEquals(2.834949, priceOfNth(sword, 20), 1.0e-6);
        assertEquals(0.294808, priceOfNth(sword, 60), 1.0e-6);
        assertEquals(0.08, priceOfNth(sword, 200), DELTA); // floor: 1% of 8
    }

    @Test
    void netheriteBottomsOutAtOnePercent() {
        double netherite = 64.0;
        assertEquals(64.0, priceOfNth(netherite, 1), DELTA);
        assertEquals(48.228544, priceOfNth(netherite, 2), 1.0e-6);
        assertEquals(0.64, priceOfNth(netherite, 30), DELTA);
    }

    @Test
    void cheapItemsBottomOutAtASixtyFourthOfAnEmerald() {
        double book = 1.0 / 8;
        assertEquals(book, priceOfNth(book, 64), DELTA); // last of the 8-emerald allowance
        assertEquals(book * 0.91, priceOfNth(book, 65), DELTA);
        assertEquals(1.0 / 64, priceOfNth(book, 20_000), DELTA);
        double wheat = 1.0 / 16;
        assertEquals(wheat, priceOfNth(wheat, 128), DELTA);
        assertEquals(wheat * 0.91, priceOfNth(wheat, 129), DELTA);
        assertEquals(1.0 / 64, priceOfNth(wheat, 5000), DELTA);
    }

    @Test
    void itemsWorthASixtyFourthOrLessNeverDrop() {
        assertEquals(1.0 / 64, priceOfNth(1.0 / 64, 50_000), DELTA);
        assertEquals(1.0 / 128, priceOfNth(1.0 / 128, 50_000), DELTA);
    }

    @Test
    void aStackSoldAtOnceCountsLikeOneAtATime() {
        double book = 1.0 / 8;
        DemandLedger batch = DemandLedger.EMPTY.withSale("x", 64, book, 0L, CURVE)
            .withSale("x", 64, book, 0L, CURVE);
        assertEquals(priceOfNth(book, 129), book * batch.priceFraction("x", book, 0L, CURVE), DELTA);
    }

    @Test
    void itemsAreTrackedSeparately() {
        DemandLedger ledger = DemandLedger.EMPTY.withSale(SWORD, 30, 8.0, 0L, CURVE);
        assertTrue(ledger.priceFraction(SWORD, 8.0, 0L, CURVE) < 1.0);
        assertEquals(1.0, ledger.priceFraction(WHEAT, 1.0 / 16, 0L, CURVE), DELTA);
    }

    @Test
    void recoversTwoStepsPerDay() {
        double sword = 8.0;
        // 5 swords = 40 emeralds: 8 free + 2.4 steps → 3 steps; a day drains two → 1 step.
        DemandLedger ledger = DemandLedger.EMPTY.withSale(SWORD, 5, sword, 0L, CURVE);
        assertEquals(Math.pow(0.91, 3), ledger.priceFraction(SWORD, sword, 0L, CURVE), DELTA);
        assertEquals(0.91, ledger.priceFraction(SWORD, sword, DemandCurve.TICKS_PER_DAY, CURVE), DELTA);
        assertEquals(1.0, ledger.priceFraction(SWORD, sword, 10 * DemandCurve.TICKS_PER_DAY, CURVE), DELTA);
    }

    @Test
    void aClockRunningBackwardsRecoversNothing() {
        assertEquals(20.0, CURVE.recovered(20.0, 8.0, -5_000L));
    }

    @Test
    void fullyRecoveredEntriesArePrunedBeforeSaving() {
        DemandLedger ledger = DemandLedger.EMPTY
            .withSale(SWORD, 1, 8.0, 0L, CURVE)
            .withSale(WHEAT, 640, 1.0 / 16, 0L, CURVE);
        DemandLedger later = ledger.pruned(DemandCurve.TICKS_PER_DAY, CURVE);
        assertTrue(later.entries().containsKey(WHEAT));
        assertFalse(later.entries().containsKey(SWORD));
        assertSame(ledger, ledger.pruned(0L, CURVE));
    }

    @Test
    void disabledCurveAlwaysPaysFull() {
        DemandCurve off = new DemandCurve(false, 8.0, 8.0, 0.25, 0.91, 0.01, 1.0 / 64, 2.0);
        assertEquals(1.0, off.priceFraction(10_000.0, 8.0), DELTA);
    }
}
