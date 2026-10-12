package dev.hyflux.api.node;

import dev.hyflux.api.energy.EnergyStorage;

/**
 * A generator. Each tick it adds {@link #getGeneration(NodeContext)} HF to its buffer,
 * and the network pulls up to {@code maxOutput} HF per tick out of it. It also pushes energy into
 * machines and batteries it touches. A generator with nothing to supply keeps generating until
 * its buffer is full and holds the energy until something needs it.
 *
 * <pre>{@code
 * public class SolarPanelNode extends ProducerNode {
 *     public SolarPanelNode() { super(1_000, 40, 20); }
 *
 *     protected long getGeneration(NodeContext ctx) {
 *         return isDaytime(ctx) ? super.getGeneration(ctx) : 0;
 *     }
 * }
 * }</pre>
 */
public class ProducerNode extends AbstractEnergyNode {

    private long generationPerTick;
    private long lastGenerated;

    /**
     * @param capacity          internal buffer size
     * @param maxOutput         most HF per tick the network can pull out
     * @param generationPerTick HF generated each tick by the default {@link #getGeneration}
     */
    public ProducerNode(long capacity, long maxOutput, long generationPerTick) {
        super(EnergyRole.PRODUCER, new EnergyStorage(capacity, 0, maxOutput));
        this.generationPerTick = Math.max(0, generationPerTick);
    }

    /** Generates energy. If you override this, call {@link #generate(long)} yourself. */
    @Override
    public void tick(NodeContext context) {
        lastGenerated = generate(getGeneration(context));
    }

    /** How much to generate this tick. Override for fuel, weather or time-of-day logic. */
    protected long getGeneration(NodeContext context) {
        return generationPerTick;
    }

    /** Adds energy to the buffer, up to its free space. Returns the amount added. */
    public long generate(long amount) {
        return storage.generate(amount);
    }

    public long getGenerationPerTick() {
        return generationPerTick;
    }

    public void setGenerationPerTick(long generationPerTick) {
        this.generationPerTick = Math.max(0, generationPerTick);
    }

    /** Energy generated during the most recent tick. */
    public long getLastGenerated() {
        return lastGenerated;
    }

    /** True if the last tick generated something. */
    public boolean isActive() {
        return lastGenerated > 0;
    }
}
