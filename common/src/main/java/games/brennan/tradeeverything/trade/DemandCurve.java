package games.brennan.tradeeverything.trade;

/**
 * How a villager tires of one item: the price it pays slides the more of that
 * item it has bought through the Trade Anything row, and drifts back over time.
 *
 * <p>Everything is measured in <b>emeralds of trade value</b> (the item's full
 * value, before the payout margin). A villager pays full price for the first
 * {@link #freeEmeralds} of an item — and always for the first one — then one
 * sixteenth of the item's value less per step. A step is
 * {@code stepEmeralds × value^stepExponent}: with the defaults (8, 0.25) it is
 * 8 emeralds for an item worth one, 4 for wheat, 23 for a netherite ingot — so
 * cheap items fade over many stacks and dear ones a sixteenth or three per sale,
 * instead of either crashing on the second sale or taking thousands to move.</p>
 *
 * <p>The price never falls below the higher of {@link #minFraction} of the
 * item's value and {@link #minEmeralds} — and never above full value, so an item
 * worth no more than {@code minEmeralds} keeps its full price.</p>
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
    double minFraction,
    double minEmeralds,
    double recoveryStepsPerDay
) {

    /** Full price, as the fraction {@link #priceFraction} returns. */
    public static final double FULL = 1.0;

    /** Each step takes this share of the item's value off its price. */
    public static final int STEPS_TO_ZERO = 16;

    public static final long TICKS_PER_DAY = 24_000L;

    /** Absorbs float noise in summed values so an exact step boundary isn't missed. */
    private static final double EPSILON = 1.0e-9;

    public static DemandCurve defaults() {
        return new DemandCurve(true, 8.0, 8.0, 0.25, 0.01, 1.0 / 16, 2.0);
    }

    /** Size of one step, in emeralds of trade, for an item worth {@code valueEmeralds}. */
    public double stepFor(double valueEmeralds) {
        return stepEmeralds * Math.pow(Math.max(valueEmeralds, EPSILON), stepExponent);
    }

    /**
     * The price this villager pays, as a fraction of full value, for an item worth
     * {@code valueEmeralds} after it has already bought {@code soldEmeralds} of it.
     */
    public double priceFraction(double soldEmeralds, double valueEmeralds) {
        if (!enabled || valueEmeralds <= 0.0 || soldEmeralds < freeEmeralds - EPSILON) return FULL;
        int steps = 1 + (int) Math.floor((soldEmeralds - freeEmeralds) / stepFor(valueEmeralds) + EPSILON);
        double price = valueEmeralds * Math.max(0, STEPS_TO_ZERO - steps) / STEPS_TO_ZERO;
        double floor = Math.max(minFraction * valueEmeralds, minEmeralds);
        return Math.min(valueEmeralds, Math.max(price, floor)) / valueEmeralds;
    }

    /** The tally left after {@code elapsedTicks} of recovery; a clock that ran backwards recovers nothing. */
    public double recovered(double soldEmeralds, double valueEmeralds, long elapsedTicks) {
        if (elapsedTicks <= 0) return soldEmeralds;
        double drained = recoveryStepsPerDay * stepFor(valueEmeralds) * elapsedTicks / TICKS_PER_DAY;
        return Math.max(0.0, soldEmeralds - drained);
    }
}
