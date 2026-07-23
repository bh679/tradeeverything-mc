package games.brennan.tradeeverything.trade;

/**
 * Server-thread flag suppressing synthetic-offer repricing while
 * {@code MerchantMenu.tryMoveItems} ejects and refills the payment slots —
 * without it, the eject phase empties the slots, repricing resets offer 0 to
 * the unmatchable placeholder, and the refill then has nothing to match
 * (clicking the Trade Anything row dead-ended and spat the payment out).
 */
public final class RepriceSuppression {

    private static final ThreadLocal<Boolean> SUPPRESSED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private RepriceSuppression() {}

    public static boolean isSuppressed() {
        return SUPPRESSED.get();
    }

    public static void begin() {
        SUPPRESSED.set(Boolean.TRUE);
    }

    /** Unconditional clear — safe to call even when begin() didn't run. */
    public static void end() {
        SUPPRESSED.set(Boolean.FALSE);
    }
}
