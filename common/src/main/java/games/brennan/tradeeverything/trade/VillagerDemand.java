package games.brennan.tradeeverything.trade;

/**
 * Duck interface attached to {@code AbstractVillager} by
 * {@code AbstractVillagerTradingMixin}: the villager's {@link DemandLedger},
 * persisted in its NBT, plus a counter bumped on every recorded sale so the
 * payment slot knows to re-quote an item that is still sitting in it.
 */
public interface VillagerDemand {

    DemandLedger tradeeverything$demandLedger();

    void tradeeverything$setDemandLedger(DemandLedger ledger);

    int tradeeverything$demandGeneration();
}
