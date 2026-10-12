package dev.hyflux.api.node;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * A kind of node, identified by a namespaced id such as {@code "mymod:solar_panel"}.
 * The id is written to disk, so keep it stable between versions of your mod.
 *
 * <pre>{@code
 * public static final NodeType SOLAR = NodeType.of("mymod:solar_panel", SolarPanelNode::new);
 * }</pre>
 */
public final class NodeType {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private final String id;
    private final NodeFactory factory;

    private NodeType(String id, NodeFactory factory) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Node type id must look like namespace:path using [a-z0-9_.-], got: " + id);
        }
        this.id = id;
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public static NodeType of(String id, NodeFactory factory) {
        return new NodeType(id, factory);
    }

    public static NodeType of(String id, Supplier<? extends EnergyNode> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return new NodeType(id, ctx -> supplier.get());
    }

    public String id() {
        return id;
    }

    public String namespace() {
        return id.substring(0, id.indexOf(':'));
    }

    public NodeFactory factory() {
        return factory;
    }

    /** Creates a node, failing loudly if the factory returns null. */
    public EnergyNode create(NodeSpawnContext context) {
        EnergyNode node = factory.create(context);
        if (node == null) {
            throw new IllegalStateException("Factory for node type " + id + " returned null");
        }
        return node;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NodeType other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
