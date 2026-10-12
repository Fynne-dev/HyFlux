package dev.hyflux.core;

import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.math.Direction;
import dev.hyflux.api.network.NodeFlow;
import dev.hyflux.api.node.BatteryNode;
import dev.hyflux.api.node.CableNode;
import dev.hyflux.api.node.ConsumerNode;
import dev.hyflux.api.node.NodeType;
import dev.hyflux.api.node.ProducerNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Blocks that touch without a cable: providers push one hop into receivers; machines never pass energy on. */
class DirectTransferTest {

    static final NodeType GENERATOR = NodeType.of("test:generator", () -> new ProducerNode(4_000, 80, 40));
    /** Lamp-like machine: small buffer, takes at most 20 HF/t, uses 10 HF/t. */
    static final NodeType LAMP = NodeType.of("test:lamp", () -> new ConsumerNode(200, 20, 10));
    /** Buffer-only machine that uses nothing, so delivered energy is easy to read. */
    static final NodeType SINK = NodeType.of("test:sink", () -> new ConsumerNode(1_000_000, 1_000, 0));
    static final NodeType BATTERY = NodeType.of("test:battery", () -> new BatteryNode(100_000, 500));

    private static BlockPos p(int x) {
        return BlockPos.of(x, 0, 0);
    }

    private static long stored(EnergyWorldImpl w, BlockPos pos) {
        return w.getStorage(pos).getEnergyStored();
    }

    private static void run(EnergyWorldImpl w, int ticks) {
        for (int i = 0; i < ticks; i++) {
            w.tick();
        }
    }

