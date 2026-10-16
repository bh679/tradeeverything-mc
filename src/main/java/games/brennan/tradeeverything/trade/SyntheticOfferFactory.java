package games.brennan.tradeeverything.trade;

import games.brennan.tradeeverything.compat.Costs;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.trading.ItemCost;
//?}

import java.util.Optional;

/**
 * Builds the synthetic "Trade Anything" offer.
 *
 * <p>Offers are constructed with {@code xp = 0} (no profession XP, no
 * level-ups), {@code priceMultiplier = 0} (demand/discounts never shift the
 * computed counts) and an effectively unlimited {@code maxUses} (plus a
 * {@code resetUses()} on every trade) so the slot never triggers restock.</p>
 */
public final class SyntheticOfferFactory {

    private static final int MAX_USES = 999_999;

    /**
     * Full price, in the percent {@link #demandPercent} carries. The percent rides in
     * the offer's vanilla {@code demand} field — synced to the client by
     * {@code MerchantOffer.STREAM_CODEC}, and inert here because {@code priceMultiplier}
     * is 0, so vanilla's demand price adjustment always comes to nothing.
     */
    public static final int FULL_PRICE_PERCENT = 100;

    /**
     * The placeholder's identity: a CUSTOM_NAME predicate holding the localized
     * "Trade Anything" label. The client renders the label with zero client-side
     * code, the cost stays unmatchable (an anvil rename produces a literal
     * component, never this translatable one), and the predicate doubles as the
     * marker that tells a placeholder row from a priced quote.
     */
    private static final Component PLACEHOLDER_NAME = Component.translatable("tradeeverything.trade_anything");

    //? if >=1.20.5 {
    private static final DataComponentPredicate PLACEHOLDER_PREDICATE = DataComponentPredicate.builder()
        .expect(DataComponents.CUSTOM_NAME, PLACEHOLDER_NAME)
        .build();
    //?}

    /** Icon shown when the cycle is disabled or has nothing to show. */
    public static final Item DEFAULT_ICON = Items.CHEST;

    private SyntheticOfferFactory() {}

    /** Pre-insertion placeholder row showing the default icon. */
    public static MerchantOffer placeholder(Item payout) {
        return placeholder(payout, DEFAULT_ICON);
    }

    /**
     * Pre-insertion placeholder row: {@code icon} named "Trade Anything" as the
     * cost. The icon is cosmetic only — {@link #PLACEHOLDER_PREDICATE} keeps the
     * cost unmatchable whatever item carries it, so it can be swapped every few
     * ticks ({@link PlaceholderIconCycle}) without affecting trade matching.
     * The result advertises one payout item — what the villager pays with.
     */
    public static MerchantOffer placeholder(Item payout, Item icon) {
        return offer(placeholderCost(icon, 1), new ItemStack(payout, 1), 0);
    }

    /**
     * Priced offer: exactly the inserted stack (item + its component patch, so
     * enchanted/damaged/renamed variants only match themselves) × n, for m × payout.
     */
    public static MerchantOffer priced(ItemStack input, int costCount, Item payout, int resultCount) {
        //? if >=1.20.5 {
        ItemCost cost = new ItemCost(input.getItemHolder(), costCount, stackPredicate(input));
        //?} else {
        /*// 1.20.1 costs match by NBT containment, so the stack's own tag already
        // limits the cost to exactly this variant (enchantments, damage, name...).
        ItemStack cost = input.copyWithCount(costCount);
        *///?}
        return offer(cost, new ItemStack(payout, resultCount), 0);
    }

    /**
     * The cost predicate for a quoted stack: every component the stack ADDS or
     * OVERRIDES on top of its item's defaults, and nothing from the defaults.
     *
     * <p>Default (prototype) components are shared by every stack of the item,
     * so expecting them never told two stacks apart — but they DO break the
     * client. {@code ItemCost}'s predicate is synced to the client and decoded
     * into fresh objects, and a vanilla {@code FoodProperties} with status
     * effects ({@code PossibleEffect} wraps its effect in a {@code Supplier}
     * lambda, so the record's generated {@code equals} is identity) never
     * compares equal to the client's own prototype. The client then failed
     * {@code MerchantContainer.updateSellItem} for rotten flesh, spider eyes,
     * golden apples, poisonous potatoes, pufferfish… and blanked the result
     * slot while the server, comparing the prototype against itself, still
     * completed the trade. Patch components (enchantments, damage, names,
     * potion contents…) all carry value-based equality and round-trip cleanly.</p>
     *
     * <p>A patch that <em>removes</em> a default component cannot be expressed
     * by a predicate (it can only expect presence), exactly as before: the old
     * full-map predicate simply lacked the entry too.</p>
     */
    //? if >=1.20.5 {
    static DataComponentPredicate stackPredicate(ItemStack stack) {
        return DataComponentPredicate.allOf(stack.getComponentsPatch().split().added());
    }
    //?}

