package games.brennan.tradeeverything.trade;

import games.brennan.tradeeverything.config.TradeEverythingConfig;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Solves the exchange (N × input → M × payout) for the synthetic offer.
 * With {@code prefer_single_item_trades} (the default) a single input item is
 * quoted on its own whenever it can afford one payout unit, and the remainder
 * rounds down; otherwise — and always for inputs too cheap to buy a unit alone —
 * it picks the (N, M) pair that minimises the player's relative overpay. Counts
 * are clamped to stack sizes and the config caps.
 */
public final class TradePricer {

    /** Cost count N and result count M for the synthetic offer. */
    public record Quote(int costCount, int resultCount) {}

    /** Max relative rounding loss accepted before trying a bigger batch. */
    private static final double ACCEPTABLE_OVERPAY = 0.10;

    /** Components that only record wear — see {@link #isCraftEquivalent(ItemStack)}. */
    private static final Set<DataComponentType<?>> DURABILITY_COMPONENTS =
        Set.of(DataComponents.DAMAGE, DataComponents.MAX_DAMAGE);

    private TradePricer() {}

    /**
     * Picks the payout item at a granularity the input can actually pay for.
     * Upgrades to emeralds (then emerald blocks) when a single input item is
     * worth more than a full stack of the preferred payout — otherwise the
     * stack cap would silently eat the difference (e.g. a diamond pickaxe
     * capped at 64 chicken instead of its ~9 emeralds) — and downgrades to
     * emeralds when even a full batch of the input can't afford one unit.
     */
    public static Item payoutFor(ItemStack input, Item preferred, MerchantOffers offers, TradeEverythingConfig config) {
        if (input.isEmpty()) return preferred;
        // The payout margin is per-payout-item, so it is re-read every time the
        // ladder considers a different one: deciding the ladder on a margin that
        // quote() won't use is how a batch passes the affordability test below and
        // then falls through to the undervalued fallback for a whole payout unit.
        int rawValue = ItemValuation.valueUnits(input);
        if (preferred != Items.EMERALD
            && overflowsStack(rawValue * effectiveMultiplier(preferred, config), preferred,
                payoutValueUnits(preferred, offers), config)) {
            preferred = Items.EMERALD;
        }
        // Netherite armor exceeds even a stack of emeralds — escalate once more.
        if (preferred == Items.EMERALD
            && overflowsStack(rawValue * effectiveMultiplier(Items.EMERALD, config), Items.EMERALD,
                emeraldValue(), config)) {
            preferred = Items.EMERALD_BLOCK;
        }
        // Mirror of the escalation: a payout unit that a FULL batch of the input
        // can't afford leaves quote() nothing to do but hand over one whole unit
        // anyway (the allow_undervalued_trades fallback), so the player is paid
        // more than they gave. Harmless at emerald granularity, an emerald
        // printer at 144-sixteenth emerald blocks (64 wheat → 9 emeralds). Step
        // down to emeralds so that fallback can never overpay by more than one.
        if (preferred != Items.EMERALD) {
            int unit = payoutValueUnits(preferred, offers);
            if (unit > emeraldValue()
                && maxBatchValue(input, rawValue * effectiveMultiplier(preferred, config), config) < unit) {
                preferred = Items.EMERALD;
            }
        }
        return preferred;
    }

    private static boolean overflowsStack(double singleValue, Item payout, int payoutValue, TradeEverythingConfig config) {
        int cap = Math.min(Math.min(64, new ItemStack(payout).getMaxStackSize()), config.maxResultCount());
        return singleValue > (double) payoutValue * cap;
    }

    /**
     * The payout margin for one specific payout item: the global
     * {@code result_multiplier} times that item's {@code payout_multipliers} entry.
     * A premium currency pays out a fraction of face value — the shipped default
     * halves whatever a diamond payout is bought with — so this is deliberately
     * per-payout-item rather than global.
     */
    private static double effectiveMultiplier(Item payout, TradeEverythingConfig config) {
        return config.resultMultiplier() * payoutMultiplier(payout, config);
    }

