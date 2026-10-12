package dev.hyflux.api.energy;

import dev.hyflux.api.node.NodeData;

/**
 * The standard {@link IEnergyStorage}: a buffer with a capacity and per-call
 * receive and extract limits. Use it as the internal buffer of your machines.
 *
 * <pre>{@code
 * EnergyStorage buffer = new EnergyStorage(50_000, 500, 0); // accepts 500 HF/tick, cannot be drained
 * }</pre>
 */
public class EnergyStorage implements IEnergyStorage {

    private static final String KEY_ENERGY = "Energy";

    protected long energy;
    protected long capacity;
    protected long maxReceive;
    protected long maxExtract;

    public EnergyStorage(long capacity) {
        this(capacity, capacity, capacity, 0);
    }

    public EnergyStorage(long capacity, long maxTransfer) {
        this(capacity, maxTransfer, maxTransfer, 0);
    }

    public EnergyStorage(long capacity, long maxReceive, long maxExtract) {
        this(capacity, maxReceive, maxExtract, 0);
    }

    public EnergyStorage(long capacity, long maxReceive, long maxExtract, long energy) {
        if (capacity < 0 || maxReceive < 0 || maxExtract < 0) {
            throw new IllegalArgumentException("capacity and transfer limits must be >= 0");
        }
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
        this.energy = clamp(energy, 0, capacity);
    }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        if (!canReceive() || maxReceive <= 0) {
            return 0;
        }
        long accepted = Math.min(capacity - energy, Math.min(this.maxReceive, maxReceive));
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            energy += accepted;
            onEnergyChanged();
        }
        return accepted;
    }

    @Override
    public long extractEnergy(long maxExtract, boolean simulate) {
        if (!canExtract() || maxExtract <= 0) {
            return 0;
        }
        long extracted = Math.min(energy, Math.min(this.maxExtract, maxExtract));
        if (extracted <= 0) {
            return 0;
        }
        if (!simulate) {
            energy -= extracted;
            onEnergyChanged();
        }
        return extracted;
    }

    @Override
    public long getEnergyStored() {
        return energy;
    }

    @Override
    public long getMaxEnergyStored() {
        return capacity;
    }

    @Override
    public boolean canReceive() {
        return maxReceive > 0;
    }

    @Override
    public boolean canExtract() {
        return maxExtract > 0;
    }

    public long getMaxReceive() {
        return maxReceive;
    }

    public long getMaxExtract() {
        return maxExtract;
    }

    /**
     * Adds energy ignoring {@link #getMaxReceive()}. This is for the owner of the
     * buffer, such as a generator burning fuel. Returns the amount actually added.
     */
    public long generate(long amount) {
        if (amount <= 0) {
            return 0;
        }
        long added = Math.min(capacity - energy, amount);
        if (added > 0) {
            energy += added;
            onEnergyChanged();
        }
        return added;
    }

    /**
     * Removes energy ignoring {@link #getMaxExtract()}. This is for the owner of the
     * buffer, such as a machine doing work. Returns the amount actually removed.
     */
    public long consume(long amount) {
        if (amount <= 0) {
            return 0;
        }
        long removed = Math.min(energy, amount);
        if (removed > 0) {
            energy -= removed;
            onEnergyChanged();
        }
        return removed;
    }

    /** Removes exactly {@code amount} if that much is stored, otherwise nothing. */
    public boolean tryConsume(long amount) {
        if (amount < 0 || energy < amount) {
            return false;
        }
        if (amount > 0) {
            energy -= amount;
            onEnergyChanged();
        }
        return true;
    }

    public void setEnergy(long energy) {
        long clamped = clamp(energy, 0, capacity);
        if (clamped != this.energy) {
            this.energy = clamped;
            onEnergyChanged();
        }
    }

    public void setCapacity(long capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        this.capacity = capacity;
        if (energy > capacity) {
            energy = capacity;
            onEnergyChanged();
        }
    }

    public void setMaxReceive(long maxReceive) {
        this.maxReceive = Math.max(0, maxReceive);
    }

    public void setMaxExtract(long maxExtract) {
        this.maxExtract = Math.max(0, maxExtract);
    }

    public boolean isFull() {
        return energy >= capacity;
    }

    public boolean isEmpty() {
        return energy <= 0;
    }

    /** Called after every change to the stored amount. Override to mark your block dirty, update visuals, etc. */
    protected void onEnergyChanged() {
    }

    /** Writes the stored energy. Capacity and limits are not saved; they come from your constructor. */
    public void save(NodeData data) {
        data.putLong(KEY_ENERGY, energy);
    }

    public void load(NodeData data) {
        this.energy = clamp(data.getLong(KEY_ENERGY, 0L), 0, capacity);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public String toString() {
        return "EnergyStorage[" + energy + "/" + capacity + " HF, in=" + maxReceive + ", out=" + maxExtract + "]";
    }
}
