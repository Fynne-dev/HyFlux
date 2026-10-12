package dev.hyflux.api.item;

/**
 * Energy properties of an item type, such as a battery pack or a powered tool.
 *
 * @param itemId     Hytale item id
 * @param capacity   most energy one item holds
 * @param maxReceive most energy accepted per charge call
 * @param maxExtract most energy given per discharge call
 */
public record ItemEnergySpec(String itemId, long capacity, long maxReceive, long maxExtract) {

    public ItemEnergySpec {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId is required");
        }
        if (capacity <= 0 || maxReceive < 0 || maxExtract < 0) {
            throw new IllegalArgumentException("capacity must be > 0 and limits >= 0");
        }
    }
}
