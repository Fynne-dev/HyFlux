package dev.hyflux.api.network;

import dev.hyflux.api.energy.IEnergyStorage;
import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.node.EnergyNode;
import dev.hyflux.api.node.NodeType;

import java.util.Collection;
import java.util.Optional;

/**
 * All energy nodes and networks of one Hytale world. Get it with {@code HyFlux.world(world)}.
 *
 * <p>Nodes bound to block types (see {@code HyFlux.blocks()}) are created and removed
 * automatically when those blocks are placed and broken. Use {@link #addNode} only for
 * nodes you manage yourself.
 *
 * <p><b>Threading:</b> only call this from the thread of the world it belongs to (for
 * example inside {@code world.execute(...)}, a system, or a player command).
 */
public interface EnergyWorld {

    /** The Hytale world name. */
    String getName();

    /** The energy tick counter, increasing by one each world tick. */
    long getTick();

    // ---- nodes -------------------------------------------------------------------------

    /** The node at a position, or null. */
    EnergyNode getNode(BlockPos pos);

    /** The node at a position if it is of the given class, otherwise null. */
    default <T extends EnergyNode> T getNode(BlockPos pos, Class<T> type) {
        EnergyNode node = getNode(pos);
        return type.isInstance(node) ? type.cast(node) : null;
    }

    default Optional<EnergyNode> findNode(BlockPos pos) {
        return Optional.ofNullable(getNode(pos));
    }

    default boolean hasNode(BlockPos pos) {
        return getNode(pos) != null;
    }

    /** The node type of the node at a position, or null. */
    NodeType getNodeType(BlockPos pos);

    /** The block type id the node at a position is bound to, or null. */
    String getBoundBlockId(BlockPos pos);

    /**
     * Creates a node from {@code type} and adds it at {@code pos}, joining or merging
     * neighbouring networks.
     *
     * @throws IllegalStateException if the position already has a node
     */
    EnergyNode addNode(BlockPos pos, NodeType type);

    /**
     * Adds an existing node instance. {@code type} is used to recreate it after a restart,
     * so it must be registered with {@code HyFlux.nodeTypes()} for the node to be saved.
     *
     * @throws IllegalStateException if the position already has a node
     */
    void addNode(BlockPos pos, NodeType type, EnergyNode node);

    /** Removes the node at a position, splitting its network if needed. Returns the removed node or null. */
    EnergyNode removeNode(BlockPos pos);

    /** Re-evaluates the connections of the node at {@code pos} after its {@code canConnect} answers changed. */
    void refreshConnections(BlockPos pos);

    /** Read-only view of all node positions. */
    Collection<BlockPos> getNodePositions();

    int getNodeCount();

    // ---- networks ----------------------------------------------------------------------

    /**
     * The cable network of the node at a position, or null if there is no node or it touches no
     * cable. A block touching several separate cable runs is attached to all of them; this
     * returns the first (see {@link #getNetworksAt(BlockPos)}).
     */
    EnergyNetwork getNetwork(BlockPos pos);

    /** Every cable network the node at a position is connected to (empty if none). */
    Collection<? extends EnergyNetwork> getNetworksAt(BlockPos pos);

    /** Read-only view of all networks. */
    Collection<? extends EnergyNetwork> getNetworks();

    // ---- convenience -------------------------------------------------------------------

    /** What the node at a position received and sent during the last tick ({@link NodeFlow#NONE} if no node). */
    NodeFlow getFlow(BlockPos pos);

    /** The energy storage of the node at a position, or null if there is no node. */
    default IEnergyStorage getStorage(BlockPos pos) {
        EnergyNode node = getNode(pos);
        return node == null ? null : node.getEnergyStorage();
    }

    /**
     * Asks HyFlux to look at the block at {@code pos} now and add, replace or remove its node
     * to match. Call this after you change blocks from code (for example
     * {@code world.setBlock}); player placing and breaking is handled for you.
     * Returns the node now at the position, or null.
     */
    EnergyNode refreshBlock(BlockPos pos);

    /** Marks the world as changed so it is included in the next autosave. */
    void markDirty();
}
