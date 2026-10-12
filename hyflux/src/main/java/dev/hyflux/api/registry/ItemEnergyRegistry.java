package dev.hyflux.api.registry;

import dev.hyflux.api.item.ItemEnergySpec;

import java.util.Collection;

/**
 * Item types that hold energy. Register during your plugin {@code setup()} and use
 * {@link dev.hyflux.api.item.ItemEnergy} to read and change the energy of item stacks.
 */
public interface ItemEnergyRegistry {

    /** Registers an energy item. Re-registering the same id replaces the spec. */
    ItemEnergySpec register(ItemEnergySpec spec);

    default ItemEnergySpec register(String itemId, long capacity, long maxTransfer) {
        return register(new ItemEnergySpec(itemId, capacity, maxTransfer, maxTransfer));
    }

    default ItemEnergySpec register(String itemId, long capacity, long maxReceive, long maxExtract) {
        return register(new ItemEnergySpec(itemId, capacity, maxReceive, maxExtract));
    }

    /** The spec for an item id, or null if it holds no energy. */
    ItemEnergySpec get(String itemId);

    Collection<ItemEnergySpec> all();
}
