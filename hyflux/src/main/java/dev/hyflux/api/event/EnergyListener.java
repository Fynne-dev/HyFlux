package dev.hyflux.api.event;

import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.network.EnergyNetwork;
import dev.hyflux.api.network.EnergyWorld;
import dev.hyflux.api.network.NetworkStats;
import dev.hyflux.api.node.DetachReason;
import dev.hyflux.api.node.EnergyNode;

/**
 * Receives energy network events. Register with {@code HyFlux.addListener}. All methods
 * are optional and run on the world thread, so keep them fast.
 */
public interface EnergyListener {

    default void onNodeAdded(EnergyWorld world, BlockPos pos, EnergyNode node) {
    }

    default void onNodeRemoved(EnergyWorld world, BlockPos pos, EnergyNode node, DetachReason reason) {
    }

    /** A new network appeared (a new isolated node, or one half of a split). */
    default void onNetworkCreated(EnergyNetwork network) {
    }

    /** A network ceased to exist (merged into another, split apart, or its last node removed). */
    default void onNetworkRemoved(EnergyNetwork network) {
    }

    /** Called after each network balances. Called every tick for every network, so be cheap. */
    default void onNetworkTick(EnergyNetwork network, NetworkStats stats) {
    }
}
