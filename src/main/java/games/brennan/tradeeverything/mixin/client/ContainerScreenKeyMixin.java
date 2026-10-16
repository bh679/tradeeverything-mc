package games.brennan.tradeeverything.mixin.client;

import games.brennan.tradeeverything.TradeEverything;
import games.brennan.tradeeverything.client.TradeAnythingRefill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Space-bar refill of the Trade Anything slot, plus the per-tick observation
 * that feeds it. Both hooks are gated on the screen actually being a
 * {@link MerchantScreen}; every other container screen falls straight through.
 *
 * <p>CLIENT ONLY — listed under {@code "client"} in the mixin config.</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenKeyMixin {

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void tradeeverything$trackTradeSlot(CallbackInfo ci) {
        try {
            if ((Object) this instanceof MerchantScreen screen) {
                TradeAnythingRefill.remember(screen.getMenu());
            }
        } catch (Throwable t) {
            TradeEverything.LOGGER.warn("[TradeEverything] trade-slot tracking failed", t);
        }
    }

    /**
     * Vanilla {@code AbstractContainerScreen.keyPressed} runs {@code super.keyPressed}
     * first, so a Tab-focused trade button gets space as "activate"
     * ({@code CommonInputs.selected}); with nothing focused, vanilla swallows
     * space and does nothing. Hence the HEAD injection plus the focus guard —
     * it claims only the keypress vanilla was going to discard.
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void tradeeverything$spaceRefill(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (keyCode != GLFW.GLFW_KEY_SPACE) return;
            if (!((Object) this instanceof MerchantScreen screen)) return;
            if (((Screen) (Object) this).getFocused() != null) return;

            TradeAnythingRefill.refill(Minecraft.getInstance(), screen.getMenu());
            cir.setReturnValue(true);
        } catch (Throwable t) {
            TradeEverything.LOGGER.warn("[TradeEverything] space-bar trade-slot refill failed", t);
        }
    }
}
