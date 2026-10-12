package dev.hyflux.api.node;

/**
 * What a node does in a network. The role decides how the network balancer treats
 * the node's {@link dev.hyflux.api.energy.IEnergyStorage}.
 */
public enum EnergyRole {
    /** Generates energy. The network extracts from it; it is never charged. */
    PRODUCER,
    /** Uses energy. The network and touching generators or batteries insert into it; it never passes energy on. */
    CONSUMER,
    /**
     * Buffers energy (batteries, capacitors). Charged from surplus production and
     * drained when producers cannot meet consumer demand.
     */
    STORAGE,
    /**
     * Carries energy (cables). Holds nothing; limits how much a network can move per tick.
     * A network is a connected run of conduits; other nodes attach to the runs they touch.
     */
    CONDUIT;

    public boolean holdsEnergy() {
        return this != CONDUIT;
    }
}
