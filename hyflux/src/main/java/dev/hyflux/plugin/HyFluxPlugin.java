package dev.hyflux.plugin;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import dev.hyflux.api.HyFlux;
import dev.hyflux.plugin.command.HyFluxCommand;
import dev.hyflux.plugin.system.BlockBreakWatcher;
import dev.hyflux.plugin.system.BlockPlaceWatcher;
import dev.hyflux.plugin.system.EnergyTickSystem;

/**
 * HyFlux plugin bootstrap. The API is installed in the constructor so that mods depending
 * on HyFlux can register their content as early as their own {@code setup()}.
 */
public final class HyFluxPlugin extends JavaPlugin {

    private final HyFluxService service;

    public HyFluxPlugin(JavaPluginInit init) {
        super(init);
        this.service = new HyFluxService(this);
        HyFlux.install(service);
    }

    @Override
    protected void setup() {
        service.loadConfig(getDataDirectory().resolve("config.json"));

        getEntityStoreRegistry().registerSystem(new EnergyTickSystem(service));
        getEntityStoreRegistry().registerSystem(new BlockPlaceWatcher(service));
        getEntityStoreRegistry().registerSystem(new BlockBreakWatcher(service));

        getEventRegistry().registerGlobal(RemoveWorldEvent.class, event -> service.closeWorld(event.getWorld()));

        getCommandRegistry().registerCommand(new HyFluxCommand(service));
    }

    @Override
    protected void start() {
        getLogger().at(java.util.logging.Level.INFO).log("HyFlux " + service.getVersion() + " ready (API v" + HyFlux.API_VERSION + ")");
    }

    @Override
    protected void shutdown() {
        service.shutdown();
        HyFlux.uninstall(service);
    }

    public HyFluxService service() {
        return service;
    }
}
