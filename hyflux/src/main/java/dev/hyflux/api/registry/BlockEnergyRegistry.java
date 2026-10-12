package dev.hyflux.api.registry;

import dev.hyflux.api.node.EnergyNode;
import dev.hyflux.api.node.NodeFactory;
import dev.hyflux.api.node.NodeType;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Binds Hytale block types to node types. When a player places a bound block, HyFlux
 * creates its node and connects it; when the block is broken, the node is removed.
 * Register during your plugin {@code setup()}.
 *
 * <p>Interaction-state variants of a block (ids such as
 * {@code *My_Lamp_State_Definitions_On}) share the binding of their base block, so a
 * machine can swap its visual state without losing its node.
 *
 * <pre>{@code
 * HyFlux.blocks().bind("MyMod_Copper_Cable", () -> new CableNode(256));
 * HyFlux.blocks().bind("MyMod_Furnace", MyNodeTypes.ELECTRIC_FURNACE);
 * }</pre>
 */
public interface BlockEnergyRegistry {

    /**
     * Binds a block type to a node type. The node type is registered with
     * {@link NodeTypeRegistry} if it is not already.
     *
     * @throws IllegalStateException if the block is already bound to a different node type
     */
    void bind(String blockTypeId, NodeType type);

    /** Binds a block type to a factory, using the generated node type id {@code block:<block id in lower case>}. */
    default NodeType bind(String blockTypeId, NodeFactory factory) {
        NodeType type = NodeType.of(defaultTypeId(blockTypeId), factory);
        bind(blockTypeId, type);
        return type;
    }

    default NodeType bind(String blockTypeId, Supplier<? extends EnergyNode> supplier) {
        NodeType type = NodeType.of(defaultTypeId(blockTypeId), supplier);
        bind(blockTypeId, type);
        return type;
    }

    /** Removes a binding. Existing nodes stay until their block is next checked. */
    void unbind(String blockTypeId);

    /** The node type bound to a block type id (variants resolve to their base block), or null. */
    NodeType getBinding(String blockTypeId);

    default boolean isBound(String blockTypeId) {
        return getBinding(blockTypeId) != null;
    }

    /** Read-only view of all bindings by base block id. */
    Map<String, NodeType> all();

    /**
     * The base block id of a variant id: {@code *My_Lamp_State_Definitions_On} becomes
     * {@code My_Lamp}. Ids that are not variants are returned unchanged.
     */
    static String baseBlockId(String blockTypeId) {
        if (blockTypeId == null) {
            return null;
        }
        String id = blockTypeId.startsWith("*") ? blockTypeId.substring(1) : blockTypeId;
        int state = id.indexOf("_State_Definitions_");
        return state > 0 ? id.substring(0, state) : id;
    }

    /** The node type id generated for {@link #bind(String, NodeFactory)}. */
    static String defaultTypeId(String blockTypeId) {
        String cleaned = baseBlockId(blockTypeId).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        return "block:" + cleaned;
    }
}
