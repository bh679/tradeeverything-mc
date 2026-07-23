package games.brennan.tradeeverything.mixin;

import games.brennan.tradeeverything.trade.SyntheticOffer;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Attaches the synthetic-offer flag to every MerchantOffer (default false). */
@Mixin(MerchantOffer.class)
public class MerchantOfferMixin implements SyntheticOffer {

    @Unique
    private boolean tradeeverything$synthetic;

    @Override
    public boolean tradeeverything$isSynthetic() {
        return tradeeverything$synthetic;
    }

    @Override
    public void tradeeverything$setSynthetic(boolean synthetic) {
        tradeeverything$synthetic = synthetic;
    }

    /** The flag survives copy() — server-side consumers of copied lists must not see the synthetic offer as real. */
    @org.spongepowered.asm.mixin.injection.Inject(method = "copy", at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void tradeeverything$copyFlag(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<MerchantOffer> cir) {
        if (tradeeverything$synthetic && cir.getReturnValue() instanceof SyntheticOffer copy) {
            copy.tradeeverything$setSynthetic(true);
        }
    }
}
