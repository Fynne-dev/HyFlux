package dev.hyflux.api.node;

import dev.hyflux.api.energy.EnergyStorage;

/**
 * A machine. The network fills its buffer by up to {@code maxInput} HF per tick, and each
 * tick it spends {@code usagePerTick} HF to run {@link #doWork(NodeContext)}. If there is
 * not enough energy the machine stalls for that tick.
 *
 * <pre>{@code
 * public class GrinderNode extends ConsumerNode {
 *     public GrinderNode() { super(4_000, 200, 50); }
 *
 *     protected boolean canWork(NodeContext ctx) { return hasInput(); }
 *     protected void doWork(NodeContext ctx) { progress++; }
 *     protected void onPoweredChanged(NodeContext ctx, boolean powered) { setLampState(powered); }
 * }
 * }</pre>
 */
public class ConsumerNode extends AbstractEnergyNode {

    private long usagePerTick;
    private boolean powered;
    private long ticksPowered;

    /**
     * @param capacity     internal buffer size
     * @param maxInput     most HF per tick the network can push in
     * @param usagePerTick HF spent per tick of work
     */
    public ConsumerNode(long capacity, long maxInput, long usagePerTick) {
        super(EnergyRole.CONSUMER, new EnergyStorage(capacity, maxInput, 0));
        this.usagePerTick = Math.max(0, usagePerTick);
    }

    @Override
    public void tick(NodeContext context) {
        boolean ran = canWork(context) && storage.tryConsume(usagePerTick);
        if (ran) {
            ticksPowered++;
            doWork(context);
        }
        if (ran != powered) {
            powered = ran;
            onPoweredChanged(context, ran);
        }
        showActive(context, ran);
    }

    /** Whether the machine has something to do this tick. Return false to idle without using energy. */
    protected boolean canWork(NodeContext context) {
        return true;
    }

    /** One tick of work, called after {@code usagePerTick} HF has been spent. */
    protected void doWork(NodeContext context) {
    }

    /** Called when the machine starts or stops running. Good place to switch block visuals. */
    protected void onPoweredChanged(NodeContext context, boolean powered) {
    }

    /** True if the machine ran during the last tick. */
    public boolean isPowered() {
        return powered;
    }

    /** Total ticks the machine has run since it was attached. */
    public long getTicksPowered() {
        return ticksPowered;
    }

    public long getUsagePerTick() {
        return usagePerTick;
    }

    public void setUsagePerTick(long usagePerTick) {
        this.usagePerTick = Math.max(0, usagePerTick);
    }
}
