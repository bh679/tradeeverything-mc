package games.brennan.tradeeverything.mixin.client;

import games.brennan.tradeeverything.TradeEverything;
import games.brennan.tradeeverything.trade.SyntheticOfferFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.OptionalInt;

/**
 * A small red "X%" under the Trade Anything row's arrow when the villager has
 * tired of the item and pays only that share of its full price. Hooked on
 * {@code renderButtonArrows}, which vanilla calls once per visible row with that
 * row's offer and position.
 *
 * <p>CLIENT ONLY — listed under {@code "client"} in the mixin config.</p>
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenDemandMixin {

    /** Vanilla's arrow sprite: x offset from the screen's left, its width, and its y offset in the row. */
    @Unique private static final int ARROW_X = 5 + 35 + 20;
    @Unique private static final int ARROW_WIDTH = 10;
    @Unique private static final int ARROW_BOTTOM = 3 + 9;
    @Unique private static final float SCALE = 0.5f;
    @Unique private static final int RED = 0xFFFF5555;

    @Inject(method = "renderButtonArrows", at = @At("TAIL"))
    private void tradeeverything$drawDemandPercent(GuiGraphics graphics, MerchantOffer offer, int posX, int posY,
                                                   CallbackInfo ci) {
        try {
            OptionalInt percent = SyntheticOfferFactory.demandPercent(offer);
            if (percent.isEmpty()) return;
            Font font = Minecraft.getInstance().font;
            String label = percent.getAsInt() + "%";
            float centreX = posX + ARROW_X + ARROW_WIDTH / 2.0f;
            graphics.pose().pushPose();
            graphics.pose().translate(centreX - font.width(label) * SCALE / 2.0f, posY + ARROW_BOTTOM, 0.0f);
            graphics.pose().scale(SCALE, SCALE, 1.0f);
            graphics.drawString(font, label, 0, 0, RED, false);
            graphics.pose().popPose();
        } catch (Throwable t) {
            TradeEverything.LOGGER.warn("[TradeEverything] demand percent label failed", t);
        }
    }
}
