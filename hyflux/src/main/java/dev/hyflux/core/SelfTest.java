package dev.hyflux.core;

import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.math.Direction;
import dev.hyflux.api.network.EnergyNetwork;
import dev.hyflux.api.node.BatteryNode;
import dev.hyflux.api.node.CableNode;
import dev.hyflux.api.node.ConsumerNode;
import dev.hyflux.api.node.NodeData;
import dev.hyflux.api.node.NodeType;
import dev.hyflux.api.node.ProducerNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the energy simulation against small scripted networks in a scratch world and
 * reports what happened. Used by {@code /hyflux selftest} to prove the engine works
 * inside a live server; the unit tests cover the same ground in more depth.
 */
public final class SelfTest {

    public record Check(String name, boolean passed, String detail) {
    }

    private static final NodeType GEN = NodeType.of("selftest:generator", () -> new ProducerNode(1_000, 100, 50));
    private static final NodeType MACHINE = NodeType.of("selftest:machine", () -> new ConsumerNode(1_000, 100, 20));
    private static final NodeType BATTERY = NodeType.of("selftest:battery", () -> new BatteryNode(10_000, 1_000));
    private static final NodeType CABLE = NodeType.of("selftest:cable", () -> new CableNode(10));
    private static final NodeType FAST_CABLE = NodeType.of("selftest:fast_cable", () -> new CableNode(1_000));

    private SelfTest() {
    }

    public static List<Check> run() {
        List<Check> checks = new ArrayList<>();
        checks.add(safely("generator powers machine over cables", SelfTest::poweredMachine));
        checks.add(safely("touching blocks share energy one hop", SelfTest::touchingBlocks));
        checks.add(safely("a machine never bridges two cable runs", SelfTest::machineDoesNotBridge));
        checks.add(safely("surplus charges battery", SelfTest::surplusToBattery));
        checks.add(safely("battery covers shortfall when generator is removed", SelfTest::batteryAfterSplit));
        checks.add(safely("cable limits throughput", SelfTest::cableLimit));
        checks.add(safely("disabled side disconnects a block", SelfTest::sideToggle));
        checks.add(safely("node state survives save/load", SelfTest::saveLoad));
        return checks;
    }

    private interface Scenario {
        String run() throws Exception;
    }