    /** That payout item's {@code payout_multipliers} entry, or 1.0 when it has none. */
    public static double payoutMultiplier(Item payout, TradeEverythingConfig config) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(payout);
        Double multiplier = config.payoutMultipliers().get(id.toString());
        return multiplier != null ? multiplier : 1.0;
    }

    /** Discounted worth of the largest batch {@link #quote} may charge for. */
    private static double maxBatchValue(ItemStack input, double singleValue, TradeEverythingConfig config) {
        return singleValue * Math.min(Math.min(64, input.getMaxStackSize()), config.maxCostCount());
    }

    private static int emeraldValue() {
        return ItemValuation.valueUnits(new ItemStack(Items.EMERALD));
    }

    /**
     * Value of one payout item: the villager's OWN exchange rate when that is
     * the <b>dearer</b> of the two, else the global valuation. If the villager
     * buys "N × item → M emeralds", one item is worth 16·M/N sixteenths to this
     * villager, and honouring a rate above the table keeps payouts consistent
     * with the villager's own visible rows rather than reading as
     * short-changing.
     *
     * <p>A rate <em>below</em> the table must never be used, because it prices
     * only one leg of the exchange: the item handed over is stamped cheap while
     * {@link #quote} values the same item at the global table when it comes back
     * in, so the difference is a free printer. A leatherworker buying 6 leather
     * for an emerald stamped leather at 2 against a table value of 4 — 8 rabbit
     * hides bought 3 leather, and those 3 leather sold back for 9 hides.</p>
     */
    public static int payoutValueUnits(Item payout, MerchantOffers offers) {
        int global = ItemValuation.valueUnits(new ItemStack(payout));
        for (MerchantOffer offer : offers) {
            if (SyntheticOfferFactory.isSynthetic(offer)) continue;
            if (!offer.getResult().is(Items.EMERALD)) continue;
            if (offer.getItemCostB().isPresent()) continue;
            if (offer.getItemCostA().item().value() != payout) continue;
            int n = offer.getItemCostA().count();
            int m = offer.getResult().getCount();
            if (n > 0 && m > 0) return Math.max(global, m * 16 * ItemValuation.PRECISION / n);
        }
        return global;
    }

    public static Optional<Quote> quote(ItemStack input, Item payout, int payoutValue, TradeEverythingConfig config) {
        int valueIn = ItemValuation.valueUnits(input);
        int valueOut = payoutValue;
        if (valueIn <= 0 || valueOut <= 0) return Optional.empty();

        int maxCost = Math.min(Math.min(64, input.getMaxStackSize()), config.maxCostCount());
        int maxResult = Math.min(Math.min(64, new ItemStack(payout).getMaxStackSize()), config.maxResultCount());
        double multiplier = effectiveMultiplier(payout, config);

        // Prefer the SMALLEST batch whose rounding loss is acceptable (1 anvil → 11
        // emeralds beats 5 anvils → 58), falling back to the least-lossy batch.
        double bestScore = Double.MAX_VALUE;
        int bestCost = -1;
        int bestResult = -1;
        for (int n = 1; n <= maxCost; n++) {
            double inValue = n * (double) valueIn * multiplier;
            int m = (int) Math.floor(inValue / valueOut);
            if (m < 1) continue;
            m = Math.min(m, maxResult);
            // One item that already affords a payout unit is quoted alone, change
            // rounded down — trading one at a time reads better than a batch priced
            // for exact change (1 ominous banner → 3 emeralds, not 2 → 7). Cheaper
            // inputs never reach here at n = 1 (m < 1), so they still batch.
            if (config.preferSingleItemTrades() && n == 1) {
                bestCost = n;
                bestResult = m;
                break;
            }
            double score = (inValue - m * (double) valueOut) / inValue; // relative overpay
            if (score <= ACCEPTABLE_OVERPAY) {
                bestCost = n;
                bestResult = m;
                break;
            }
            if (score < bestScore - 1.0e-9) {
                bestScore = score;
                bestCost = n;
                bestResult = m;
            }
        }
        if (bestCost < 0) {
            // Input too cheap to ever afford one payout item within the caps.
            return config.allowUndervaluedTrades()
                ? Optional.of(new Quote(maxCost, 1))
                : Optional.empty();
        }
        // Never pay out an item's own crafting material at or above what the
        // recipe consumes — otherwise craft-then-sell prints materials (an iron
        // block must sell for fewer than the 9 iron ingots it's crafted from).
        // Only a plain, unmodified stack can feed that loop: enchanted or
        // custom-stat items have value beyond their materials (already priced in
        // by ItemValuation) and can't be freely re-crafted, so they're exempt.
        OptionalInt materialCap = isCraftEquivalent(input)
            ? RecipeValues.maxMaterialPayout(input.getItem(), payout, bestCost)
            : OptionalInt.empty();
        if (materialCap.isPresent()) {
            bestResult = Math.min(bestResult, materialCap.getAsInt());
            if (bestResult < 1) return Optional.empty(); // capped below one unit — not a viable trade
        }
        return Optional.of(new Quote(bestCost, bestResult));
    }

    /**
     * True when the stack is worth no more than its craftable base — no
     * enchantments, custom name, attribute modifiers, or any other non-default
     * component. Such a stack is interchangeable with what the recipe produces,
     * so the material-printer cap applies; anything modified is worth more than
     * its raw materials and is exempt.
     *
     * <p>Durability is deliberately NOT disqualifying: damage only ever LOWERS a
     * stack's worth (already priced in by {@link ItemValuation}), so a used tool
     * must stay capped. Treating the DAMAGE patch as "modified" made a
     * once-swung pickaxe skip the cap and pay out more than a pristine one —
     * craft, swing once, sell for more materials than it took to make.</p>
     */
    private static boolean isCraftEquivalent(ItemStack stack) {
        return stack.getComponentsPatch().forget(DURABILITY_COMPONENTS::contains).isEmpty();
    }
}
