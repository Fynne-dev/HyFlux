package dev.hyflux.api.node;

import dev.hyflux.api.energy.IEnergyStorage;
import dev.hyflux.api.math.Direction;

/**
 * A participant in an energy network: a generator, a machine, a battery or a cable.
 *
 * <p>Most mods extend one of the ready-made base classes ({@link ProducerNode},
 * {@link ConsumerNode}, {@link BatteryNode}, {@link CableNode}) instead of
 * implementing this interface directly.
 *
 * <h2>Tick order</h2>
 * Every world tick HyFlux first calls {@link #tick(NodeContext)} on each node, then
 * balances every network: energy is pulled from producers (and batteries if needed)
 * and pushed into consumers (and batteries if there is surplus).
 *
 * <h2>Threading</h2>
 * All callbacks run on the thread of the Hytale world the node lives in.
 */
public interface EnergyNode {

    /** What the node does in its network. Must not change while attached. */
    EnergyRole getRole();

    /**
     * The buffer the network reads and writes. Producers expose their output buffer,
     * consumers their input buffer, batteries their cells. Conduits return
     * {@link IEnergyStorage#EMPTY}.
     */
    IEnergyStorage getEnergyStorage();

    /**
     * Whether the node accepts a connection on the given face. Two touching nodes interact
     * only if both accept:
     * <ul>
     *   <li>a {@link EnergyRole#CONDUIT} joins a touching cable into its network and attaches a
     *       touching block to it (a block attached to two separate cable runs does not join them);</li>
     *   <li>otherwise, a producer or battery pushes energy straight into a touching consumer or
     *       battery (one hop, Redstone Flux style). Consumers never pass energy on, and nothing
     *       travels through a node to reach the block behind it.</li>
     * </ul>
     * Call {@code EnergyWorld.refreshConnections(pos)} after this answer changes.
     */
    default boolean canConnect(Direction side) {
        return true;
    }

    /**
     * Order in which the network serves nodes of the same role. Higher goes first:
     * high-priority consumers are filled first, high-priority producers drained first,
     * high-priority batteries charged first and discharged first.
     * Nodes with equal priority share evenly.
     */
    default int getPriority() {
        return 0;
    }

    /**
     * For {@link EnergyRole#CONDUIT} nodes: the most energy per tick this conduit lets the
     * whole network move. A network is limited by its weakest conduit. Ignored for other roles.
     */
    default long getTransferRate() {
        return Long.MAX_VALUE;
    }

    /** Called once per world tick before networks balance. Generate or use energy here. */
    default void tick(NodeContext context) {
    }

    /** Called when the node joins a world (placed, added through the API, or restored on load). */
    default void onAttached(NodeContext context) {
    }

    /** Called when the node leaves a world. The context is still valid during this call. */
    default void onDetached(NodeContext context, DetachReason reason) {
    }

    /** Called after the node moved to a different network (a merge or split). */
    default void onNetworkChanged(NodeContext context) {
    }

    /** Writes state that must survive a restart. */
    default void save(NodeData data) {
    }

    /** Restores state written by {@link #save(NodeData)}. Called before {@link #onAttached}. */
    default void load(NodeData data) {
    }
}
