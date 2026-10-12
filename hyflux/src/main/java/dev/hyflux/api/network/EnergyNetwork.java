package dev.hyflux.api.network;

import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.node.EnergyRole;

import java.util.Set;

/**
 * A connected run of cables and the blocks attached to it. Only cables form the network:
 * generators, machines and batteries are attached to every separate run they touch, but never
 * join two runs together, so energy cannot pass through them from one run to the other.
 * Networks form, merge and split on their own as cables are added and removed; do not keep
 * references across ticks, look them up again with {@link EnergyWorld#getNetwork(BlockPos)}.
 */
public interface EnergyNetwork {

    /** Id unique within its world for the lifetime of the server. Not persisted. */
    long getId();

    EnergyWorld getWorld();

    /** Number of cables plus attached blocks. */
    int size();

    boolean contains(BlockPos pos);

    /** Read-only view of member positions. */
    Set<BlockPos> getNodePositions();

    int count(EnergyRole role);

    /** Energy held by batteries in this network. */
    long getStoredEnergy();

    /** Total battery capacity in this network. */
    long getStorageCapacity();

    /**
     * The most energy this network can move per tick: the lowest {@code getTransferRate()}
     * among its conduits, or {@link Long#MAX_VALUE} if it has none (a lone node).
     */
    long getTransferLimit();

    /** Results of the most recent tick. */
    NetworkStats getLastStats();

    /** False once the network has been merged into another, split, or emptied. */
    boolean isValid();
}
