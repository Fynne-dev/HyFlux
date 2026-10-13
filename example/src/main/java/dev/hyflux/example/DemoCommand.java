package dev.hyflux.example;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyflux.api.HyFlux;
import dev.hyflux.api.energy.EnergyUtil;
import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.math.Direction;
import dev.hyflux.api.network.EnergyNetwork;
import dev.hyflux.api.network.EnergyWorld;
import dev.hyflux.api.network.NodeFlow;
import dev.hyflux.api.node.ConsumerNode;
import dev.hyflux.api.node.EnergyNode;
import org.joml.Vector3d;

import java.util.concurrent.CompletableFuture;

/**
 * {@code /hyfluxdemo here | at | touching | bridge | status}: builds and inspects small rigs laid
 * out towards +X from the given block ({@code x y z} for every subcommand except {@code here}).
 */
final class DemoCommand extends AbstractCommandCollection {

    static final String[] LAYOUT = {
        HyFluxExamplePlugin.GENERATOR,
        HyFluxExamplePlugin.CABLE,
        HyFluxExamplePlugin.CABLE,
        HyFluxExamplePlugin.BATTERY,
        HyFluxExamplePlugin.CABLE,
        HyFluxExamplePlugin.LAMP,
    };

    /**
     * Blocks touching with no cable. The generator lights the lamp beside it and charges the
     * battery, the battery lights the lamp beside it, and the outer lamp stays dark because
     * lamps never pass energy on.
     */
    static final String[] TOUCHING_LAYOUT = {
        HyFluxExamplePlugin.LAMP,
        HyFluxExamplePlugin.LAMP,
        HyFluxExamplePlugin.GENERATOR,
        HyFluxExamplePlugin.BATTERY,
        HyFluxExamplePlugin.LAMP,
    };

    /**
     * Two cable runs with a lamp between them. The first lamp lights from the generator's run;
     * the last lamp stays dark because energy never passes through a lamp into another run.
     */
    static final String[] BRIDGE_LAYOUT = {
        HyFluxExamplePlugin.GENERATOR,
        HyFluxExamplePlugin.CABLE,
        HyFluxExamplePlugin.LAMP,
        HyFluxExamplePlugin.CABLE,
        HyFluxExamplePlugin.LAMP,
    };

    DemoCommand() {
        super("hyfluxdemo", "Builds a HyFlux demo rig");
        addSubCommand(new Here());
        addSubCommand(new BuildAt("at", "Builds the cabled demo rig at a position", LAYOUT));
        addSubCommand(new BuildAt("touching", "Builds touching blocks without cables to show direct transfer", TOUCHING_LAYOUT));
        addSubCommand(new BuildAt("bridge", "Builds two cable runs joined only by a lamp", BRIDGE_LAYOUT));
        addSubCommand(new Status());
    }

    /** Loads the chunks the rig spans, then builds it on the world thread. */
    static void build(World world, BlockPos origin, String[] layout, CommandContext ctx) {
        BlockPos end = origin.offset(Direction.EAST, layout.length - 1);
        var chunks = world.getChunkStore();
        CompletableFuture.allOf(
                chunks.getChunkSectionReferenceAtBlockAsync(origin.x(), origin.y(), origin.z()),
                chunks.getChunkSectionReferenceAtBlockAsync(end.x(), end.y(), end.z()))
            .thenRunAsync(() -> {
                EnergyWorld energy = HyFlux.world(world);
                for (int i = 0; i < layout.length; i++) {
                    BlockPos p = origin.offset(Direction.EAST, i);
                    // placeBlock also creates the block's energy node right away.
                    HyFlux.placeBlock(world, p, layout[i]);
                }
                EnergyNetwork network = energy.getNetwork(origin);
                ctx.sendMessage(Message.raw("Built a demo rig from " + origin.toShortString() + " to " + end.toShortString()
                    + (network == null ? "." : ", network #" + network.getId() + " has " + network.size() + " nodes.")
                    + " Run /hyflux probe on a lamp.").color("#55FF55"));
            }, world)
            .exceptionally(error -> {
                ctx.sendMessage(Message.raw("Could not build the rig: " + error).color("#FF5555"));
                return null;
            });
    }

