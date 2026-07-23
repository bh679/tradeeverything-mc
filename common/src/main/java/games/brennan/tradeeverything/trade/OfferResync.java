package games.brennan.tradeeverything.trade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;

/**
 * Re-sends the merchant offer list to the trading player mid-session,
 * mirroring exactly what {@code Merchant.openTradingScreen} sends. The
 * client applies it in place ({@code ClientPacketListener.handleMerchantOffers}
 * → {@code MerchantMenu.setOffers}) with selection and scroll preserved.
 */
public final class OfferResync {

    private OfferResync() {}

    public static void send(int containerId, AbstractVillager villager) {
        if (!(villager.getTradingPlayer() instanceof ServerPlayer player)) return;
        int level = villager instanceof Villager v ? v.getVillagerData().getLevel() : 1;
        player.sendMerchantOffers(
            containerId,
            villager.getOffers(),
            level,
            villager.getVillagerXp(),
            villager.showProgressBar(),
            villager.canRestock()
        );
    }
}
