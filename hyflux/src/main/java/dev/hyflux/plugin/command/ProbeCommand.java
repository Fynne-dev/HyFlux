package dev.hyflux.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import dev.hyflux.api.energy.EnergyUtil;
import dev.hyflux.api.energy.IEnergyStorage;
import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.network.EnergyNetwork;
import dev.hyflux.api.network.EnergyWorld;
import dev.hyflux.api.network.NetworkStats;
import dev.hyflux.api.network.NodeFlow;
import dev.hyflux.api.node.ConsumerNode;
import dev.hyflux.api.node.EnergyNode;
import dev.hyflux.api.node.EnergyRole;
import dev.hyflux.api.node.ProducerNode;
import dev.hyflux.plugin.HyFluxService;
import org.joml.Vector3i;

/** {@code /hyflux probe}: shows the node and network of the block you are looking at. */
final class ProbeCommand extends AbstractPlayerCommand {

    private static final double REACH = 10.0;

    private final HyFluxService service;

    ProbeCommand(HyFluxService service) {
        super("probe", "Shows the energy node and network of the block you are looking at");
        this.service = service;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
        Vector3i target = TargetUtil.getTargetBlock(ref, REACH, store);
        if (target == null) {
            Msg.error(ctx, "Look at a block within " + (int) REACH + " blocks.");
            return;
        }
        BlockPos pos = new BlockPos(target.x, target.y, target.z);
        EnergyWorld energy = service.getWorld(world);
        EnergyNode node = energy.refreshBlock(pos);
        if (node == null) {
            BlockType type = world.getBlockType(pos.x(), pos.y(), pos.z());
            Msg.note(ctx, "No energy node at " + pos + " (block " + (type == null ? "unknown" : type.getId()) + ").");
            return;
        }
        describe(ctx, energy, pos, node);
    }

    static void describe(CommandContext ctx, EnergyWorld energy, BlockPos pos, EnergyNode node) {
        Msg.header(ctx, energy.getNodeType(pos).id() + " at " + pos.toShortString());
        Msg.field(ctx, "Role", node.getRole().name());
        String block = energy.getBoundBlockId(pos);
        if (block != null) {
            Msg.field(ctx, "Block", block);
        }
        if (node.getRole() == EnergyRole.CONDUIT) {
            Msg.field(ctx, "Transfer rate", EnergyUtil.formatRate(node.getTransferRate()));
        } else {
            IEnergyStorage storage = node.getEnergyStorage();
            Msg.field(ctx, "Energy", EnergyUtil.bar(storage) + " " + EnergyUtil.format(storage.getEnergyStored())
                + " / " + EnergyUtil.format(storage.getMaxEnergyStored()));
        }
        if (node.getRole() != EnergyRole.CONDUIT) {
            NodeFlow flow = energy.getFlow(pos);
            Msg.field(ctx, "Last tick", "received " + EnergyUtil.formatRate(flow.received())
                + ", sent " + EnergyUtil.formatRate(flow.sent()));
        }
        if (node.getPriority() != 0) {
            Msg.field(ctx, "Priority", String.valueOf(node.getPriority()));
        }
        if (node instanceof ProducerNode producer) {
            Msg.field(ctx, "Generating", EnergyUtil.formatRate(producer.getLastGenerated())
                + " (max " + EnergyUtil.formatRate(producer.getGenerationPerTick()) + ")");
        }
        if (node instanceof ConsumerNode consumer) {
            Msg.field(ctx, "Status", (consumer.isPowered() ? "running" : "idle") + ", uses "
                + EnergyUtil.formatRate(consumer.getUsagePerTick()));
        }
        var networks = energy.getNetworksAt(pos);
        if (networks.isEmpty()) {
            Msg.note(ctx, "Not connected to any cable; only exchanges energy with the blocks it touches.");
        }
        for (EnergyNetwork network : networks) {
            describeNetwork(ctx, network);
        }
    }

    static void describeNetwork(CommandContext ctx, EnergyNetwork network) {
        NetworkStats s = network.getLastStats();
        Msg.field(ctx, "Cable network", "#" + network.getId() + ": " + network.count(EnergyRole.CONDUIT) + " cables linking "
            + network.count(EnergyRole.PRODUCER) + " generators, " + network.count(EnergyRole.CONSUMER) + " machines, "
            + network.count(EnergyRole.STORAGE) + " batteries");
        if (network.getStorageCapacity() > 0) {
            Msg.field(ctx, "  Stored", EnergyUtil.format(network.getStoredEnergy()) + " / " + EnergyUtil.format(network.getStorageCapacity()));
        }
        Msg.field(ctx, "  Last tick", "produced " + EnergyUtil.formatNumber(s.produced())
            + ", consumed " + EnergyUtil.formatNumber(s.consumed()) + " of " + EnergyUtil.formatNumber(s.demand())
            + ", charged " + EnergyUtil.formatNumber(s.charged()) + ", discharged " + EnergyUtil.formatNumber(s.discharged()));
        Msg.field(ctx, "  Limit", EnergyUtil.formatRate(s.limit()) + (s.limited() ? " (bottleneck!)" : ""));
    }
}
