package games.brennan.tradeeverything.forge;

import games.brennan.tradeeverything.ConfigDir;
import games.brennan.tradeeverything.TradeEverything;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge entrypoint. Sets the config directory and runs common init.
 * All gameplay logic is mixin-driven from the common module.
 */
@Mod("tradeeverything")
public final class TradeEverythingForge {

    public TradeEverythingForge(IEventBus modBus) {
        ConfigDir.set(FMLPaths.CONFIGDIR.get());
        TradeEverything.init();
    }
}
