package games.brennan.tradeeverything.trade;

/**
 * How a villager tires of one item: the price it pays slides the more of that
 * item it has bought through the Trade Anything row, and drifts back over time.
 *
 * <p>Everything is measured in <b>emeralds of trade value</b> (the item's full
 * value, before the payout margin). A villager pays full price for the first
 * {@link #freeEmeralds} of an item — and always for the first one — then one
 * sixteenth less per step, down to {@link #minSixteenths}. A step is
 * {@code stepEmeralds × value^stepExponent}: with the defaults (8, 0.25) it is
 * 8 emeralds for an item worth one, 4 for wheat, 23 for a netherite ingot — so
 * cheap items fade over many stacks and dear ones a sixteenth or three per sale,
 * instead of either crashing on the second sale or taking thousands to move.</p>
 *
 * <p>Recovery drains the tally by {@link #recoveryStepsPerDay} steps per in-game
 * day (24 000 game ticks), computed lazily from the elapsed game time.</p>
 *
 * <p>No Minecraft types, so the curve is unit-testable on its own.</p>
 */
public record DemandCurve(
    boolean enabled,
    double freeEmeralds,
    double stepEmeralds,
    double stepExponent,
    int minSixteenths,
    double recoveryStepsPerDay
) {

    /** Full price, in the sixteenths {@link #factorSixteenths} returns. */
    public static final int FULL = 16;

    public static final long TICKS_PER_DAY = 24_000L;

    /** Absorbs float noise in summed values so an exact step boundary isn't missed. */
    private static final double EPSILON = 1.0e-9;

    public static DemandCurve defaults() {
        return new DemandCurve(true, 8.0, 8.0, 0.25, 4, 2.0);
    }

    /** Size of one sixteenth-step, in emeralds of trade, for an item worth {@code valueEmeralds}. */
    public double stepFor(double valueEmeralds) {
        return stepEmeralds * Math.pow(Math.max(valueEmeralds, EPSILON), stepExponent);
    }

    /**
     * The price this villager pays, in sixteenths of full value, for an item worth
     * {@code valueEmeralds} after it has already bought {@code soldEmeralds} of it.
     */
    public int factorSixteenths(double soldEmeralds, double valueEmeralds) {
        if (!enabled || soldEmeralds < freeEmeralds - EPSILON) return FULL;
        int steps = (int) Math.floor((soldEmeralds - freeEmeralds) / stepFor(valueEmeralds) + EPSILON);
        return Math.max(minSixteenths, FULL - 1 - steps);
    }

    /** The tally left after {@code elapsedTicks} of recovery; a clock that ran backwards recovers nothing. */
    public double recovered(double soldEmeralds, double valueEmeralds, long elapsedTicks) {
        if (elapsedTicks <= 0) return soldEmeralds;
        double drained = recoveryStepsPerDay * stepFor(valueEmeralds) * elapsedTicks / TICKS_PER_DAY;
        return Math.max(0.0, soldEmeralds - drained);
    }
}