    private static Check safely(String name, Scenario scenario) {
        try {
            return new Check(name, true, scenario.run());
        } catch (AssertionError e) {
            return new Check(name, false, e.getMessage());
        } catch (Throwable t) {
            return new Check(name, false, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String poweredMachine() {
        EnergyWorldImpl w = new EnergyWorldImpl("selftest");
        BlockPos p = BlockPos.ORIGIN;
        w.addNode(p, GEN);
        w.addNode(p.offset(Direction.EAST, 1), FAST_CABLE);
        w.addNode(p.offset(Direction.EAST, 2), MACHINE);
        require(w.getNetworks().size() == 1, "expected 1 network, got " + w.getNetworks().size());
        for (int i = 0; i < 10; i++) {
            w.tick();
        }
        ConsumerNode machine = w.getNode(p.offset(Direction.EAST, 2), ConsumerNode.class);
        require(machine.isPowered(), "machine is not powered");
        return "machine ran " + machine.getTicksPowered() + " of 10 ticks";
    }

    private static String touchingBlocks() {
        // lamp2 - lamp1 - generator - battery - lamp3, no cables
        EnergyWorldImpl w = new EnergyWorldImpl("selftest");
        BlockPos lamp2 = BlockPos.of(0, 0, 0);
        BlockPos lamp1 = BlockPos.of(1, 0, 0);
        BlockPos gen = BlockPos.of(2, 0, 0);
        BlockPos battery = BlockPos.of(3, 0, 0);
        BlockPos lamp3 = BlockPos.of(4, 0, 0);
        NodeType lamp = NodeType.of("selftest:lamp", () -> new ConsumerNode(40, 20, 10));
        w.addNode(lamp2, lamp);
        w.addNode(lamp1, lamp);
        w.addNode(gen, NodeType.of("selftest:big_generator", () -> new ProducerNode(1_000, 200, 100)));
        w.addNode(battery, BATTERY);
        w.addNode(lamp3, lamp);
        for (int i = 0; i < 20; i++) {
            w.tick();
        }
        require(w.getNode(lamp1, ConsumerNode.class).isPowered(), "machine touching the generator is not powered");
        require(w.getStorage(battery).getEnergyStored() > 0, "battery touching the generator did not charge");
        require(w.getNode(lamp3, ConsumerNode.class).isPowered(), "machine touching the battery is not powered");
        require(w.getStorage(lamp2).getEnergyStored() == 0, "a machine passed energy on to the machine it touches");
        return "generator feeds machine and charges battery, battery feeds machine, machine-to-machine stays at 0 HF";
    }

    private static String machineDoesNotBridge() {
        // generator - cable - lamp - cable - lamp: the second run only touches lamps
        EnergyWorldImpl w = new EnergyWorldImpl("selftest");
        NodeType lamp = NodeType.of("selftest:lamp", () -> new ConsumerNode(40, 20, 10));
        BlockPos first = BlockPos.of(2, 0, 0);
        BlockPos second = BlockPos.of(4, 0, 0);
        w.addNode(BlockPos.ORIGIN, GEN);
        w.addNode(BlockPos.of(1, 0, 0), FAST_CABLE);
        w.addNode(first, lamp);
        w.addNode(BlockPos.of(3, 0, 0), FAST_CABLE);
        w.addNode(second, lamp);
        require(w.getNetworks().size() == 2, "the two cable runs must stay separate, got " + w.getNetworks().size() + " networks");
        require(w.getNetworksAt(first).size() == 2, "the first lamp should be attached to both runs");
        for (int i = 0; i < 20; i++) {
            w.tick();
        }
        require(w.getNode(first, ConsumerNode.class).isPowered(), "lamp on the generator cable is not powered");
        require(w.getStorage(second).getEnergyStored() == 0, "energy passed through a lamp into the other cable run");
        return "lamp on the generator run is lit, lamp on the other run stays at 0 HF";
    }

    private static String surplusToBattery() {
        EnergyWorldImpl w = new EnergyWorldImpl("selftest");
        w.addNode(BlockPos.ORIGIN, GEN);
        w.addNode(BlockPos.of(1, 0, 0), FAST_CABLE);
        w.addNode(BlockPos.of(2, 0, 0), BATTERY);
        for (int i = 0; i < 20; i++) {
            w.tick();
        }
        long stored = w.getStorage(BlockPos.of(2, 0, 0)).getEnergyStored();
        require(stored > 0, "battery stayed empty");
        return "battery holds " + stored + " HF after 20 ticks";
    }

    private static String batteryAfterSplit() {
        EnergyWorldImpl w = new EnergyWorldImpl("selftest");
        BlockPos gen = BlockPos.ORIGIN;
        BlockPos battery = BlockPos.of(2, 0, 0);
        BlockPos machine = BlockPos.of(4, 0, 0);
        w.addNode(gen, GEN);
        w.addNode(BlockPos.of(1, 0, 0), FAST_CABLE);
        w.addNode(battery, BATTERY);
        w.addNode(BlockPos.of(3, 0, 0), FAST_CABLE);
        w.addNode(machine, MACHINE);
        for (int i = 0; i < 40; i++) {
            w.tick();
        }
        w.removeNode(gen);
        long before = w.getStorage(battery).getEnergyStored();
        for (int i = 0; i < 5; i++) {
            w.tick();
        }
        long after = w.getStorage(battery).getEnergyStored();
        require(w.getNode(machine, ConsumerNode.class).isPowered(), "machine lost power");
        require(after < before, "battery did not discharge");
        return "battery went " + before + " -> " + after + " HF";
    }

    private static String cableLimit() {
        EnergyWorldImpl w = new EnergyWorldImpl("selftest");
        w.addNode(BlockPos.ORIGIN, GEN);
        w.addNode(BlockPos.of(1, 0, 0), CABLE);
        w.addNode(BlockPos.of(2, 0, 0), BATTERY);
        for (int i = 0; i < 5; i++) {
            w.tick();
        }
        EnergyNetwork net = w.getNetwork(BlockPos.ORIGIN);
        require(net.getLastStats().transferred() <= 10, "moved " + net.getLastStats().transferred() + " HF through a 10 HF/t cable");
        require(net.getLastStats().limited(), "stats do not report the cable limit");
        return "moved " + net.getLastStats().transferred() + " HF/t through a 10 HF/t cable";
    }

    private static String sideToggle() {
        EnergyWorldImpl w = new EnergyWorldImpl("selftest");
        w.addNode(BlockPos.ORIGIN, GEN);
        CableNode cable = (CableNode) w.addNode(BlockPos.of(1, 0, 0), CABLE);
        w.addNode(BlockPos.of(2, 0, 0), MACHINE);
        BlockPos machine = BlockPos.of(2, 0, 0);
        require(w.getNetwork(machine) != null, "machine should be on the cable");
        cable.setSideEnabled(Direction.EAST, false);
        require(w.getNetwork(machine) == null, "machine still attached after disabling the cable side");
        cable.setSideEnabled(Direction.EAST, true);
        require(w.getNetwork(machine) != null, "machine not re-attached after re-enabling");
        return "attached -> detached -> attached";
    }

    private static String saveLoad() {
        BatteryNode original = new BatteryNode(10_000, 1_000);
        original.getStorage().setEnergy(4_321);
        original.setPriority(3);
        original.setSideEnabled(Direction.UP, false);
        NodeData data = new NodeData();
        original.save(data);
        BatteryNode copy = new BatteryNode(10_000, 1_000);
        copy.load(data);
        require(copy.getStorage().getEnergyStored() == 4_321, "energy not restored");
        require(copy.getPriority() == 3, "priority not restored");
        require(!copy.canConnect(Direction.UP), "side setting not restored");
        return "restored " + data;
    }
}
