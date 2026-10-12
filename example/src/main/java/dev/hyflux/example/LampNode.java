package dev.hyflux.example;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hyflux.api.HyFlux;
import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.node.ConsumerNode;
import dev.hyflux.api.node.NodeContext;
import dev.hyflux.api.node.NodeType;
import org.joml.Vector3i;

/**
 * A machine that uses 10 HF per tick and switches its block to the lit {@code On} state while
 * it has power. The {@code On} state is a variant of the same block, so HyFlux keeps the node.
 */
public final class LampNode extends ConsumerNode {

    public static final NodeType TYPE = NodeType.of("hyfluxexample:lamp", LampNode::new);

    private static final String STATE_ON = "On";
    private static final String STATE_OFF = "default";

    public LampNode() {
        super(200, 20, 10);
    }

    /** How often the block state is re-checked, in case something else changed the block. */
    private static final int SYNC_INTERVAL_TICKS = 20;

    @Override
    public void tick(NodeContext context) {
        super.tick(context);
        if (context.currentTick() % SYNC_INTERVAL_TICKS == 0) {
            setLit(context, isPowered());
        }
    }

    @Override
    protected void onPoweredChanged(NodeContext context, boolean powered) {
        setLit(context, powered);
    }

    private static void setLit(NodeContext context, boolean lit) {
        World world = HyFlux.hytaleWorld(context);
        if (world == null) {
            return;
        }
        BlockPos p = context.pos();
        BlockType current = world.getBlockType(p.x(), p.y(), p.z());
        if (current == null || current.getId().endsWith("_State_Definitions_" + STATE_ON) == lit) {
            return; // Unknown block, or already showing the right state.
        }
        world.setBlockInteractionState(new Vector3i(p.x(), p.y(), p.z()), current, lit ? STATE_ON : STATE_OFF);
    }
}
