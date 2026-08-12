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
     * Priced offer: exactly the inserted stack (item + full components, so
     * enchanted/damaged variants only match themselves) × n, for m × payout.
     */
    public static MerchantOffer priced(ItemStack input, int costCount, Item payout, int resultCount) {
        ItemCost cost = new ItemCost(input.getItemHolder(), costCount, DataComponentPredicate.allOf(input.getComponents()));
        return mark(new MerchantOffer(cost, Optional.empty(), new ItemStack(payout, resultCount), 0, MAX_USES, 0, 0.0f));
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
        return isSynthetic(offer) && offer.getItemCostA().components().equals(PLACEHOLDER_PREDICATE);
    }

    private static MerchantOffer mark(MerchantOffer offer) {
        ((SyntheticOffer) offer).tradeeverything$setSynthetic(true);
        return offer;
    }
}
