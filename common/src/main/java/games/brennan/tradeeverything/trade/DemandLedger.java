package games.brennan.tradeeverything.trade;

import java.util.HashMap;
import java.util.Map;

/**
 * One villager's memory of what it has bought through the Trade Anything row:
 * per item id, the emeralds of trade value bought and when it was last updated.
 * Immutable — every sale returns a new ledger — and keyed by plain item-id
 * strings so it stays free of Minecraft types (see {@link DemandCurve}).
 *
 * <p>Recovery is lazy: an entry stores the tally as of {@link Entry#tick} and the
 * item value it drains at, and {@link DemandCurve#recovered} brings it up to the
 * current game time whenever it is read.</p>
 */
public final class DemandLedger {

    /**
     * @param soldEmeralds emeralds of trade value bought, as of {@code tick}
     * @param tick         game time of the last update
     * @param valueEmeralds value of one item at the last sale — sets the step size, so the drain rate
     */
    public record Entry(double soldEmeralds, long tick, double valueEmeralds) {}

    public static final DemandLedger EMPTY = new DemandLedger(Map.of());

    private final Map<String, Entry> entries;

    private DemandLedger(Map<String, Entry> entries) {
        this.entries = entries;
    }

    public static DemandLedger of(Map<String, Entry> entries) {
        return entries.isEmpty() ? EMPTY : new DemandLedger(Map.copyOf(entries));
    }

    public Map<String, Entry> entries() {
        return entries;
    }

    /** Emeralds of {@code itemId} bought, recovered up to {@code now}. */
    public double soldEmeralds(String itemId, long now, DemandCurve curve) {
        Entry entry = entries.get(itemId);
        if (entry == null) return 0.0;
        return curve.recovered(entry.soldEmeralds(), entry.valueEmeralds(), now - entry.tick());
    }

    /** What this villager pays for {@code itemId} right now, as a fraction of full value. */
    public double priceFraction(String itemId, double valueEmeralds, long now, DemandCurve curve) {
        return curve.priceFraction(soldEmeralds(itemId, now, curve), valueEmeralds);
    }

    /** This ledger plus a sale of {@code count} items worth {@code valueEmeralds} each. */
    public DemandLedger withSale(String itemId, int count, double valueEmeralds, long now, DemandCurve curve) {
        if (count <= 0 || valueEmeralds <= 0.0) return this;
        double sold = soldEmeralds(itemId, now, curve) + count * valueEmeralds;
        Map<String, Entry> next = new HashMap<>(entries);
        next.put(itemId, new Entry(sold, now, valueEmeralds));
        return of(next);
    }

    /** This ledger without the entries that have fully recovered by {@code now} — what is worth saving. */
    public DemandLedger pruned(long now, DemandCurve curve) {
        Map<String, Entry> next = new HashMap<>();
        entries.forEach((id, entry) -> {
            if (soldEmeralds(id, now, curve) > 0.0) next.put(id, entry);
        });
        return next.size() == entries.size() ? this : of(next);
    }
}
