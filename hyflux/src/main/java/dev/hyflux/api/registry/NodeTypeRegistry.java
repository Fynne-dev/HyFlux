package dev.hyflux.api.registry;

import dev.hyflux.api.node.EnergyNode;
import dev.hyflux.api.node.NodeFactory;
import dev.hyflux.api.node.NodeType;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Known node types. A node type must be registered for its nodes to be restored after a
 * restart. Register during your plugin {@code setup()}.
 */
public interface NodeTypeRegistry {

    /**
     * Registers a node type.
     *
     * @throws IllegalStateException if a different type with the same id is already registered
     */
    NodeType register(NodeType type);

    default NodeType register(String id, NodeFactory factory) {
        return register(NodeType.of(id, factory));
    }

    default NodeType register(String id, Supplier<? extends EnergyNode> supplier) {
        return register(NodeType.of(id, supplier));
    }

    /** The registered type with this id, or null. */
    NodeType get(String id);

    default boolean contains(String id) {
        return get(id) != null;
    }

    /** Read-only view of all registered types. */
    Collection<NodeType> all();
}
