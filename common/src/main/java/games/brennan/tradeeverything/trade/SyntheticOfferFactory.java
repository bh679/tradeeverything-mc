package games.brennan.tradeeverything.trade;

import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

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
     * The placeholder's identity: a CUSTOM_NAME predicate holding the localized
     * "Trade Anything" label. The client renders the label with zero client-side
     * code, the cost stays unmatchable (an anvil rename produces a literal
     * component, never this translatable one), and the predicate doubles as the
     * marker that tells a placeholder row from a priced quote.
     */
    private static final DataComponentPredicate PLACEHOLDER_PREDICATE = DataComponentPredicate.builder()
        .expect(DataComponents.CUSTOM_NAME, Component.translatable("tradeeverything.trade_anything"))
        .build();

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
        ItemCost cost = new ItemCost(icon.builtInRegistryHolder(), 1, PLACEHOLDER_PREDICATE);
        return mark(new MerchantOffer(cost, Optional.empty(), new ItemStack(payout, 1), 0, MAX_USES, 0, 0.0f));
    }

    /**
     * Priced offer: exactly the inserted stack (item + its component patch, so
     * enchanted/damaged/renamed variants only match themselves) × n, for m × payout.
     */
    public static MerchantOffer priced(ItemStack input, int costCount, Item payout, int resultCount) {
        ItemCost cost = new ItemCost(input.getItemHolder(), costCount, stackPredicate(input));
        return mark(new MerchantOffer(cost, Optional.empty(), new ItemStack(payout, resultCount), 0, MAX_USES, 0, 0.0f));
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
    static DataComponentPredicate stackPredicate(ItemStack stack) {
        return DataComponentPredicate.allOf(stack.getComponentsPatch().split().added());
    }

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
        ItemCost quoted = quote.getItemCostA();
        ItemCost cost = new ItemCost(quoted.item(), quoted.count(), PLACEHOLDER_PREDICATE);
        return mark(new MerchantOffer(cost, Optional.empty(), quote.getResult().copy(), 0, MAX_USES, 0, 0.0f));
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
        return offer.getItemCostA().components().equals(PLACEHOLDER_PREDICATE);
    }

    private static MerchantOffer mark(MerchantOffer offer) {
        ((SyntheticOffer) offer).tradeeverything$setSynthetic(true);
        return offer;
    }
}
