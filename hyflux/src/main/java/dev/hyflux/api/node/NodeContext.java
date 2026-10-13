package dev.hyflux.api.node;

import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.network.EnergyNetwork;
import dev.hyflux.api.network.EnergyWorld;

import java.util.Collection;

/**
 * Where a node lives. Passed to every {@link EnergyNode} callback and valid while the
 * node is attached to its world.
 */
public interface NodeContext {

    /** The energy world the node is attached to. */
    EnergyWorld world();

    /** The node position. */
    BlockPos pos();

    /** The node type the node was created from. */
    NodeType type();

    /** The Hytale block type id the node is bound to, or null for nodes added directly through the API. */
    String blockTypeId();

    /**
     * The cable network the node is connected to, or null if it touches no cable. A cable is
     * always in exactly one network. Any other node is attached to every separate cable network
     * it touches; this returns the first of them (see {@link #networks()}).
     */
    EnergyNetwork network();

    /** Every cable network the node is connected to (empty if it touches no cable). */
    Collection<? extends EnergyNetwork> networks();

    /** The energy tick counter of the world, increasing by one per world tick. */
    long currentTick();

    /** Asks HyFlux to include this node in the next save. Call it after changing state you write in {@code save}. */
    void markDirty();

    /**
     * Switches the node's block to one of its state variants, as defined under
     * {@code BlockType.State.Definitions} in the block JSON ({@code "default"} for the normal look).
     * Does nothing for nodes without a block or if the block already shows that state.
     */
    void setBlockState(String state);
}
