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
    void diamondSwordSlidesOneSaleAtATimeDownToOnePercent() {
        double sword = 8.0;
        assertEquals(8.0, priceOfNth(sword, 1), DELTA);
        assertEquals(7.5, priceOfNth(sword, 2), DELTA);
        assertEquals(7.5, priceOfNth(sword, 3), DELTA);
        assertEquals(7.0, priceOfNth(sword, 4), DELTA);
        assertEquals(5.5, priceOfNth(sword, 10), DELTA);
        assertEquals(4.0, priceOfNth(sword, 15), DELTA);
        assertEquals(1.0, priceOfNth(sword, 25), DELTA);
        assertEquals(0.08, priceOfNth(sword, 30), DELTA); // 1% of 8
        assertEquals(0.08, priceOfNth(sword, 60), DELTA);
    }

    @Test
    void netheriteBottomsOutAtOnePercent() {
        double netherite = 64.0;
        assertEquals(64.0, priceOfNth(netherite, 1), DELTA);
        assertEquals(52.0, priceOfNth(netherite, 2), DELTA);
        assertEquals(0.64, priceOfNth(netherite, 7), DELTA);
    }

    @Test
    void cheapItemsBottomOutAtASixtyFourthOfAnEmerald() {
        double book = 1.0 / 8;
        assertEquals(book, priceOfNth(book, 64), DELTA); // last of the 8-emerald allowance
        assertTrue(priceOfNth(book, 65) < book);
        assertEquals(1.0 / 64, priceOfNth(book, 5000), DELTA);
        double wheat = 1.0 / 16;
        assertEquals(wheat, priceOfNth(wheat, 128), DELTA);
        assertTrue(priceOfNth(wheat, 129) < wheat);
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
        // 5 swords = 40 emeralds: 8 free + 2.4 steps → 13/16; a day drains two steps → 15/16.
        DemandLedger ledger = DemandLedger.EMPTY.withSale(SWORD, 5, sword, 0L, CURVE);
        assertEquals(13.0 / 16, ledger.priceFraction(SWORD, sword, 0L, CURVE), DELTA);
        assertEquals(15.0 / 16, ledger.priceFraction(SWORD, sword, DemandCurve.TICKS_PER_DAY, CURVE), DELTA);
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
        DemandCurve off = new DemandCurve(false, 8.0, 8.0, 0.25, 0.01, 1.0 / 64, 2.0);
        assertEquals(1.0, off.priceFraction(10_000.0, 8.0), DELTA);
    }
}
