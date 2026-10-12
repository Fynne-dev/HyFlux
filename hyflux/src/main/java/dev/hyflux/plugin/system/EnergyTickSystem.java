package dev.hyflux.plugin.system;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyflux.plugin.HyFluxService;

/** Runs once per world tick on the world thread and advances that world's energy simulation. */
public final class EnergyTickSystem extends TickingSystem<EntityStore> {

    private final HyFluxService service;

    public EnergyTickSystem(HyFluxService service) {
        this.service = service;
    }

    @Override
    public void tick(float deltaSeconds, int systemIndex, Store<EntityStore> store) {
        service.tickWorld(store.getExternalData().getWorld());
    }
}
