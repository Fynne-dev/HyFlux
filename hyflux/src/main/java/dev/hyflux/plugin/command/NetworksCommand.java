package dev.hyflux.plugin.command;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyflux.api.energy.EnergyUtil;
import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.network.EnergyNetwork;
import dev.hyflux.api.network.EnergyWorld;
import dev.hyflux.api.network.NetworkStats;
import dev.hyflux.plugin.HyFluxService;

import java.util.Comparator;
import java.util.List;

/** {@code /hyflux networks}: lists the largest networks of a world. */
final class NetworksCommand extends AbstractWorldCommand {

    private static final int SHOWN = 10;

    private final HyFluxService service;

    NetworksCommand(HyFluxService service) {
        super("networks", "Lists the energy networks of a world");
        this.service = service;
    }

    @Override
    protected void execute(CommandContext ctx, World world, Store<EntityStore> store) {
        EnergyWorld energy = service.getWorld(world);
        List<? extends EnergyNetwork> networks = energy.getNetworks().stream()
            .sorted(Comparator.comparingInt(EnergyNetwork::size).reversed())
            .toList();
        Msg.header(ctx, "World " + world.getName() + ": " + energy.getNodeCount() + " nodes, " + networks.size() + " networks");
        for (EnergyNetwork network : networks.subList(0, Math.min(SHOWN, networks.size()))) {
            NetworkStats s = network.getLastStats();
            BlockPos anchor = network.getNodePositions().stream().min(Comparator.naturalOrder()).orElse(BlockPos.ORIGIN);
            Msg.line(ctx, "#" + network.getId() + " at " + anchor.toShortString() + ": " + network.size() + " nodes, "
                + EnergyUtil.formatRate(s.consumed()) + " to machines, "
                + EnergyUtil.formatNumber(network.getStoredEnergy()) + "/" + EnergyUtil.format(network.getStorageCapacity()) + " stored"
                + (s.limited() ? ", bottlenecked" : ""));
        }
        if (networks.size() > SHOWN) {
            Msg.note(ctx, "... and " + (networks.size() - SHOWN) + " more.");
        }
    }
}
