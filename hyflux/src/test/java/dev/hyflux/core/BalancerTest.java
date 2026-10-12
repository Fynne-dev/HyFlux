package dev.hyflux.core;

import dev.hyflux.api.energy.EnergyStorage;
import dev.hyflux.api.energy.IEnergyStorage;
import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.network.NetworkStats;
import dev.hyflux.api.node.BatteryNode;
import dev.hyflux.api.node.CableNode;
import dev.hyflux.api.node.ConsumerNode;
import dev.hyflux.api.node.EnergyNode;
import dev.hyflux.api.node.EnergyRole;
import dev.hyflux.api.node.NodeType;
import dev.hyflux.api.node.ProducerNode;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalancerTest {

    /** A cable that never limits, so tests can focus on the distribution rules. */
    static final NodeType BUS = NodeType.of("test:bus", () -> new CableNode(Long.MAX_VALUE));

    /** A consumer that only buffers (uses nothing), so delivered energy is easy to read. */
    static NodeType sink(String id, long maxInput, int priority) {
        return NodeType.of("test:" + id, () -> {
            ConsumerNode n = new ConsumerNode(1_000_000, maxInput, 0);
            n.setPriority(priority);
            return n;
        });
    }

    static NodeType source(String id, long perTick, int priority) {
        return NodeType.of("test:" + id, () -> {
            ProducerNode n = new ProducerNode(perTick, perTick, perTick);
            n.setPriority(priority);
            return n;
        });
    }

    /**
     * Places a node in slot {@code slot} on top of a cable bus running along y = 0. Slots are two
     * blocks apart so nodes never touch each other and only exchange energy through the network.
     */
    static BlockPos onBus(EnergyWorldImpl w, int slot, NodeType type) {
        for (int x = slot * 2; x <= slot * 2 + 1; x++) {
            if (!w.hasNode(BlockPos.of(x, 0, 0))) {
                w.addNode(BlockPos.of(x, 0, 0), BUS);
            }
        }
        BlockPos pos = BlockPos.of(slot * 2, 1, 0);
        w.addNode(pos, type);
        return pos;
    }

    private static long stored(EnergyWorldImpl w, BlockPos pos) {
        return w.getStorage(pos).getEnergyStored();
    }

    @Test
    void producerFeedsConsumer() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        BlockPos gen = onBus(w, 0, source("gen", 100, 0));
        BlockPos consumer = onBus(w, 1, sink("sink", 1_000, 0));
        w.tick();
        assertEquals(100, stored(w, consumer));
        NetworkStats s = w.getNetwork(gen).getLastStats();
        assertEquals(100, s.produced());
        assertEquals(100, s.consumed());
        assertEquals(1_000, s.demand());
        assertFalse(s.isSatisfied(), "the sink wanted 1000 but only 100 was produced");
        assertEquals(0.1, s.satisfaction(), 1e-9);
    }

    @Test
    void equalPrioritiesShareEvenly() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        onBus(w, 0, source("gen", 90, 0));
        BlockPos[] sinks = new BlockPos[3];
        for (int i = 0; i < 3; i++) {
            sinks[i] = onBus(w, i + 1, sink("sink" + i, 1_000, 0));
        }
        w.tick();
        for (BlockPos sink : sinks) {
            assertEquals(30, stored(w, sink));
        }
    }

    @Test
    void smallRequestsAreFilledBeforeLargeOnesAreCapped() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        onBus(w, 0, source("gen", 100, 0));
        BlockPos small = onBus(w, 1, sink("small", 10, 0));
        BlockPos big1 = onBus(w, 2, sink("big1", 1_000, 0));
        BlockPos big2 = onBus(w, 3, sink("big2", 1_000, 0));
        w.tick();
        assertEquals(10, stored(w, small));
        assertEquals(45, stored(w, big1));
        assertEquals(45, stored(w, big2));
    }

    @Test
    void higherPriorityConsumersGoFirst() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        onBus(w, 0, source("gen", 100, 0));
        BlockPos low = onBus(w, 1, sink("low", 1_000, 0));
        BlockPos high = onBus(w, 2, sink("high", 80, 5));
        w.tick();
        assertEquals(80, stored(w, high));
        assertEquals(20, stored(w, low));
    }

    @Test
    void higherPriorityProducersAreDrainedFirst() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        BlockPos solar = onBus(w, 0, source("solar", 50, 10));
        BlockPos fuel = onBus(w, 1, source("fuel", 50, 0));
        onBus(w, 2, sink("sink", 60, 0));
        w.tick();
        assertEquals(0, stored(w, solar));
        assertEquals(40, stored(w, fuel), "low priority producer keeps what was not needed");
    }

    @Test
    void surplusChargesBatteriesAndBatteriesCoverDeficits() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        NodeType machine = NodeType.of("test:machine", () -> new ConsumerNode(100, 100, 30));
        BlockPos gen = onBus(w, 0, source("gen", 50, 0));
        BlockPos battery = onBus(w, 1, NodeType.of("test:bat", () -> new BatteryNode(10_000, 1_000)));
        BlockPos consumer = onBus(w, 2, machine);
        for (int i = 0; i < 50; i++) {
            w.tick();
        }
        long charged = stored(w, battery);
        assertTrue(charged > 0, "battery charged from surplus");
        w.removeNode(gen);
        for (int i = 0; i < 10; i++) {
            w.tick();
            assertTrue(w.getNode(consumer, ConsumerNode.class).isPowered(), "machine keeps running on battery");
        }
        assertEquals(charged - 300, stored(w, battery));
        NetworkStats s = w.getNetwork(battery).getLastStats();
        assertEquals(30, s.discharged());
        assertEquals(0, s.produced());
    }

    @Test
    void energyNeverPassesThroughAMachineBetweenCableRuns() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(BlockPos.of(0, 0, 0), source("gen", 100, 0));
        w.addNode(BlockPos.of(1, 0, 0), BUS);
        w.addNode(BlockPos.of(2, 0, 0), sink("middle", 50, 0));
        w.addNode(BlockPos.of(3, 0, 0), BUS);
        w.addNode(BlockPos.of(4, 0, 0), sink("far", 1_000, 0));
        for (int i = 0; i < 20; i++) {
            w.tick();
        }
        assertEquals(1_000, stored(w, BlockPos.of(2, 0, 0)), "the middle machine fills from the generator run");
        assertEquals(0, stored(w, BlockPos.of(4, 0, 0)), "nothing reaches the run that only touches machines");
    }

    @Test
    void aBatteryBetweenCableRunsRelaysThroughItsStorage() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(BlockPos.of(0, 0, 0), source("gen", 100, 0));
        w.addNode(BlockPos.of(1, 0, 0), BUS);
        w.addNode(BlockPos.of(2, 0, 0), NodeType.of("test:relay", () -> new BatteryNode(10_000, 100)));
        w.addNode(BlockPos.of(3, 0, 0), BUS);
        w.addNode(BlockPos.of(4, 0, 0), sink("far", 1_000, 0));
        for (int i = 0; i < 20; i++) {
            w.tick();
        }
        assertTrue(stored(w, BlockPos.of(4, 0, 0)) > 0, "the battery charges from one run and discharges into the other");
    }

    @Test
    void slowestCableLimitsTheNetwork() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(BlockPos.of(0, 0, 0), source("gen", 1_000, 0));
        w.addNode(BlockPos.of(1, 0, 0), NodeType.of("test:fast", () -> new CableNode(500)));
        w.addNode(BlockPos.of(2, 0, 0), NodeType.of("test:slow", () -> new CableNode(64)));
        w.addNode(BlockPos.of(3, 0, 0), sink("sink", 10_000, 0));
        w.tick();
        NetworkStats s = w.getNetwork(BlockPos.ORIGIN).getLastStats();
        assertEquals(64, s.limit());
        assertEquals(64, s.consumed());
        assertTrue(s.limited());
        assertEquals(64, w.getNetwork(BlockPos.ORIGIN).getTransferLimit());
    }

    @Test
    void inactiveNodesAreFrozen() {
        BlockPos frozen = BlockPos.of(2, 1, 0); // bus slot 1
        EnergyWorldImpl w = new EnergyWorldImpl("t", new EnergyWorldImpl.Host() {
            @Override
            public boolean isActive(BlockPos pos, String blockTypeId) {
                return !pos.equals(frozen);
            }
        });
        onBus(w, 0, source("gen", 100, 0));
        onBus(w, 1, sink("frozen", 1_000, 0));
        BlockPos live = onBus(w, 2, sink("live", 1_000, 0));
        w.tick();
        assertEquals(0, stored(w, frozen));
        assertEquals(100, stored(w, live));
    }

    @Test
    void storageThatBreaksItsPromiseIsCountedAsVoided() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        BlockPos gen = onBus(w, 0, source("gen", 100, 0));
        EnergyNode liar = new EnergyNode() {
            private final IEnergyStorage storage = new EnergyStorage(1_000) {
                @Override
                public long receiveEnergy(long maxReceive, boolean simulate) {
                    return simulate ? Math.min(maxReceive, 100) : 0;
                }
            };

            @Override
            public EnergyRole getRole() {
                return EnergyRole.CONSUMER;
            }

            @Override
            public IEnergyStorage getEnergyStorage() {
                return storage;
            }
        };
        w.addNode(BlockPos.of(2, 0, 0), BUS);
        w.addNode(BlockPos.of(2, 1, 0), NodeType.of("test:liar", () -> liar), liar);
        w.tick();
        NetworkStats s = w.getNetwork(gen).getLastStats();
        assertEquals(100, s.produced());
        assertEquals(0, s.consumed());
        assertEquals(100, s.voided());
    }

    @Test
    void randomNetworksConserveEnergy() {
        Random random = new Random(42);
        for (int round = 0; round < 30; round++) {
            boolean direct = round % 2 == 1;
            EnergyWorldImpl w = new EnergyWorldImpl("fuzz" + round, new EnergyWorldImpl.Host() {
                @Override
                public boolean allowDirectTransfer() {
                    return direct;
                }
            });
            int count = 20 + random.nextInt(60);
            for (int i = 0; i < count; i++) {
                BlockPos pos = BlockPos.of(random.nextInt(6), random.nextInt(3), random.nextInt(6));
                if (w.hasNode(pos)) {
                    continue;
                }
                int priority = random.nextInt(3);
                long a = 1 + random.nextInt(200);
                long b = 1 + random.nextInt(200);
                NodeType type = switch (random.nextInt(5)) {
                    case 0 -> NodeType.of("fuzz:gen", () -> {
                        ProducerNode n = new ProducerNode(a * 5, b, a);
                        n.setPriority(priority);
                        return n;
                    });
                    case 1 -> NodeType.of("fuzz:machine", () -> {
                        ConsumerNode n = new ConsumerNode(a * 5, b, a / 2);
                        n.setPriority(priority);
                        return n;
                    });
                    case 2 -> NodeType.of("fuzz:battery", () -> {
                        BatteryNode n = new BatteryNode(a * 50, b, a);
                        n.setPriority(priority);
                        return n;
                    });
                    default -> NodeType.of("fuzz:cable", () -> new CableNode(50 + a * 4));
                };
                w.addNode(pos, type);
            }
            for (int tick = 0; tick < 60; tick++) {
                long before = totalStored(w);
                w.tick();
                long generated = 0;
                long used = 0;
                for (BlockPos pos : w.getNodePositions()) {
                    EnergyNode node = w.getNode(pos);
                    if (node instanceof ProducerNode p) {
                        generated += p.getLastGenerated();
                    } else if (node instanceof ConsumerNode c && c.isPowered()) {
                        used += c.getUsagePerTick();
                    }
                }
                long voided = w.getNetworks().stream().mapToLong(n -> n.getLastStats().voided()).sum();
                assertEquals(0, voided, "well-behaved storages never void energy");
                assertEquals(before + generated - used, totalStored(w), "energy conserved in round " + round + " tick " + tick);
                for (var network : w.getNetworks()) {
                    NetworkStats s = network.getLastStats();
                    assertTrue(s.transferred() <= s.limit(), "limit respected");
                    assertFalse(s.charged() > 0 && s.discharged() > 0, "batteries never charge and discharge in one tick");
                    if (network.size() > 1) {
                        assertTrue(network.count(EnergyRole.CONDUIT) > 0, "networks are always joined by cables");
                    }
                }
            }
            if (round % 5 == 0 && w.getNodeCount() > 0) {
                // Knock out random nodes and keep checking that networks stay consistent.
                for (BlockPos pos : w.getNodePositions().stream().filter(x -> random.nextBoolean()).toList()) {
                    w.removeNode(pos);
                }
                long cables = w.getNodePositions().stream().filter(x -> w.getNode(x).getRole() == EnergyRole.CONDUIT).count();
                int cablesInNetworks = w.getNetworks().stream().mapToInt(n -> n.count(EnergyRole.CONDUIT)).sum();
                assertEquals(cables, cablesInNetworks, "every cable belongs to exactly one network");
                for (BlockPos x : w.getNodePositions()) {
                    if (w.getNode(x).getRole() != EnergyRole.CONDUIT) {
                        for (var network : w.getNetworksAt(x)) {
                            assertTrue(network.isValid() && network.contains(x), "attached blocks are listed by their networks");
                        }
                    }
                }
            }
        }
    }

    private static long totalStored(EnergyWorldImpl w) {
        long total = 0;
        for (BlockPos pos : w.getNodePositions()) {
            total += w.getStorage(pos).getEnergyStored();
        }
        return total;
    }
}
