package games.brennan.tradeeverything.trade;

/**
 * Compares two candidate offers for the same input by what each pays per
 * input item. Offers can pay in different items (the buy-back pays the sell
 * row's currency, the valuation quote pays the villager's goods), so counts
 * alone don't compare — each side carries the value of one payout item.
 *
 * <p>Plain ints, no Minecraft types, so it unit-tests without a bootstrap.</p>
 */
final class PayoutComparison {

    private PayoutComparison() {}

    /**
     * Whether offer A pays strictly less per input item than offer B.
     * Cross-multiplied, so no rounding decides a close call.
     *
     * @param costA        input items offer A takes
     * @param resultA      payout items offer A gives
     * @param unitValueA   value of one of A's payout items
     * @param costB        input items offer B takes
     * @param resultB      payout items offer B gives
     * @param unitValueB   value of one of B's payout items
     */
    static boolean paysLess(int costA, int resultA, int unitValueA,
                            int costB, int resultB, int unitValueB) {
        long a = (long) resultA * unitValueA * costB;
        long b = (long) resultB * unitValueB * costA;
        return a < b;
    }
}