    @Test
    void generatorPowersTheLampItTouches() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0), GENERATOR);
        w.addNode(p(1), LAMP);
        run(w, 5);
        assertTrue(w.getNode(p(1), ConsumerNode.class).isPowered());
        assertEquals(new NodeFlow(20, 0), w.getFlow(p(1)));
        assertEquals(new NodeFlow(0, 20), w.getFlow(p(0)));
        assertTrue(w.getNetworks().isEmpty(), "touching blocks share energy without forming a network");
    }

    @Test
    void lampsNeverPassEnergyToTheLampTheyTouch() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0), GENERATOR);
        w.addNode(p(1), LAMP);
        w.addNode(p(2), LAMP);
        run(w, 50);
        assertTrue(w.getNode(p(1), ConsumerNode.class).isPowered());
        assertFalse(w.getNode(p(2), ConsumerNode.class).isPowered());
        assertEquals(0, stored(w, p(2)));
    }

    @Test
    void batteryChargesFromTouchingGeneratorAndPowersTouchingLamp() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0), GENERATOR);
        w.addNode(p(1), BATTERY);
        w.addNode(p(2), LAMP);
        run(w, 50);
        assertTrue(stored(w, p(1)) > 0, "battery charged");
        assertTrue(w.getNode(p(2), ConsumerNode.class).isPowered(), "lamp powered by the battery");
        w.removeNode(p(0));
        long before = stored(w, p(1));
        run(w, 10);
        assertTrue(w.getNode(p(2), ConsumerNode.class).isPowered(), "battery keeps the lamp running");
        assertEquals(before - 100, stored(w, p(1)), "the lamp draws 10 HF/t from the battery");
    }

    @Test
    void aLampBetweenGeneratorAndBatteryDoesNotRelay() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0), GENERATOR);
        w.addNode(p(1), LAMP);
        w.addNode(p(2), BATTERY);
        run(w, 50);
        assertTrue(w.getNode(p(1), ConsumerNode.class).isPowered());
        assertEquals(0, stored(w, p(2)), "energy must not travel through the lamp");
    }

    @Test
    void machinesAreServedBeforeBatteries() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        BlockPos gen = BlockPos.of(0, 0, 0);
        w.addNode(gen, NodeType.of("test:small_gen", () -> new ProducerNode(100, 100, 30)));
        w.addNode(gen.offset(Direction.EAST), LAMP);
        w.addNode(gen.offset(Direction.WEST), BATTERY);
        run(w, 3);
        assertEquals(20, w.getFlow(gen.offset(Direction.EAST)).received(), "the lamp gets all it asks for");
        assertEquals(10, w.getFlow(gen.offset(Direction.WEST)).received(), "the battery gets what is left");
    }

    @Test
    void batteryOnlyCoversTheShortfall() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        BlockPos lamp = BlockPos.of(0, 0, 0);
        BlockPos gen = lamp.offset(Direction.EAST);
        BlockPos battery = lamp.offset(Direction.WEST);
        w.addNode(lamp, LAMP);
        w.addNode(gen, NodeType.of("test:weak_gen", () -> new ProducerNode(100, 100, 5)));
        w.addNode(battery, BATTERY);
        w.getNode(battery, BatteryNode.class).getStorage().setEnergy(1_000);
        run(w, 5);
        assertEquals(5, w.getFlow(gen).sent());
        assertEquals(15, w.getFlow(battery).sent(), "lamp wants 20, generator gives 5, battery the other 15");
    }

    @Test
    void generatorSharesEvenlyBetweenTouchingMachines() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        BlockPos gen = BlockPos.of(0, 0, 0);
        w.addNode(gen, NodeType.of("test:gen90", () -> new ProducerNode(90, 90, 90)));
        w.addNode(gen.offset(Direction.EAST), SINK);
        w.addNode(gen.offset(Direction.WEST), SINK);
        w.addNode(gen.offset(Direction.UP), SINK);
        w.tick();
        for (Direction d : new Direction[]{Direction.EAST, Direction.WEST, Direction.UP}) {
            assertEquals(30, stored(w, gen.offset(d)));
        }
    }

    @Test
    void batteriesDoNotFeedEachOther() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        w.addNode(p(0), BATTERY);
        w.addNode(p(1), BATTERY);
        w.getNode(p(0), BatteryNode.class).getStorage().setEnergy(5_000);
        run(w, 10);
        assertEquals(5_000, stored(w, p(0)));
        assertEquals(0, stored(w, p(1)));
    }

    @Test
    void disabledSideBlocksTheTransfer() {
        EnergyWorldImpl w = new EnergyWorldImpl("t");
        ProducerNode gen = (ProducerNode) w.addNode(p(0), GENERATOR);
        w.addNode(p(1), LAMP);
        gen.setSideEnabled(Direction.EAST, false);
        run(w, 10);
        assertEquals(0, stored(w, p(1)));
        assertEquals(400, stored(w, p(0)), "the generator keeps its energy");
    }

    @Test
    void directTransferCanBeTurnedOff() {
        EnergyWorldImpl w = new EnergyWorldImpl("t", new EnergyWorldImpl.Host() {
            @Override
            public boolean allowDirectTransfer() {
                return false;
            }
        });
        w.addNode(p(0), GENERATOR);
        w.addNode(p(1), LAMP);
        run(w, 10);
        assertEquals(0, stored(w, p(1)));
        NodeType cable = NodeType.of("test:cable", () -> new CableNode(100));
        w.addNode(BlockPos.of(0, 1, 0), cable);
        w.addNode(BlockPos.of(1, 1, 0), cable);
        run(w, 3);
        assertTrue(w.getNode(p(1), ConsumerNode.class).isPowered(), "cables still work");
    }

    @Test
    void frozenNeighboursAreSkipped() {
        EnergyWorldImpl w = new EnergyWorldImpl("t", new EnergyWorldImpl.Host() {
            @Override
            public boolean isActive(BlockPos pos, String blockTypeId) {
                return !pos.equals(p(1));
            }
        });
        w.addNode(p(0), GENERATOR);
        w.addNode(p(1), LAMP);
        run(w, 10);
        assertEquals(0, stored(w, p(1)));
    }
}
