package games.brennan.tradeeverything.mixin.client;

import games.brennan.tradeeverything.TradeEverything;
import games.brennan.tradeeverything.trade.SyntheticOfferFactory;
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
 * Up to three small red bars between the Trade Anything row's cost and its arrow
 * when the villager has tired of the item: one once the price has dropped at all,
 * two once it is down by more than a quarter, three below half. Each bar is three
 * pixels wide with its corners clipped, and they rise in height left to right.
 * Hooked on {@code renderButtonArrows}, which vanilla calls once per visible row
 * with that row's offer and position.
 *
 * <p>CLIENT ONLY — listed under {@code "client"} in the mixin config.</p>
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenDemandMixin {

    /** First bar's x offset from the screen's left — just right of the cost item (x 10–26). */
    @Unique private static final int FIRST_BAR_X = 30;
    @Unique private static final int BAR_SPACING = 5;
    @Unique private static final int BAR_WIDTH = 3;
    /** Each bar's top, as a y offset in the 16-pixel row: much shorter, slightly shorter, full. */
    @Unique private static final int[] BAR_TOPS = {8, 5, 3};
    @Unique private static final int BAR_BOTTOM = 14;
    /** Percent of full price below which the second and third bars show. */
    @Unique private static final int SECOND_BAR_BELOW = 75;
    @Unique private static final int THIRD_BAR_BELOW = 50;
    @Unique private static final int RED = 0xFFFF5555;

    @Inject(method = "renderButtonArrows", at = @At("TAIL"))
    private void tradeeverything$drawDemandBars(GuiGraphics graphics, MerchantOffer offer, int posX, int posY,
                                                CallbackInfo ci) {
        try {
            OptionalInt percent = SyntheticOfferFactory.demandPercent(offer);
            if (percent.isEmpty()) return;
            int bars = percent.getAsInt() < THIRD_BAR_BELOW ? 3 : percent.getAsInt() < SECOND_BAR_BELOW ? 2 : 1;
            for (int bar = 0; bar < bars; bar++) {
                int x = posX + FIRST_BAR_X + bar * BAR_SPACING;
                int top = posY + BAR_TOPS[bar];
                int bottom = posY + BAR_BOTTOM;
                // Middle column full height, outer columns one short at each end: rounded corners.
                graphics.fill(x + 1, top, x + BAR_WIDTH - 1, bottom, RED);
                graphics.fill(x, top + 1, x + 1, bottom - 1, RED);
                graphics.fill(x + BAR_WIDTH - 1, top + 1, x + BAR_WIDTH, bottom - 1, RED);
            }
        } catch (Throwable t) {
            TradeEverything.LOGGER.warn("[TradeEverything] demand bars failed", t);
        }
    }
}
