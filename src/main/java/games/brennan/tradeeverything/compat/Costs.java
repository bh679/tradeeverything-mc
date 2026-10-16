package games.brennan.tradeeverything.compat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
//? if <1.20.5 {
/*import net.minecraft.nbt.NbtUtils;
*///?}

import java.util.Optional;

/**
 * Version-neutral reads of a trade's cost slots. 1.20.5+ models a cost as an
 * {@code ItemCost} (item holder + count + component predicate); 1.20.1 models it
 * as a plain {@code ItemStack} whose NBT the offered stack must contain.
 * Counts are always the BASE (undiscounted) cost.
 */
public final class Costs {

    private Costs() {}

    public static Item itemA(MerchantOffer offer) {
        //? if <1.20.5 {
        /*return offer.getBaseCostA().getItem();
        *///?} else {
        return offer.getItemCostA().item().value();
        //?}
    }

    public static int countA(MerchantOffer offer) {
        //? if <1.20.5 {
        /*return offer.getBaseCostA().getCount();
        *///?} else {
        return offer.getItemCostA().count();
        //?}
    }

    /** The second cost's item, or empty when the offer has only one cost. */
    public static Optional<Item> itemB(MerchantOffer offer) {
        //? if <1.20.5 {
        /*ItemStack costB = offer.getCostB();
        return costB.isEmpty() ? Optional.empty() : Optional.of(costB.getItem());
        *///?} else {
        return offer.getItemCostB().map(cost -> cost.item().value());
        //?}
    }

    /** The second cost's count, or 0 when the offer has only one cost. */
    public static int countB(MerchantOffer offer) {
        //? if <1.20.5 {
        /*return offer.getCostB().getCount();
        *///?} else {
        return offer.getItemCostB().map(cost -> cost.count()).orElse(0);
        //?}
    }

    /** True when {@code stack} satisfies the first cost's item and data (count ignored). */
    public static boolean matchesA(MerchantOffer offer, ItemStack stack) {
        //? if <1.20.5 {
        /*// Mirrors vanilla's private MerchantOffer.isRequiredItem.
        ItemStack cost = offer.getBaseCostA();
        if (!ItemStack.isSameItem(stack, cost)) return false;
        return !cost.hasTag() || stack.hasTag() && NbtUtils.compareNbt(cost.getTag(), stack.getTag(), false);
        *///?} else {
        return offer.getItemCostA().test(stack);
        //?}
    }
}
