package games.brennan.tradeeverything.trade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;

/**
 * Re-sends the merchant offer list to the trading player mid-session,
 * mirroring exactly what {@code Merchant.openTradingScreen} sends. The
 * client applies it in place ({@code ClientPacketListener.handleMerchantOffers}
 * → {@code MerchantMenu.setOffers}) with selection and scroll preserved.
 */
public final class OfferResync {

    private OfferResync() {}

    public static void send(AbstractVillager villager) {
        if (!(villager.getTradingPlayer() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof MerchantMenu menu)) return;
        int level = villager instanceof Villager v ? v.getVillagerData().getLevel() : 1;
        player.sendMerchantOffers(
            menu.containerId,
            villager.getOffers(),
            level,
            villager.getVillagerXp(),
            villager.showProgressBar(),
            villager.canRestock()
        );
    }
}
