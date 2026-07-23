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

    private SyntheticOfferFactory() {}

    /**
     * Pre-insertion placeholder row: a chest named "Trade Anything" as the
     * cost — the chest reads as "put anything in here", and the CUSTOM_NAME
     * lives in the ItemCost predicate, so the client renders the localized
     * label without any client-side code AND the cost stays unmatchable (an
     * anvil rename produces a literal component, never this translatable one).
     * The result advertises one payout item — what the villager pays with.
     */
    public static MerchantOffer placeholder(Item payout) {
        DataComponentPredicate predicate = DataComponentPredicate.builder()
            .expect(DataComponents.CUSTOM_NAME, Component.translatable("tradeeverything.trade_anything"))
            .build();
        ItemCost cost = new ItemCost(Items.CHEST.builtInRegistryHolder(), 1, predicate);
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

    private static MerchantOffer mark(MerchantOffer offer) {
        ((SyntheticOffer) offer).tradeeverything$setSynthetic(true);
        return offer;
    }
}
