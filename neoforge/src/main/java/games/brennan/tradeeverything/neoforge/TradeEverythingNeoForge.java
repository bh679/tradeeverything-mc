package games.brennan.tradeeverything.neoforge;

import games.brennan.tradeeverything.ConfigDir;
import games.brennan.tradeeverything.TradeEverything;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

/**
 * NeoForge entrypoint. Sets the config directory and runs common init.
 * All gameplay logic is mixin-driven from the common module.
 */
@Mod(TradeEverythingNeoForge.MOD_ID)
public final class TradeEverythingNeoForge {

    public static final String MOD_ID = "tradeeverything";

    public TradeEverythingNeoForge(IEventBus modBus) {
        ConfigDir.set(FMLPaths.CONFIGDIR.get());
        TradeEverything.init();
    }
}
