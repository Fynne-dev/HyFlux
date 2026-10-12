package dev.hyflux.plugin.command;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import dev.hyflux.api.HyFlux;
import dev.hyflux.api.network.EnergyWorld;
import dev.hyflux.plugin.HyFluxService;

/** {@code /hyflux info}: version, registrations and per-world totals. */
final class InfoCommand extends CommandBase {

    private final HyFluxService service;

    InfoCommand(HyFluxService service) {
        super("info", "Shows HyFlux version, registered content and loaded energy worlds");
        this.service = service;
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        Msg.header(ctx, "HyFlux " + service.getVersion() + " (API v" + HyFlux.API_VERSION + ")");
        Msg.field(ctx, "Node types", String.valueOf(service.nodeTypes().all().size()));
        Msg.field(ctx, "Bound blocks", String.valueOf(service.blocks().all().size()));
        Msg.field(ctx, "Energy items", String.valueOf(service.items().all().size()));
        if (service.getWorlds().isEmpty()) {
            Msg.note(ctx, "No energy worlds loaded yet.");
        }
        for (EnergyWorld world : service.getWorlds()) {
            String line = world.getNodeCount() + " nodes in " + world.getNetworks().size() + " networks";
            int orphans = service.orphanCount(world.getName());
            if (orphans > 0) {
                line += ", " + orphans + " inactive (missing node types)";
            }
            Msg.field(ctx, "World " + world.getName(), line);
        }
    }
}
