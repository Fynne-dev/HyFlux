package dev.hyflux.core;

import dev.hyflux.api.node.NodeType;
import dev.hyflux.api.registry.BlockEnergyRegistry;
import dev.hyflux.api.registry.NodeTypeRegistry;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockEnergyRegistryImpl implements BlockEnergyRegistry {

    private final NodeTypeRegistry nodeTypes;
    private final Map<String, NodeType> bindings = new ConcurrentHashMap<>();

    public BlockEnergyRegistryImpl(NodeTypeRegistry nodeTypes) {
        this.nodeTypes = nodeTypes;
    }

    @Override
    public void bind(String blockTypeId, NodeType type) {
        Objects.requireNonNull(type, "type");
        if (blockTypeId == null || blockTypeId.isBlank()) {
            throw new IllegalArgumentException("blockTypeId is required");
        }
        String base = BlockEnergyRegistry.baseBlockId(blockTypeId);
        NodeType registered = nodeTypes.get(type.id());
        if (registered == null) {
            nodeTypes.register(type);
        } else if (registered != type) {
            throw new IllegalStateException("A different node type with id " + type.id() + " is already registered");
        }
        NodeType existing = bindings.putIfAbsent(base, type);
        if (existing != null && existing != type) {
            throw new IllegalStateException("Block " + base + " is already bound to node type " + existing.id());
        }
    }

    @Override
    public void unbind(String blockTypeId) {
        if (blockTypeId != null) {
            bindings.remove(BlockEnergyRegistry.baseBlockId(blockTypeId));
        }
    }

    @Override
    public NodeType getBinding(String blockTypeId) {
        if (blockTypeId == null || bindings.isEmpty()) {
            return null;
        }
        NodeType direct = bindings.get(blockTypeId);
        return direct != null ? direct : bindings.get(BlockEnergyRegistry.baseBlockId(blockTypeId));
    }

    @Override
    public Map<String, NodeType> all() {
        return Collections.unmodifiableMap(bindings);
    }
}
