package dev.hyflux.example;

import dev.hyflux.api.node.NodeContext;
import dev.hyflux.api.node.NodeData;
import dev.hyflux.api.node.NodeType;
import dev.hyflux.api.node.ProducerNode;

/**
 * A generator that makes 40 HF every tick and lets the network pull up to 80 HF per tick.
 * It also keeps a lifetime counter to show how custom state is saved.
 */
public final class FluxGeneratorNode extends ProducerNode {

    /** Stable id: it is written to the world save, so never rename it. */
    public static final NodeType TYPE = NodeType.of("hyfluxexample:flux_generator", FluxGeneratorNode::new);

    private long totalGenerated;

    public FluxGeneratorNode() {
        super(4_000, 80, 40);
    }

    @Override
    public void tick(NodeContext context) {
        super.tick(context);
        if (getLastGenerated() > 0) {
            totalGenerated += getLastGenerated();
        }
    }

    public long getTotalGenerated() {
        return totalGenerated;
    }

    @Override
    protected void writeData(NodeData data) {
        data.putLong("TotalGenerated", totalGenerated);
    }

    @Override
    protected void readData(NodeData data) {
        totalGenerated = data.getLong("TotalGenerated", 0);
    }
}
