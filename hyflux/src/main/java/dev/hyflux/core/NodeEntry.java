package dev.hyflux.core;

import dev.hyflux.api.energy.EnergyUtil;
import dev.hyflux.api.energy.IEnergyStorage;
import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.network.EnergyNetwork;
import dev.hyflux.api.network.EnergyWorld;
import dev.hyflux.api.node.EnergyNode;
import dev.hyflux.api.node.EnergyRole;
import dev.hyflux.api.node.NodeContext;
import dev.hyflux.api.node.NodeType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** A node placed in a world. Doubles as the {@link NodeContext} handed to the node. */
final class NodeEntry implements NodeContext {

    final EnergyWorldImpl world;
    final BlockPos pos;
    final NodeType type;
    final String blockTypeId;
    final EnergyNode node;
    final EnergyRole role;

    /** For cables: the network the cable belongs to. Null for other nodes. */
    EnergyNetworkImpl grid;
    /** For other nodes: every separate cable network they touch (usually zero or one). */
    final List<EnergyNetworkImpl> links = new ArrayList<>(2);
    boolean attached;
    /** Whether the node took part in the current tick (its chunk is loaded). */
    boolean active = true;
    /** Energy the node received and gave away during the current tick, from all sources. */
    long received;
    long sent;
    /** Most the node may take in / give out this tick; -1 until first asked. */
    private long inCap = -1;
    private long outCap = -1;

    void resetTick() {
        received = 0;
        sent = 0;
        inCap = -1;
        outCap = -1;
    }

    /**
     * How much the storage can still accept this tick. The first answer of the tick sets the
     * budget, so a storage limited to N per call takes at most N per tick in total, however
     * many neighbours and networks feed it.
     */
    long wantIn(IEnergyStorage storage) {
        long now = Math.max(0, storage.receiveEnergy(Long.MAX_VALUE, true));
        if (inCap < 0) {
            inCap = EnergyUtil.saturatedAdd(now, received);
        }
        return Math.max(0, Math.min(now, inCap - received));
    }

    /** How much the storage can still give this tick; see {@link #wantIn}. */
    long offerOut(IEnergyStorage storage) {
        long now = Math.max(0, storage.extractEnergy(Long.MAX_VALUE, true));
        if (outCap < 0) {
            outCap = EnergyUtil.saturatedAdd(now, sent);
        }
        return Math.max(0, Math.min(now, outCap - sent));
    }

    NodeEntry(EnergyWorldImpl world, BlockPos pos, NodeType type, String blockTypeId, EnergyNode node) {
        this.world = world;
        this.pos = pos;
        this.type = type;
        this.blockTypeId = blockTypeId;
        this.node = node;
        this.role = node.getRole();
        if (role == null) {
            throw new IllegalArgumentException("Node " + node.getClass().getName() + " returned a null role");
        }
    }

    @Override
    public EnergyWorld world() {
        return world;
    }

    @Override
    public BlockPos pos() {
        return pos;
    }

    @Override
    public NodeType type() {
        return type;
    }

    @Override
    public String blockTypeId() {
        return blockTypeId;
    }

    boolean isCable() {
        return role == EnergyRole.CONDUIT;
    }

    @Override
    public EnergyNetwork network() {
        if (isCable()) {
            return grid;
        }
        return links.isEmpty() ? null : links.get(0);
    }

    @Override
    public Collection<? extends EnergyNetwork> networks() {
        if (isCable()) {
            return grid == null ? List.of() : List.of(grid);
        }
        return List.copyOf(links);
    }

    @Override
    public long currentTick() {
        return world.getTick();
    }

    @Override
    public void markDirty() {
        world.markDirty();
    }

    @Override
    public void setBlockState(String state) {
        if (attached && blockTypeId != null) {
            try {
                world.host().setBlockState(pos, state == null ? "default" : state);
            } catch (Throwable t) {
                world.host().reportError("Could not set block state " + state + " at " + pos, t);
            }
        }
    }

    @Override
    public String toString() {
        return type.id() + "@" + pos;
    }
}
