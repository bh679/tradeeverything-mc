package games.brennan.tradeeverything.fabric;

import games.brennan.tradeeverything.ConfigDir;
import games.brennan.tradeeverything.TradeEverything;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric entrypoint. Sets the config directory and runs common init.
 * All gameplay logic is mixin-driven from the common module.
 */
public final class TradeEverythingFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ConfigDir.set(FabricLoader.getInstance().getConfigDir());
        TradeEverything.init();
    }
}
