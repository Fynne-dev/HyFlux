package dev.hyflux.core;

import dev.hyflux.api.event.EnergyListener;
import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.math.Direction;
import dev.hyflux.api.network.EnergyNetwork;
import dev.hyflux.api.network.EnergyWorld;
import dev.hyflux.api.network.NetworkStats;
import dev.hyflux.api.network.NodeFlow;
import dev.hyflux.api.node.DetachReason;
import dev.hyflux.api.node.EnergyNode;
import dev.hyflux.api.node.EnergyRole;
import dev.hyflux.api.node.NodeData;
import dev.hyflux.api.node.NodeSpawnContext;
import dev.hyflux.api.node.NodeType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The energy simulation for one world: node storage, network topology (merge and split)
 * and the per-tick update. Pure Java; the Hytale specifics are supplied by a {@link Host}.
 */
public class EnergyWorldImpl implements EnergyWorld {

    /** Integration points supplied by the environment (the Hytale plugin, or tests). */
    public interface Host {

        /** Whether a node takes part in this tick. The plugin freezes nodes in unloaded chunks. */
        default boolean isActive(BlockPos pos, String blockTypeId) {
            return true;
        }

        /** Re-checks the block at a position against the block bindings. */
        default EnergyNode refreshBlock(EnergyWorldImpl world, BlockPos pos) {
            return world.getNode(pos);
        }

        /** Switches the block at a position to one of its state variants ("default" = normal look). */
        default void setBlockState(BlockPos pos, String state) {
        }

        /** Throws if called from the wrong thread. */
        default void checkThread() {
        }

        /** Reports an exception thrown by node code. */
        default void reportError(String message, Throwable error) {
            System.err.println("[HyFlux] " + message);
            if (error != null) {
                error.printStackTrace();
            }
        }

        /** Listeners notified of world events. */
        default Collection<EnergyListener> listeners() {
            return List.of();
        }

        /**
         * Whether generators and batteries push energy into the machines and batteries they
         * touch without a cable (see {@link DirectTransfer}). When false, energy only moves
         * through cables. Read once when the world is created.
         */
        default boolean allowDirectTransfer() {
            return true;
        }
    }

    private static final Host DEFAULT_HOST = new Host() {
    };

    private final String name;
    private final Host host;
    private final boolean directTransfer;
    private final Map<BlockPos, NodeEntry> nodes = new HashMap<>();
    private final Set<EnergyNetworkImpl> networks = new LinkedHashSet<>();
    private long tick;
    private long nextNetworkId = 1;
    private boolean dirty;

    public EnergyWorldImpl(String name) {
        this(name, DEFAULT_HOST);
    }

    public EnergyWorldImpl(String name, Host host) {
        this.name = Objects.requireNonNull(name, "name");
        this.host = host == null ? DEFAULT_HOST : host;
        this.directTransfer = this.host.allowDirectTransfer();
    }

    // ---- ticking -----------------------------------------------------------------------

    /**
     * Advances one tick: every active node ticks, then energy moves. Touching generators feed
     * touching machines first, then every network balances, then touching batteries cover what
     * machines still need, and finally touching generators charge touching batteries.
     */
    public void tick() {
        tick++;
        if (nodes.isEmpty()) {
            return;
        }
        NodeEntry[] snapshot = nodes.values().toArray(new NodeEntry[0]);
        for (NodeEntry entry : snapshot) {
            entry.active = isActive(entry);
            entry.resetTick();
        }
        for (NodeEntry entry : snapshot) {
            if (entry.attached && entry.active) {
                try {
                    entry.node.tick(entry);
                } catch (Throwable t) {
                    host.reportError("Node " + entry + " threw in tick()", t);
                }
            }
        }
        boolean moved = directPass(DirectTransfer.Pass.PRODUCERS_TO_CONSUMERS, snapshot);
        Collection<EnergyListener> listeners = host.listeners();
        for (EnergyNetworkImpl network : networks.toArray(new EnergyNetworkImpl[0])) {
            if (!network.isValid()) {
                continue;
            }
            NetworkStats stats;
            try {
                stats = network.tick(tick);
            } catch (Throwable t) {
                host.reportError("Network " + network.getId() + " in world " + name + " failed to balance", t);
                stats = NetworkStats.EMPTY;
                network.setLastStats(stats);
            }
            if (stats.transferred() > 0 || stats.produced() > 0) {
                moved = true;
            }
            for (EnergyListener listener : listeners) {
                try {
                    listener.onNetworkTick(network, stats);
                } catch (Throwable t) {
                    host.reportError("Listener threw in onNetworkTick", t);
                }
            }
        }
        moved |= directPass(DirectTransfer.Pass.STORAGE_TO_CONSUMERS, snapshot);
        moved |= directPass(DirectTransfer.Pass.PRODUCERS_TO_STORAGE, snapshot);
        if (moved) {
            dirty = true;
        }
    }

