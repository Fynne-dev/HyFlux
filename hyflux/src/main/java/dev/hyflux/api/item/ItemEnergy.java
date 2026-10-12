package dev.hyflux.api.item;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.hyflux.api.HyFlux;

/**
 * Reads and changes the energy stored in item stacks. Energy is kept in the stack
 * metadata under {@link #METADATA_KEY}, so it survives inventories, drops and restarts.
 *
 * <p>Hytale item stacks are immutable: every method that changes energy returns a new
 * stack, which you must put back into the inventory slot yourself. Energy is per stack,
 * so give energy items a {@code MaxStack} of 1.
 *
 * <pre>{@code
 * ItemEnergyStorage cell = ItemEnergy.storage(stack);
 * long moved = EnergyUtil.transfer(machineBuffer, cell, 100);
 * inventory.setItemStackForSlot(slot, cell.getStack());
 * }</pre>
 */
public final class ItemEnergy {

    /** Metadata key holding the stored energy as a 64-bit integer. */
    public static final String METADATA_KEY = "HyFlux_Energy";

    private ItemEnergy() {
    }

    /** The registered spec of the stack item, or null if it holds no energy. */
    public static ItemEnergySpec spec(ItemStack stack) {
        if (stack == null || stack.getItemId() == null) {
            return null;
        }
        return HyFlux.items().get(stack.getItemId());
    }

    public static boolean isEnergyItem(ItemStack stack) {
        return spec(stack) != null;
    }

    /** Energy stored in the stack (0 for items that hold no energy). */
    public static long getEnergy(ItemStack stack) {
        ItemEnergySpec spec = spec(stack);
        if (spec == null) {
            return 0;
        }
        return Math.max(0, Math.min(spec.capacity(), readRaw(stack)));
    }

    /** Capacity of the stack (0 for items that hold no energy). */
    public static long getCapacity(ItemStack stack) {
        ItemEnergySpec spec = spec(stack);
        return spec == null ? 0 : spec.capacity();
    }

    /** A copy of the stack holding {@code energy}, clamped to its capacity. Non-energy stacks are returned unchanged. */
    public static ItemStack withEnergy(ItemStack stack, long energy) {
        ItemEnergySpec spec = spec(stack);
        if (spec == null) {
            return stack;
        }
        long clamped = Math.max(0, Math.min(spec.capacity(), energy));
        return stack.withMetadata(METADATA_KEY, Codec.LONG, clamped);
    }

    /** A mutable {@link dev.hyflux.api.energy.IEnergyStorage} view over the stack. */
    public static ItemEnergyStorage storage(ItemStack stack) {
        ItemEnergySpec spec = spec(stack);
        if (spec == null) {
            throw new IllegalArgumentException("Item " + (stack == null ? "null" : stack.getItemId()) + " is not a registered energy item");
        }
        return new ItemEnergyStorage(stack, spec);
    }

    static long readRaw(ItemStack stack) {
        Long value = stack.getFromMetadataOrNull(METADATA_KEY, Codec.LONG);
        return value == null ? 0 : value;
    }
}
