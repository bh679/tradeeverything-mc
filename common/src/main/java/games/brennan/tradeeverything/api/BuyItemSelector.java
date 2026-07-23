package games.brennan.tradeeverything.api;

import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

/**
 * Chooses which item a villager pays out from its "Trade Anything" slot.
 * Registered selectors are consulted in registration order; the first
 * non-empty result wins, ahead of the built-in default (first non-emerald
 * cost item across the villager's real offers, emerald fallback).
 */
@FunctionalInterface
public interface BuyItemSelector {

    Optional<Item> selectBuyItem(AbstractVillager villager, MerchantOffers offers);
}
