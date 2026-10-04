package games.brennan.tradeeverything.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the agreed curve: the values a villager pays as it buys more of one item. */
class DemandCurveTest {

    private static final DemandCurve CURVE = DemandCurve.defaults();
    private static final String SWORD = "minecraft:diamond_sword";
    private static final String WHEAT = "minecraft:wheat";

    /** Sixteenths paid for the {@code nth} item (1-based) when every sale is one item. */
    private static int priceOfNth(double value, int nth) {
        DemandLedger ledger = DemandLedger.EMPTY;
        for (int i = 1; i < nth; i++) ledger = ledger.withSale("x", 1, value, 0L, CURVE);
        return ledger.factorSixteenths("x", value, 0L, CURVE);
    }

    @Test
    void diamondSwordSlidesOneSaleAtATime() {
        double sword = 8.0;
        assertEquals(16, priceOfNth(sword, 1)); // 8.00
        assertEquals(15, priceOfNth(sword, 2)); // 7.50
        assertEquals(15, priceOfNth(sword, 3));
        assertEquals(14, priceOfNth(sword, 4)); // 7.00
        assertEquals(14, priceOfNth(sword, 5));
        assertEquals(11, priceOfNth(sword, 10)); // 5.50
        assertEquals(8, priceOfNth(sword, 15)); // 4.00
        assertEquals(4, priceOfNth(sword, 25)); // 2.00 floor
        assertEquals(4, priceOfNth(sword, 60));
    }

    @Test
    void wheatHoldsForTwoStacksThenFadesToAQuarter() {
        double wheat = 1.0 / 16;
        assertEquals(16, priceOfNth(wheat, 128)); // last of the 8-emerald allowance
        assertEquals(15, priceOfNth(wheat, 129));
        assertEquals(4, priceOfNth(wheat, 14 * 64 + 1)); // 15th stack: 1/64 emerald
    }

    @Test
    void netheriteReachesTheFloorBySeventhIngot() {
        double netherite = 64.0;
        assertEquals(16, priceOfNth(netherite, 1));
        assertEquals(13, priceOfNth(netherite, 2));
        assertEquals(4, priceOfNth(netherite, 7));
    }

    @Test
    void aStackSoldAtOnceCountsLikeOneAtATime() {
        DemandLedger batch = DemandLedger.EMPTY.withSale(WHEAT, 64, 1.0 / 16, 0L, CURVE)
            .withSale(WHEAT, 64, 1.0 / 16, 0L, CURVE);
        assertEquals(priceOfNth(1.0 / 16, 129), batch.factorSixteenths(WHEAT, 1.0 / 16, 0L, CURVE));
    }

    @Test
    void itemsAreTrackedSeparately() {
        DemandLedger ledger = DemandLedger.EMPTY.withSale(SWORD, 30, 8.0, 0L, CURVE);
        assertEquals(4, ledger.factorSixteenths(SWORD, 8.0, 0L, CURVE));
        assertEquals(16, ledger.factorSixteenths(WHEAT, 1.0 / 16, 0L, CURVE));
    }

    @Test
    void recoversTwoStepsPerDay() {
        double sword = 8.0;
        // 5 swords = 40 emeralds: 8 free + 2.4 steps → 13/16; a day drains two steps → 15/16.
        DemandLedger ledger = DemandLedger.EMPTY.withSale(SWORD, 5, sword, 0L, CURVE);
        assertEquals(13, ledger.factorSixteenths(SWORD, sword, 0L, CURVE));
        assertEquals(15, ledger.factorSixteenths(SWORD, sword, DemandCurve.TICKS_PER_DAY, CURVE));
        assertEquals(16, ledger.factorSixteenths(SWORD, sword, 10 * DemandCurve.TICKS_PER_DAY, CURVE));
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
        assertTrue(!later.entries().containsKey(SWORD));
        assertSame(ledger, ledger.pruned(0L, CURVE));
    }

    @Test
    void disabledCurveAlwaysPaysFull() {
        DemandCurve off = new DemandCurve(false, 8.0, 8.0, 0.25, 4, 2.0);
        assertEquals(16, off.factorSixteenths(10_000.0, 8.0));
    }
}
