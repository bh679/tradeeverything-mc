package games.brennan.tradeeverything.trade;

import games.brennan.tradeeverything.config.TradeEverythingConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The Minecraft side of villager demand: reads and records a villager's
 * {@link DemandLedger} against the live game clock and item valuation, and
 * (de)serialises it to the villager's NBT. See {@link DemandCurve} for the curve.
 */
public final class VillagerDemands {

    /** Villager NBT key holding the ledger: item id → {sold, tick, value}. */
    public static final String NBT_KEY = "tradeeverything:demand";
    private static final String SOLD = "sold";
    private static final String TICK = "tick";
    private static final String VALUE = "value";

    /** Internal value units in one emerald (16 sixteenths × {@link ItemValuation#PRECISION}). */
    private static final double UNITS_PER_EMERALD = 16.0 * ItemValuation.PRECISION;

    private VillagerDemands() {}

    /** What {@code villager} pays for one of {@code input} right now, as a fraction of full value. */
    public static double priceFraction(AbstractVillager villager, ItemStack input) {
        DemandCurve curve = TradeEverythingConfig.get().demand();
        if (!curve.enabled() || input.isEmpty() || !(villager instanceof VillagerDemand demand)) {
            return DemandCurve.FULL;
        }
        return demand.tradeeverything$demandLedger()
            .priceFraction(itemId(input), valueEmeralds(input), villager.level().getGameTime(), curve);
    }

    /** Remembers a completed Trade Anything sale: the offer's cost is what the villager bought. */
    public static void recordSale(AbstractVillager villager, MerchantOffer offer) {
        DemandCurve curve = TradeEverythingConfig.get().demand();
        if (!curve.enabled() || !(villager instanceof VillagerDemand demand)) return;
        ItemStack bought = offer.getCostA();
        if (bought.isEmpty()) return;
        DemandLedger next = demand.tradeeverything$demandLedger().withSale(
            itemId(bought), bought.getCount(), valueEmeralds(bought), villager.level().getGameTime(), curve);
        demand.tradeeverything$setDemandLedger(next);
    }

    /** Bumped on every recorded sale; 0 for a merchant that keeps no ledger. */
    public static int generation(AbstractVillager villager) {
        return villager instanceof VillagerDemand demand ? demand.tradeeverything$demandGeneration() : 0;
    }

    /**
     * The still-unrecovered entries as the compound stored under {@link #NBT_KEY},
     * or null when the villager has fully recovered (nothing is written).
     */
    @Nullable
    public static CompoundTag encode(AbstractVillager villager) {
        if (!(villager instanceof VillagerDemand demand)) return null;
        DemandLedger ledger = demand.tradeeverything$demandLedger()
            .pruned(villager.level().getGameTime(), TradeEverythingConfig.get().demand());
        if (ledger.entries().isEmpty()) return null;
        CompoundTag root = new CompoundTag();
        ledger.entries().forEach((id, entry) -> {
            CompoundTag e = new CompoundTag();
            e.putDouble(SOLD, entry.soldEmeralds());
            e.putLong(TICK, entry.tick());
            e.putDouble(VALUE, entry.valueEmeralds());
            root.put(id, e);
        });
        return root;
    }

    /** Restores the ledger from the compound stored under {@link #NBT_KEY}; null starts fresh. */
    public static void decode(AbstractVillager villager, @Nullable CompoundTag root) {
        if (!(villager instanceof VillagerDemand demand)) return;
        if (root == null) {
            demand.tradeeverything$setDemandLedger(DemandLedger.EMPTY);
            return;
        }
        Map<String, DemandLedger.Entry> entries = new HashMap<>();
        //? if >=1.21.11 {
        /*for (String id : root.keySet()) {
            CompoundTag e = root.getCompoundOrEmpty(id);
            double sold = e.getDoubleOr(SOLD, 0.0);
            double value = e.getDoubleOr(VALUE, 0.0);
            long tick = e.getLongOr(TICK, 0L);
        *///?} else {
        for (String id : root.getAllKeys()) {
            CompoundTag e = root.getCompound(id);
            double sold = e.getDouble(SOLD);
            double value = e.getDouble(VALUE);
            long tick = e.getLong(TICK);
        //?}
            // A hand-edited or corrupt entry is dropped rather than trusted.
            if (sold > 0.0 && value > 0.0 && Double.isFinite(sold) && Double.isFinite(value)) {
                entries.put(id, new DemandLedger.Entry(sold, tick, value));
            }
        }
        demand.tradeeverything$setDemandLedger(DemandLedger.of(entries));
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** One item's full trade value in emeralds — the unit {@link DemandCurve} speaks. */
    private static double valueEmeralds(ItemStack stack) {
        return ItemValuation.valueUnits(stack.copyWithCount(1)) / UNITS_PER_EMERALD;
    }
}
