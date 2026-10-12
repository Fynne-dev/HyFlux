package dev.hyflux.core;

import dev.hyflux.api.node.NodeType;
import dev.hyflux.api.registry.NodeTypeRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class NodeTypeRegistryImpl implements NodeTypeRegistry {

    private final Map<String, NodeType> types = new ConcurrentHashMap<>();

    @Override
    public NodeType register(NodeType type) {
        Objects.requireNonNull(type, "type");
        NodeType existing = types.putIfAbsent(type.id(), type);
        if (existing != null && existing != type) {
            throw new IllegalStateException("Node type " + type.id() + " is already registered");
        }
        return type;
    }

    @Override
    public NodeType get(String id) {
        return id == null ? null : types.get(id);
    }

    @Override
    public Collection<NodeType> all() {
        return Collections.unmodifiableCollection(types.values());
    }
}
