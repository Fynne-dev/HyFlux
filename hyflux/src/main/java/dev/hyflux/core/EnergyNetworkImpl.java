package dev.hyflux.core;

import dev.hyflux.api.energy.EnergyUtil;
import dev.hyflux.api.energy.IEnergyStorage;
import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.network.EnergyNetwork;
import dev.hyflux.api.network.EnergyWorld;
import dev.hyflux.api.network.NetworkStats;
import dev.hyflux.api.node.EnergyRole;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A connected run of cables plus the blocks attached to it. Only cables make up the network;
 * generators, machines and batteries are endpoints that may be attached to several networks.
 * Membership is maintained by {@link EnergyWorldImpl}.
 */
final class EnergyNetworkImpl implements EnergyNetwork {

    private final long id;
    private final EnergyWorldImpl world;
    private final Map<BlockPos, NodeEntry> cables = new LinkedHashMap<>();
    private final Map<BlockPos, NodeEntry> endpoints = new LinkedHashMap<>();
    private final int[] roleCounts = new int[EnergyRole.values().length];
    private NetworkStats lastStats = NetworkStats.EMPTY;
    private boolean valid = true;

    EnergyNetworkImpl(long id, EnergyWorldImpl world) {
        this.id = id;
        this.world = world;
    }

    void addCable(NodeEntry cable) {
        if (cables.put(cable.pos, cable) == null) {
            roleCounts[cable.role.ordinal()]++;
        }
        cable.grid = this;
    }

    void removeCable(NodeEntry cable) {
        if (cables.remove(cable.pos) != null) {
            roleCounts[cable.role.ordinal()]--;
        }
    }

    void addEndpoint(NodeEntry endpoint) {
        if (endpoints.put(endpoint.pos, endpoint) == null) {
            roleCounts[endpoint.role.ordinal()]++;
        }
    }

    void removeEndpoint(NodeEntry endpoint) {
        if (endpoints.remove(endpoint.pos) != null) {
            roleCounts[endpoint.role.ordinal()]--;
        }
    }

    Collection<NodeEntry> cables() {
        return cables.values();
    }

    Collection<NodeEntry> endpoints() {
        return endpoints.values();
    }

    /** Cables and endpoints together, for balancing. */
    List<NodeEntry> entries() {
        List<NodeEntry> all = new ArrayList<>(cables.size() + endpoints.size());
        all.addAll(cables.values());
        all.addAll(endpoints.values());
        return all;
    }

    /** A network without cables does not exist. */
    boolean isEmpty() {
        return cables.isEmpty();
    }

    void invalidate() {
        valid = false;
    }

    NetworkStats tick(long tick) {
        lastStats = NetworkBalancer.balance(tick, entries());
        return lastStats;
    }

    void setLastStats(NetworkStats stats) {
        lastStats = stats;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public EnergyWorld getWorld() {
        return world;
    }

    @Override
    public int size() {
        return cables.size() + endpoints.size();
    }

    @Override
    public boolean contains(BlockPos pos) {
        return cables.containsKey(pos) || endpoints.containsKey(pos);
    }

    @Override
    public Set<BlockPos> getNodePositions() {
        Set<BlockPos> all = new LinkedHashSet<>(cables.keySet());
        all.addAll(endpoints.keySet());
        return Collections.unmodifiableSet(all);
    }

    @Override
    public int count(EnergyRole role) {
        return roleCounts[role.ordinal()];
    }

    @Override
    public long getStoredEnergy() {
        long total = 0;
        for (NodeEntry e : endpoints.values()) {
            if (e.role == EnergyRole.STORAGE) {
                IEnergyStorage s = e.node.getEnergyStorage();
                if (s != null) {
                    total = EnergyUtil.saturatedAdd(total, s.getEnergyStored());
                }
            }
        }
        return total;
    }

    @Override
    public long getStorageCapacity() {
        long total = 0;
        for (NodeEntry e : endpoints.values()) {
            if (e.role == EnergyRole.STORAGE) {
                IEnergyStorage s = e.node.getEnergyStorage();
                if (s != null) {
                    total = EnergyUtil.saturatedAdd(total, s.getMaxEnergyStored());
                }
            }
        }
        return total;
    }

    @Override
    public long getTransferLimit() {
        long limit = Long.MAX_VALUE;
        for (NodeEntry c : cables.values()) {
            limit = Math.min(limit, Math.max(0, c.node.getTransferRate()));
        }
        return limit;
    }

    @Override
    public NetworkStats getLastStats() {
        return lastStats;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public String toString() {
        return "EnergyNetwork[" + id + ", " + cables.size() + " cables, " + endpoints.size() + " attached]";
    }
}
