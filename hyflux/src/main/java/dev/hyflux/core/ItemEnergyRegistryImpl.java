package dev.hyflux.core;

import dev.hyflux.api.item.ItemEnergySpec;
import dev.hyflux.api.registry.ItemEnergyRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemEnergyRegistryImpl implements ItemEnergyRegistry {

    private final Map<String, ItemEnergySpec> specs = new ConcurrentHashMap<>();

    @Override
    public ItemEnergySpec register(ItemEnergySpec spec) {
        Objects.requireNonNull(spec, "spec");
        specs.put(spec.itemId(), spec);
        return spec;
    }

    @Override
    public ItemEnergySpec get(String itemId) {
        return itemId == null ? null : specs.get(itemId);
    }

    @Override
    public Collection<ItemEnergySpec> all() {
        return Collections.unmodifiableCollection(specs.values());
    }
}
