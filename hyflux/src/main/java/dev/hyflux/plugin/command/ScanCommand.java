package dev.hyflux.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyflux.api.math.BlockPos;
import dev.hyflux.plugin.HyFluxService;
import org.joml.Vector3d;

/**
 * {@code /hyflux scan [radius]}: re-checks the blocks around you against the block bindings.
 * Useful after building with tools or commands that bypass block place events.
 */
final class ScanCommand extends AbstractPlayerCommand {

    private final HyFluxService service;
    private final DefaultArg<Integer> radius;

    ScanCommand(HyFluxService service) {
        super("scan", "Creates missing energy nodes and removes stale ones around you");
        this.service = service;
        this.radius = withDefaultArg("radius", "Blocks to scan in each direction", ArgTypes.INTEGER, 8, "8");
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            Msg.error(ctx, "Could not find your position.");
            return;
        }
        Vector3d p = transform.getPosition();
        BlockPos center = new BlockPos((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
        int requested = ctx.get(radius);
        int r = Math.max(0, Math.min(requested, service.config().maxScanRadius));
        HyFluxService.ScanResult result = service.scan(world, center, r);
        Msg.ok(ctx, "Scanned " + result.checked() + " blocks within " + r + ": " + result.added() + " nodes added, "
            + result.removed() + " removed. " + result.nodesAfter() + " nodes in this world.");
        if (requested > r) {
            Msg.note(ctx, "Radius capped at " + r + " (MaxScanRadius in config.json).");
        }
    }
}
