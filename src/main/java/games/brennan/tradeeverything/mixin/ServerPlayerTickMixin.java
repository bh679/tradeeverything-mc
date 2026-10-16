package games.brennan.tradeeverything.mixin;

import games.brennan.tradeeverything.trade.PlaceholderIconCycle;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tick source for the "Trade Anything" icon cycle.
 *
 * <p>{@code AbstractVillager} has no tick method of its own to hook, and the
 * cycle only matters while a merchant screen is open — so it rides the trading
 * player's tick instead, which also bounds the work to players actually
 * looking at a villager.</p>
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTickMixin {

    @Inject(method = "doTick", at = @At("TAIL"))
    private void tradeeverything$cyclePlaceholderIcon(CallbackInfo ci) {
        PlaceholderIconCycle.tick((ServerPlayer) (Object) this);
    }
}
