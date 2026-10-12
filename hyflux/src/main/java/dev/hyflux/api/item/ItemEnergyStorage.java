package dev.hyflux.api.item;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.hyflux.api.energy.IEnergyStorage;

/**
 * An {@link IEnergyStorage} over an item stack. Because stacks are immutable, changes
 * produce a new stack: read it with {@link #getStack()} and store it back in the slot.
 */
public final class ItemEnergyStorage implements IEnergyStorage {

    private final ItemEnergySpec spec;
    private ItemStack stack;
    private long energy;

    ItemEnergyStorage(ItemStack stack, ItemEnergySpec spec) {
        this.stack = stack;
        this.spec = spec;
        this.energy = ItemEnergy.getEnergy(stack);
    }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        long accepted = Math.max(0, Math.min(spec.capacity() - energy, Math.min(spec.maxReceive(), maxReceive)));
        if (!simulate && accepted > 0) {
            set(energy + accepted);
        }
        return accepted;
    }

    @Override
    public long extractEnergy(long maxExtract, boolean simulate) {
        long extracted = Math.max(0, Math.min(energy, Math.min(spec.maxExtract(), maxExtract)));
        if (!simulate && extracted > 0) {
            set(energy - extracted);
        }
        return extracted;
    }

    @Override
    public long getEnergyStored() {
        return energy;
    }

    @Override
    public long getMaxEnergyStored() {
        return spec.capacity();
    }

    @Override
    public boolean canReceive() {
        return spec.maxReceive() > 0;
    }

    @Override
    public boolean canExtract() {
        return spec.maxExtract() > 0;
    }

    /** Sets the energy directly, ignoring transfer limits. */
    public void setEnergy(long energy) {
        set(Math.max(0, Math.min(spec.capacity(), energy)));
    }

    /** The stack reflecting every change made through this view. */
    public ItemStack getStack() {
        return stack;
    }

    public ItemEnergySpec getSpec() {
        return spec;
    }

    private void set(long value) {
        energy = value;
        stack = ItemEnergy.withEnergy(stack, value);
    }
}