    static void status(World world, BlockPos origin, CommandContext ctx) {
        EnergyWorld energy = HyFlux.world(world);
        for (int i = 0; i < LAYOUT.length; i++) {
            BlockPos p = origin.offset(Direction.EAST, i);
            EnergyNode node = energy.getNode(p);
            String block = HyFlux.getBlockId(world, p);
            StringBuilder line = new StringBuilder(p.toShortString()).append(" ")
                .append(block == null ? "?" : block).append(": ");
            if (node == null) {
                line.append("no node");
            } else {
                line.append(node.getRole());
                if (node.getEnergyStorage().getMaxEnergyStored() > 0) {
                    line.append(" ").append(EnergyUtil.format(node.getEnergyStorage().getEnergyStored()))
                        .append("/").append(EnergyUtil.format(node.getEnergyStorage().getMaxEnergyStored()));
                }
                if (node instanceof FluxGeneratorNode generator) {
                    line.append(", lifetime ").append(EnergyUtil.format(generator.getTotalGenerated()));
                }
                if (node instanceof ConsumerNode consumer) {
                    line.append(consumer.isPowered() ? ", POWERED" : ", unpowered");
                }
                NodeFlow flow = energy.getFlow(p);
                if (flow.received() > 0 || flow.sent() > 0) {
                    line.append(", in ").append(EnergyUtil.formatRate(flow.received()))
                        .append(" out ").append(EnergyUtil.formatRate(flow.sent()));
                }
            }
            ctx.sendMessage(Message.raw(line.toString()));
        }
        EnergyNetwork network = energy.getNetwork(origin);
        if (network != null) {
            var s = network.getLastStats();
            ctx.sendMessage(Message.raw("network #" + network.getId() + " tick " + s.tick() + ": produced " + s.produced()
                + ", consumed " + s.consumed() + ", charged " + s.charged() + ", discharged " + s.discharged()
                + ", limit " + EnergyUtil.formatRate(s.limit()) + ", stored " + network.getStoredEnergy()));
        }
    }

    /** {@code /hyfluxdemo here}: builds the rig two blocks east of you. */
    private static final class Here extends AbstractPlayerCommand {
        Here() {
            super("here", "Builds the demo rig next to you");
        }

        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            Vector3d p = transform.getPosition();
            build(world, new BlockPos((int) Math.floor(p.x) + 2, (int) Math.floor(p.y), (int) Math.floor(p.z)), LAYOUT, ctx);
        }
    }

    /** Base for the subcommands that take an x y z position. */
    private abstract static class Positioned extends AbstractWorldCommand {
        private final RequiredArg<Integer> x;
        private final RequiredArg<Integer> y;
        private final RequiredArg<Integer> z;

        Positioned(String name, String description) {
            super(name, description);
            x = withRequiredArg("x", "Block x", ArgTypes.INTEGER);
            y = withRequiredArg("y", "Block y", ArgTypes.INTEGER);
            z = withRequiredArg("z", "Block z", ArgTypes.INTEGER);
        }

        BlockPos pos(CommandContext ctx) {
            return new BlockPos(ctx.get(x), ctx.get(y), ctx.get(z));
        }
    }

    /** {@code /hyfluxdemo <at|touching|bridge> <x> <y> <z>}: builds a layout at a position; works from the console. */
    private static final class BuildAt extends Positioned {
        private final String[] layout;

        BuildAt(String name, String description, String[] layout) {
            super(name, description);
            this.layout = layout;
        }

        @Override
        protected void execute(CommandContext ctx, World world, Store<EntityStore> store) {
            build(world, pos(ctx), layout, ctx);
        }
    }

    /** {@code /hyfluxdemo status <x> <y> <z>}: prints every rig block and its node. */
    private static final class Status extends Positioned {
        Status() {
            super("status", "Shows the state of a demo rig");
        }

        @Override
        protected void execute(CommandContext ctx, World world, Store<EntityStore> store) {
            status(world, pos(ctx), ctx);
        }
    }
}