    private boolean directPass(DirectTransfer.Pass pass, NodeEntry[] snapshot) {
        if (!directTransfer) {
            return false;
        }
        try {
            return DirectTransfer.run(pass, snapshot, nodes, this::accepts) > 0;
        } catch (Throwable t) {
            host.reportError("Direct transfer (" + pass + ") failed in world " + name, t);
            return false;
        }
    }

    private boolean isActive(NodeEntry entry) {
        try {
            return host.isActive(entry.pos, entry.blockTypeId);
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- EnergyWorld: queries ----------------------------------------------------------

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getTick() {
        return tick;
    }

    @Override
    public EnergyNode getNode(BlockPos pos) {
        NodeEntry entry = nodes.get(pos);
        return entry == null ? null : entry.node;
    }

    @Override
    public NodeType getNodeType(BlockPos pos) {
        NodeEntry entry = nodes.get(pos);
        return entry == null ? null : entry.type;
    }

    @Override
    public String getBoundBlockId(BlockPos pos) {
        NodeEntry entry = nodes.get(pos);
        return entry == null ? null : entry.blockTypeId;
    }

    @Override
    public Collection<BlockPos> getNodePositions() {
        return Collections.unmodifiableSet(nodes.keySet());
    }

    @Override
    public int getNodeCount() {
        return nodes.size();
    }

    @Override
    public NodeFlow getFlow(BlockPos pos) {
        NodeEntry entry = nodes.get(pos);
        return entry == null ? NodeFlow.NONE : new NodeFlow(entry.received, entry.sent);
    }

    @Override
    public EnergyNetwork getNetwork(BlockPos pos) {
        NodeEntry entry = nodes.get(pos);
        return entry == null ? null : entry.network();
    }

    @Override
    public Collection<? extends EnergyNetwork> getNetworksAt(BlockPos pos) {
        NodeEntry entry = nodes.get(pos);
        return entry == null ? List.of() : entry.networks();
    }

    @Override
    public Collection<? extends EnergyNetwork> getNetworks() {
        return Collections.unmodifiableSet(networks);
    }

    @Override
    public EnergyNode refreshBlock(BlockPos pos) {
        host.checkThread();
        return host.refreshBlock(this, pos);
    }

    @Override
    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public Host host() {
        return host;
    }

    /** Whether touching generators and batteries push energy into touching blocks in this world. */
    public boolean allowsDirectTransfer() {
        return directTransfer;
    }

    // ---- EnergyWorld: mutations --------------------------------------------------------

    @Override
    public EnergyNode addNode(BlockPos pos, NodeType type) {
        Objects.requireNonNull(type, "type");
        EnergyNode node = type.create(new NodeSpawnContext(name, pos, null, false));
        attach(pos, type, null, node);
        return node;
    }

    @Override
    public void addNode(BlockPos pos, NodeType type, EnergyNode node) {
        attach(pos, type, null, node);
    }

    /** Creates and adds a node bound to a block type. */
    public EnergyNode addBlockNode(BlockPos pos, NodeType type, String blockTypeId) {
        Objects.requireNonNull(type, "type");
        EnergyNode node = type.create(new NodeSpawnContext(name, pos, blockTypeId, false));
        attach(pos, type, blockTypeId, node);
        return node;
    }

    /** Recreates a saved node. Failures are reported and return null instead of throwing. */
    public EnergyNode restoreNode(BlockPos pos, NodeType type, String blockTypeId, NodeData data) {
        if (nodes.containsKey(pos)) {
            host.reportError("Ignoring duplicate saved node " + type.id() + " at " + pos + " in world " + name, null);
            return null;
        }
        EnergyNode node;
        try {
            node = type.create(new NodeSpawnContext(name, pos, blockTypeId, true));
            node.load(data == null ? new NodeData() : data);
        } catch (Throwable t) {
            host.reportError("Could not restore node " + type.id() + " at " + pos + " in world " + name, t);
            return null;
        }
        attach(pos, type, blockTypeId, node);
        return node;
    }

    private void attach(BlockPos pos, NodeType type, String blockTypeId, EnergyNode node) {
        host.checkThread();
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(node, "node");
        if (nodes.containsKey(pos)) {
            throw new IllegalStateException("There is already a node at " + pos + " in world " + name);
        }
        NodeEntry entry = new NodeEntry(this, pos, type, blockTypeId, node);
        nodes.put(pos, entry);
        link(entry);
        entry.attached = true;
        dirty = true;
        try {
            node.onAttached(entry);
        } catch (Throwable t) {
            host.reportError("Node " + entry + " threw in onAttached()", t);
        }
        for (EnergyListener listener : host.listeners()) {
            try {
                listener.onNodeAdded(this, pos, node);
            } catch (Throwable t) {
                host.reportError("Listener threw in onNodeAdded", t);
            }
        }
    }

    @Override
    public EnergyNode removeNode(BlockPos pos) {
        return removeNode(pos, DetachReason.REMOVED);
    }

    public EnergyNode removeNode(BlockPos pos, DetachReason reason) {
        host.checkThread();
        NodeEntry entry = nodes.get(pos);
        if (entry == null) {
            return null;
        }
        try {
            entry.node.onDetached(entry, reason);
        } catch (Throwable t) {
            host.reportError("Node " + entry + " threw in onDetached()", t);
        }
        if (nodes.get(pos) != entry) {
            return entry.node; // The callback already removed it.
        }
        nodes.remove(pos);
        entry.attached = false;
        unlink(entry);
        dirty = true;
        for (EnergyListener listener : host.listeners()) {
            try {
                listener.onNodeRemoved(this, pos, entry.node, reason);
            } catch (Throwable t) {
                host.reportError("Listener threw in onNodeRemoved", t);
            }
        }
        return entry.node;
    }

    @Override
    public void refreshConnections(BlockPos pos) {
        host.checkThread();
        NodeEntry entry = nodes.get(pos);
        if (entry == null) {
            return;
        }
        if (entry.isCable()) {
            EnergyNetworkImpl before = entry.grid;
            unlinkCable(entry);
            linkCable(entry);
            if (entry.grid != before) {
                notifyNetworkChanged(entry);
            }
        } else {
            relinkEndpoint(entry);
        }
    }

    /** Detaches every node with {@link DetachReason#UNLOADED} and forgets the topology, without events. */
    public void unloadAll() {
        for (NodeEntry entry : nodes.values().toArray(new NodeEntry[0])) {
            try {
                entry.node.onDetached(entry, DetachReason.UNLOADED);
            } catch (Throwable t) {
                host.reportError("Node " + entry + " threw in onDetached()", t);
            }
            entry.attached = false;
        }
        nodes.clear();
        networks.forEach(EnergyNetworkImpl::invalidate);
        networks.clear();
    }

    /** Visits every node, for saving. */
    public void forEachNode(NodeVisitor visitor) {
        for (NodeEntry e : nodes.values()) {
            visitor.visit(e.pos, e.type, e.blockTypeId, e.node);
        }
    }

    @FunctionalInterface
    public interface NodeVisitor {
        void visit(BlockPos pos, NodeType type, String blockTypeId, EnergyNode node);
    }

    // ---- topology ----------------------------------------------------------------------
    //
    // Networks are made of cables only, like Redstone Flux ducts. Every other node is an
    // endpoint: it attaches to each separate cable network it touches but never joins two
    // networks together, so energy cannot travel through a machine or generator from one cable
    // run to another. (A battery touching two runs still charges from one and discharges into
    // the other, because it stores the energy in between.)

    /** Whether both faces between {@code a} and its neighbour {@code b} in direction {@code d} accept a connection. */
    private boolean accepts(NodeEntry a, NodeEntry b, Direction d) {
        try {
            return a.node.canConnect(d) && b.node.canConnect(d.opposite());
        } catch (Throwable t) {
            host.reportError("canConnect() threw for " + a + " or " + b, t);
            return false;
        }
    }

    private void link(NodeEntry entry) {
        if (entry.isCable()) {
            linkCable(entry);
        } else {
            relinkEndpoint(entry);
        }
    }

    private void unlink(NodeEntry entry) {
        if (entry.isCable()) {
            unlinkCable(entry);
        } else {
            for (EnergyNetworkImpl network : entry.links) {
                network.removeEndpoint(entry);
            }
            entry.links.clear();
        }
    }

    /** Puts a cable into the network of the cables it touches (merging them) and attaches the blocks it touches. */
    private void linkCable(NodeEntry cable) {
        List<EnergyNetworkImpl> adjacent = new ArrayList<>(6);
        for (Direction d : Direction.all()) {
            NodeEntry nb = nodes.get(cable.pos.offset(d));
            if (nb != null && nb != cable && nb.isCable() && nb.grid != null && !adjacent.contains(nb.grid) && accepts(cable, nb, d)) {
                adjacent.add(nb.grid);
            }
        }
        if (adjacent.isEmpty()) {
            EnergyNetworkImpl network = createNetwork();
            network.addCable(cable);
            fireNetworkCreated(network);
        } else {
            EnergyNetworkImpl target = adjacent.stream().max(Comparator.comparingInt(EnergyNetworkImpl::size)).orElseThrow();
            target.addCable(cable);
            for (EnergyNetworkImpl other : adjacent) {
                if (other != target) {
                    merge(other, target);
                }
            }
        }
        relinkEndpointsAround(cable.pos);
    }

    private void unlinkCable(NodeEntry cable) {
        EnergyNetworkImpl network = cable.grid;
        if (network == null) {
            return;
        }
        network.removeCable(cable);
        cable.grid = null;
        if (network.isEmpty()) {
            for (NodeEntry endpoint : new ArrayList<>(network.endpoints())) {
                network.removeEndpoint(endpoint);
                if (endpoint.links.remove(network)) {
                    notifyNetworkChanged(endpoint);
                }
            }
            network.invalidate();
            networks.remove(network);
            fireNetworkRemoved(network);
            return;
        }
        // Removing a cable can only cut the run in two if it touched two or more of its cables.
        int neighbours = 0;
        for (Direction d : Direction.all()) {
            NodeEntry nb = nodes.get(cable.pos.offset(d));
            if (nb != null && nb != cable && nb.grid == network) {
                neighbours++;
            }
        }
        if (neighbours >= 2) {
            List<NodeEntry> endpoints = new ArrayList<>(network.endpoints());
            if (split(network)) {
                for (NodeEntry endpoint : endpoints) {
                    relinkEndpoint(endpoint);
                }
            }
        }
        relinkEndpointsAround(cable.pos);
    }

    private void merge(EnergyNetworkImpl from, EnergyNetworkImpl into) {
        List<NodeEntry> cables = new ArrayList<>(from.cables());
        List<NodeEntry> endpoints = new ArrayList<>(from.endpoints());
        for (NodeEntry cable : cables) {
            into.addCable(cable);
        }
        for (NodeEntry endpoint : endpoints) {
            endpoint.links.remove(from);
            if (!endpoint.links.contains(into)) {
                endpoint.links.add(into);
                into.addEndpoint(endpoint);
            }
        }
        from.invalidate();
        networks.remove(from);
        fireNetworkRemoved(from);
        for (NodeEntry cable : cables) {
            notifyNetworkChanged(cable);
        }
        for (NodeEntry endpoint : endpoints) {
            notifyNetworkChanged(endpoint);
        }
    }

    /** Splits a network into its connected cable runs; the largest keeps the network. Returns true if it split. */
    private boolean split(EnergyNetworkImpl network) {
        Set<NodeEntry> unvisited = new HashSet<>(network.cables());
        List<List<NodeEntry>> components = new ArrayList<>();
        ArrayDeque<NodeEntry> queue = new ArrayDeque<>();
        for (NodeEntry start : new ArrayList<>(network.cables())) {
            if (!unvisited.remove(start)) {
                continue;
            }
            List<NodeEntry> component = new ArrayList<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                NodeEntry current = queue.poll();
                component.add(current);
                for (Direction d : Direction.all()) {
                    NodeEntry nb = nodes.get(current.pos.offset(d));
                    if (nb != null && nb.grid == network && unvisited.contains(nb) && accepts(current, nb, d)) {
                        unvisited.remove(nb);
                        queue.add(nb);
                    }
                }
            }
            components.add(component);
        }
        if (components.size() <= 1) {
            return false;
        }
        components.sort(Comparator.comparingInt((List<NodeEntry> c) -> c.size()).reversed());
        for (int i = 1; i < components.size(); i++) {
            EnergyNetworkImpl part = createNetwork();
            for (NodeEntry cable : components.get(i)) {
                network.removeCable(cable);
                part.addCable(cable);
            }
            fireNetworkCreated(part);
            for (NodeEntry cable : components.get(i)) {
                notifyNetworkChanged(cable);
            }
        }
        return true;
    }

    /** Attaches a non-cable node to exactly the cable networks it touches. */
    private void relinkEndpoint(NodeEntry endpoint) {
        if (endpoint.isCable() || nodes.get(endpoint.pos) != endpoint) {
            return;
        }
        List<EnergyNetworkImpl> wanted = new ArrayList<>(2);
        for (Direction d : Direction.all()) {
            NodeEntry nb = nodes.get(endpoint.pos.offset(d));
            if (nb != null && nb.isCable() && nb.grid != null && !wanted.contains(nb.grid) && accepts(endpoint, nb, d)) {
                wanted.add(nb.grid);
            }
        }
        if (wanted.size() == endpoint.links.size() && endpoint.links.containsAll(wanted)) {
            return;
        }
        for (EnergyNetworkImpl old : endpoint.links) {
            if (!wanted.contains(old)) {
                old.removeEndpoint(endpoint);
            }
        }
        for (EnergyNetworkImpl network : wanted) {
            if (!endpoint.links.contains(network)) {
                network.addEndpoint(endpoint);
            }
        }
        endpoint.links.clear();
        endpoint.links.addAll(wanted);
        notifyNetworkChanged(endpoint);
    }

    private void relinkEndpointsAround(BlockPos pos) {
        for (Direction d : Direction.all()) {
            NodeEntry nb = nodes.get(pos.offset(d));
            if (nb != null && !nb.isCable()) {
                relinkEndpoint(nb);
            }
        }
    }

    private EnergyNetworkImpl createNetwork() {
        EnergyNetworkImpl network = new EnergyNetworkImpl(nextNetworkId++, this);
        networks.add(network);
        return network;
    }

    private void notifyNetworkChanged(NodeEntry entry) {
        if (!entry.attached) {
            return;
        }
        try {
            entry.node.onNetworkChanged(entry);
        } catch (Throwable t) {
            host.reportError("Node " + entry + " threw in onNetworkChanged()", t);
        }
    }

    private void fireNetworkCreated(EnergyNetworkImpl network) {
        for (EnergyListener listener : host.listeners()) {
            try {
                listener.onNetworkCreated(network);
            } catch (Throwable t) {
                host.reportError("Listener threw in onNetworkCreated", t);
            }
        }
    }

    private void fireNetworkRemoved(EnergyNetworkImpl network) {
        for (EnergyListener listener : host.listeners()) {
            try {
                listener.onNetworkRemoved(network);
            } catch (Throwable t) {
                host.reportError("Listener threw in onNetworkRemoved", t);
            }
        }
    }

    @Override
    public String toString() {
        return "EnergyWorld[" + name + ", " + nodes.size() + " nodes, " + networks.size() + " networks]";
    }
}
