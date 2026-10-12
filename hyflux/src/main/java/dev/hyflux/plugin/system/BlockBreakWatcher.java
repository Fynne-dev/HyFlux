package dev.hyflux.plugin.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyflux.api.math.BlockPos;
import dev.hyflux.plugin.HyFluxService;
import org.joml.Vector3i;

/**
 * Removes nodes when their block is broken. Breaks that bypass this event (explosions,
 * commands, other mods) are caught by the periodic validation pass instead.
 */
public final class BlockBreakWatcher extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final HyFluxService service;

    public BlockBreakWatcher(HyFluxService service) {
        super(BreakBlockEvent.class);
        this.service = service;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, BreakBlockEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Vector3i target = event.getTargetBlock();
        if (target != null) {
            service.scheduleBlockCheck(store.getExternalData().getWorld(), new BlockPos(target.x, target.y, target.z));
        }
    }
}
