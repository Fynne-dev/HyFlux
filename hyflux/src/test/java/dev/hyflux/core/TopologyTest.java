package dev.hyflux.core;

import dev.hyflux.api.event.EnergyListener;
import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.math.Direction;
import dev.hyflux.api.network.EnergyNetwork;
import dev.hyflux.api.network.EnergyWorld;
import dev.hyflux.api.node.BatteryNode;
import dev.hyflux.api.node.CableNode;
import dev.hyflux.api.node.ConsumerNode;
import dev.hyflux.api.node.DetachReason;
import dev.hyflux.api.node.EnergyNode;
import dev.hyflux.api.node.NodeContext;
import dev.hyflux.api.node.NodeType;
import dev.hyflux.api.node.ProducerNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopologyTest {

    static final NodeType CABLE = NodeType.of("test:cable", () -> new CableNode(100));
    static final NodeType BATTERY = NodeType.of("test:battery", () -> new BatteryNode(1_000, 100));

    private static BlockPos p(int x, int y, int z) {
        return BlockPos.of(x, y, z);
    }

    @Test
    void adjacentNodesShareANetwork() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0, 0, 0), CABLE);
        w.addNode(p(1, 0, 0), CABLE);
        w.addNode(p(5, 0, 0), CABLE);
        assertEquals(2, w.getNetworks().size());
        assertSame(w.getNetwork(p(0, 0, 0)), w.getNetwork(p(1, 0, 0)));
        assertNotSame(w.getNetwork(p(0, 0, 0)), w.getNetwork(p(5, 0, 0)));
    }

    @Test
    void bridgingMergesNetworks() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0, 0, 0), CABLE);
        w.addNode(p(2, 0, 0), CABLE);
        w.addNode(p(1, 1, 0), CABLE);
        assertEquals(3, w.getNetworks().size());
        List<EnergyNetwork> before = new ArrayList<>(w.getNetworks());
        w.addNode(p(1, 0, 0), CABLE);
        assertEquals(1, w.getNetworks().size());
        assertEquals(4, w.getNetwork(p(1, 1, 0)).size());
        assertEquals(2, before.stream().filter(n -> !n.isValid()).count(), "absorbed networks are invalidated");
    }

    @Test
    void removingABridgeSplits() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        for (int x = 0; x < 5; x++) {
            w.addNode(p(x, 0, 0), CABLE);
        }
        assertEquals(1, w.getNetworks().size());
        w.removeNode(p(2, 0, 0));
        assertEquals(2, w.getNetworks().size());
        assertEquals(2, w.getNetwork(p(0, 0, 0)).size());
        assertEquals(2, w.getNetwork(p(4, 0, 0)).size());
    }

    @Test
    void removingALoopMemberDoesNotSplit() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (x != 1 || z != 1) {
                    w.addNode(p(x, 0, z), CABLE); // a ring around an empty centre
                }
            }
        }
        assertEquals(1, w.getNetworks().size());
        w.removeNode(p(1, 0, 0));
        assertEquals(1, w.getNetworks().size());
        assertEquals(7, w.getNetwork(p(0, 0, 0)).size());
    }

    @Test
    void removingLastNodeRemovesNetwork() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0, 0, 0), CABLE);
        EnergyNetwork net = w.getNetwork(p(0, 0, 0));
        assertSame(CableNode.class, w.removeNode(p(0, 0, 0)).getClass());
        assertTrue(w.getNetworks().isEmpty());
        assertFalse(net.isValid());
        assertNull(w.removeNode(p(0, 0, 0)));
    }

    @Test
    void sideTogglesRewireNetworks() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0, 0, 0), CABLE);
        CableNode middle = (CableNode) w.addNode(p(1, 0, 0), CABLE);
        w.addNode(p(2, 0, 0), CABLE);
        middle.setSideEnabled(Direction.WEST, false);
        assertEquals(2, w.getNetworks().size());
        assertSame(w.getNetwork(p(1, 0, 0)), w.getNetwork(p(2, 0, 0)));
        middle.setSideEnabled(Direction.EAST, false);
        assertEquals(3, w.getNetworks().size());
        middle.setSideEnabled(Direction.WEST, true);
        middle.setSideEnabled(Direction.EAST, true);
        assertEquals(1, w.getNetworks().size());
    }

    @Test
    void bothSidesMustAcceptAConnection() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        NodeType oneWay = NodeType.of("test:one_way", () -> {
            CableNode c = new CableNode(100);
            c.setSideEnabled(Direction.EAST, false);
            return c;
        });
        w.addNode(p(0, 0, 0), oneWay);
        w.addNode(p(1, 0, 0), CABLE);
        assertEquals(2, w.getNetworks().size());
    }

    @Test
    void duplicatePositionsAreRejected() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0, 0, 0), CABLE);
        assertThrows(IllegalStateException.class, () -> w.addNode(p(0, 0, 0), CABLE));
    }

    @Test
    void lifecycleCallbacksAndListenersFire() {
        List<String> events = new ArrayList<>();
        EnergyListener listener = new EnergyListener() {
            @Override
            public void onNodeAdded(EnergyWorld world, BlockPos pos, EnergyNode node) {
                events.add("added " + pos.toShortString());
            }

            @Override
            public void onNodeRemoved(EnergyWorld world, BlockPos pos, EnergyNode node, DetachReason reason) {
                events.add("removed " + pos.toShortString() + " " + reason);
            }

            @Override
            public void onNetworkCreated(EnergyNetwork network) {
                events.add("net+");
            }

            @Override
            public void onNetworkRemoved(EnergyNetwork network) {
                events.add("net-");
            }
        };
        EnergyWorldImpl w = new EnergyWorldImpl("t", new EnergyWorldImpl.Host() {
            @Override
            public Collection<EnergyListener> listeners() {
                return List.of(listener);
            }
        });
        List<String> nodeEvents = new ArrayList<>();
        NodeType tracked = NodeType.of("test:tracked", () -> new ProducerNode(10, 10, 1) {
            @Override
            protected void onAdded(NodeContext context) {
                nodeEvents.add("attached, network " + context.network());
            }

            @Override
            protected void onRemoved(NodeContext context, DetachReason reason) {
                nodeEvents.add("detached " + reason);
            }
        });
        w.addNode(p(0, 0, 0), tracked);
        w.addNode(p(1, 0, 0), CABLE);
        w.removeNode(p(0, 0, 0));
        assertEquals(List.of("added 0,0,0", "net+", "added 1,0,0", "removed 0,0,0 REMOVED"), events);
        assertEquals(List.of("attached, network null", "detached REMOVED"), nodeEvents);
    }

    @Test
    void unloadAllDetachesWithUnloadedReason() {
        List<DetachReason> reasons = new ArrayList<>();
        NodeType tracked = NodeType.of("test:tracked2", () -> new BatteryNode(10, 10) {
            @Override
            protected void onRemoved(NodeContext context, DetachReason reason) {
                reasons.add(reason);
            }
        });
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0, 0, 0), tracked);
        w.addNode(p(0, 1, 0), tracked);
        w.unloadAll();
        assertEquals(List.of(DetachReason.UNLOADED, DetachReason.UNLOADED), reasons);
        assertEquals(0, w.getNodeCount());
        assertTrue(w.getNetworks().isEmpty());
    }

    @Test
    void largeGridSplitsIntoTwoHalves() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        int size = 40;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                w.addNode(p(x, 0, z), CABLE);
            }
        }
        assertEquals(1, w.getNetworks().size());
        for (int z = 0; z < size; z++) {
            w.removeNode(p(size / 2, 0, z));
        }
        assertEquals(2, w.getNetworks().size());
        assertEquals(size * (size - 1), w.getNodeCount());
    }

    static final NodeType GENERATOR = NodeType.of("test:generator", () -> new ProducerNode(1_000, 100, 10));
    static final NodeType LAMP = NodeType.of("test:lamp", () -> new ConsumerNode(100, 20, 10));

    @Test
    void generatorsMachinesAndBatteriesDoNotConnectByTouching() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0, 0, 0), GENERATOR);
        w.addNode(p(1, 0, 0), LAMP);
        w.addNode(p(2, 0, 0), LAMP);
        w.addNode(p(0, 1, 0), BATTERY);
        w.addNode(p(0, 2, 0), BATTERY);
        assertTrue(w.getNetworks().isEmpty(), "networks are made of cables; touching blocks form none");
        assertNull(w.getNetwork(p(0, 0, 0)));
    }

    @Test
    void aCableJoinsTheBlocksItTouches() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0, 0, 0), GENERATOR);
        w.addNode(p(2, 0, 0), LAMP);
        w.addNode(p(1, 1, 0), BATTERY);
        assertTrue(w.getNetworks().isEmpty());
        w.addNode(p(1, 0, 0), CABLE); // touches all three
        assertEquals(1, w.getNetworks().size());
        assertEquals(4, w.getNetwork(p(0, 0, 0)).size());
        w.removeNode(p(1, 0, 0));
        assertTrue(w.getNetworks().isEmpty(), "removing the only cable disconnects everything again");
        assertNull(w.getNetwork(p(0, 0, 0)));
        assertNull(w.getNetwork(p(1, 1, 0)));
    }

    @Test
    void touchingBlocksOnTheSameCableDoNotFormAShortcut() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        // generator and lamp touch each other and each touches its own cable run
        w.addNode(p(0, 0, 0), GENERATOR);
        w.addNode(p(1, 0, 0), LAMP);
        w.addNode(p(0, 1, 0), CABLE);
        w.addNode(p(1, 1, 0), CABLE);
        assertEquals(1, w.getNetworks().size());
        w.removeNode(p(1, 1, 0));
        assertEquals(1, w.getNetworks().size());
        assertNull(w.getNetwork(p(1, 0, 0)), "the lamp only connected through the removed cable");
        assertEquals(2, w.getNetwork(p(0, 0, 0)).size());
    }

    @Test
    void aMachineBetweenTwoCableRunsDoesNotJoinThem() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0, 0, 0), GENERATOR);
        w.addNode(p(1, 0, 0), CABLE);
        w.addNode(p(2, 0, 0), LAMP);
        w.addNode(p(3, 0, 0), CABLE);
        w.addNode(p(4, 0, 0), LAMP);
        assertEquals(2, w.getNetworks().size());
        assertEquals(2, w.getNetworksAt(p(2, 0, 0)).size(), "the middle lamp is attached to both runs");
        assertNotSame(w.getNetwork(p(0, 0, 0)), w.getNetwork(p(4, 0, 0)));
    }

    @Test
    void aGeneratorBetweenTwoCableRunsDoesNotJoinThem() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0, 0, 0), CABLE);
        w.addNode(p(1, 0, 0), GENERATOR);
        w.addNode(p(2, 0, 0), CABLE);
        assertEquals(2, w.getNetworks().size());
        assertEquals(2, w.getNetworksAt(p(1, 0, 0)).size());
    }

    @Test
    void attachedBlocksFollowMergesAndSplits() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0, 1, 0), LAMP);
        w.addNode(p(4, 1, 0), BATTERY);
        for (int x = 0; x <= 4; x++) {
            if (x != 2) {
                w.addNode(p(x, 0, 0), CABLE);
            }
        }
        assertNotSame(w.getNetwork(p(0, 1, 0)), w.getNetwork(p(4, 1, 0)));
        w.addNode(p(2, 0, 0), CABLE); // bridge the gap
        assertEquals(1, w.getNetworks().size());
        assertSame(w.getNetwork(p(0, 1, 0)), w.getNetwork(p(4, 1, 0)));
        assertEquals(7, w.getNetwork(p(0, 1, 0)).size());
        w.removeNode(p(2, 0, 0)); // cut it again
        assertEquals(2, w.getNetworks().size());
        assertNotSame(w.getNetwork(p(0, 1, 0)), w.getNetwork(p(4, 1, 0)));
        assertEquals(3, w.getNetwork(p(0, 1, 0)).size(), "2 cables + the lamp");
        assertEquals(3, w.getNetwork(p(4, 1, 0)).size(), "2 cables + the battery");
    }
}
