package dev.hyflux.plugin.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyflux.api.math.BlockPos;
import dev.hyflux.plugin.HyFluxService;
import org.joml.Vector3i;

/**
 * Creates nodes for bound blocks. The event fires before the block is set (and other mods
 * may still cancel it), so the position is re-checked once the placement has happened.
 */
public final class BlockPlaceWatcher extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private final HyFluxService service;

    public BlockPlaceWatcher(HyFluxService service) {
        super(PlaceBlockEvent.class);
        this.service = service;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, PlaceBlockEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Vector3i target = event.getTargetBlock();
        if (target != null) {
            service.scheduleBlockCheck(store.getExternalData().getWorld(), new BlockPos(target.x, target.y, target.z));
        }
    }
}