    /**
     * Pre-insertion placeholder row carrying a real quote's numbers: the quoted
     * cost item and count, and the payout the quote settled on. Only the cost's
     * components are swapped back to {@link #PLACEHOLDER_PREDICATE}, so the row
     * keeps its "Trade Anything" label, stays unmatchable, and still reads as a
     * placeholder to {@link #isPlaceholder} / {@link #hasPlaceholderCost} — those
     * compare components only, never the count, which is what leaves the counts
     * free to show the real price.
     */
    public static MerchantOffer pricedPlaceholder(MerchantOffer quote) {
        return offer(placeholderCost(Costs.itemA(quote), Costs.countA(quote)), quote.getResult().copy(), quote.getDemand());
    }

    /**
     * {@code offer} tagged with the share of full price a tired villager now pays
     * ({@code VillagerDemands}), for the trade screen to show. Full price — and any
     * offer that isn't ours — comes back unchanged.
     */
    public static MerchantOffer withDemandPercent(MerchantOffer offer, double priceFraction) {
        if (!isSynthetic(offer)) return offer;
        int percent = Math.max(1, Math.min(FULL_PRICE_PERCENT, (int) Math.floor(priceFraction * FULL_PRICE_PERCENT)));
        if (percent >= FULL_PRICE_PERCENT) return offer;
        //? if >=1.20.5 {
        return mark(new MerchantOffer(offer.getItemCostA(), offer.getItemCostB(), offer.getResult().copy(),
            0, MAX_USES, 0, 0.0f, percent));
        //?} else {
        /*return mark(new MerchantOffer(offer.getBaseCostA().copy(), offer.getCostB().copy(), offer.getResult().copy(),
            0, MAX_USES, 0, 0.0f, percent));
        *///?}
    }

    /**
     * The share of full price the Trade Anything row pays, 1–99, or empty at full
     * price. Works client-side: the row is told apart from vanilla trades by its
     * synced shape (unlimited uses, no XP, no price multiplier), since the
     * {@link SyntheticOffer} flag never leaves the server.
     */
    public static java.util.OptionalInt demandPercent(MerchantOffer offer) {
        boolean ours = offer.getMaxUses() == MAX_USES && offer.getXp() == 0 && offer.getPriceMultiplier() == 0.0f;
        int percent = offer.getDemand();
        return ours && percent >= 1 && percent < FULL_PRICE_PERCENT
            ? java.util.OptionalInt.of(percent)
            : java.util.OptionalInt.empty();
    }

    public static boolean isSynthetic(MerchantOffer offer) {
        return offer instanceof SyntheticOffer synthetic && synthetic.tradeeverything$isSynthetic();
    }

    /**
     * True for the "nothing inserted yet" row — synthetic AND still carrying the
     * placeholder predicate (a priced quote copies the inserted stack's own
     * components instead), which is what makes it safe to swap the icon.
     */
    public static boolean isPlaceholder(MerchantOffer offer) {
        return isSynthetic(offer) && hasPlaceholderCost(offer);
    }

    /**
     * The components-only half of {@link #isPlaceholder}, for code that cannot
     * see the {@link SyntheticOffer} flag: that flag is set server-side and is
     * NOT part of the offer's stream codec, so a client-side reader only ever
     * sees {@code false}. The cost's {@link DataComponentPredicate} IS synced
     * (via {@code ItemCost.STREAM_CODEC}), which makes this predicate the
     * client's only reliable way to recognise the Trade Anything row.
     */
    public static boolean hasPlaceholderCost(MerchantOffer offer) {
        //? if >=1.20.5 {
        return offer.getItemCostA().components().equals(PLACEHOLDER_PREDICATE);
        //?} else {
        /*ItemStack cost = offer.getBaseCostA();
        return cost.hasCustomHoverName() && cost.getHoverName().equals(PLACEHOLDER_NAME);
        *///?}
    }

    /**
     * {@code count} × {@code icon} carrying the "Trade Anything" name: a
     * {@link #PLACEHOLDER_PREDICATE} on 1.20.5+, the stack's own hover name on
     * 1.20.1 (whose costs match by NBT containment, so no plain or anvil-renamed
     * stack ever satisfies the translatable name).
     */
    //? if >=1.20.5 {
    private static ItemCost placeholderCost(Item icon, int count) {
        return new ItemCost(icon.builtInRegistryHolder(), count, PLACEHOLDER_PREDICATE);
    }

    private static MerchantOffer offer(ItemCost cost, ItemStack result, int demand) {
        return mark(new MerchantOffer(cost, Optional.empty(), result, 0, MAX_USES, 0, 0.0f, demand));
    }
    //?} else {
    /*private static ItemStack placeholderCost(Item icon, int count) {
        ItemStack cost = new ItemStack(icon, count);
        cost.setHoverName(PLACEHOLDER_NAME);
        return cost;
    }

    private static MerchantOffer offer(ItemStack cost, ItemStack result, int demand) {
        return mark(new MerchantOffer(cost, ItemStack.EMPTY, result, 0, MAX_USES, 0, 0.0f, demand));
    }
    *///?}

    private static MerchantOffer mark(MerchantOffer offer) {
        ((SyntheticOffer) offer).tradeeverything$setSynthetic(true);
        return offer;
    }
}
