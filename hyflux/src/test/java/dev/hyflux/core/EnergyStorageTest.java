package dev.hyflux.core;

import dev.hyflux.api.energy.EnergyStorage;
import dev.hyflux.api.energy.EnergyUtil;
import dev.hyflux.api.node.NodeData;
import dev.hyflux.api.registry.BlockEnergyRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyStorageTest {

    @Test
    void receiveRespectsCapacityAndRate() {
        EnergyStorage s = new EnergyStorage(1_000, 100, 50);
        assertEquals(100, s.receiveEnergy(500, false));
        assertEquals(100, s.getEnergyStored());
        s.setEnergy(950);
        assertEquals(50, s.receiveEnergy(500, false));
        assertTrue(s.isFull());
        assertEquals(0, s.receiveEnergy(10, false));
    }

    @Test
    void simulateDoesNotChangeState() {
        EnergyStorage s = new EnergyStorage(1_000, 100, 100, 500);
        assertEquals(100, s.extractEnergy(1_000, true));
        assertEquals(100, s.receiveEnergy(1_000, true));
        assertEquals(500, s.getEnergyStored());
    }

    @Test
    void zeroRateDisablesDirection() {
        EnergyStorage producerBuffer = new EnergyStorage(1_000, 0, 100, 1_000);
        assertFalse(producerBuffer.canReceive());
        assertEquals(0, producerBuffer.receiveEnergy(10, false));
        assertEquals(100, producerBuffer.extractEnergy(1_000, false));
    }

    @Test
    void generateAndConsumeIgnoreRates() {
        EnergyStorage s = new EnergyStorage(1_000, 0, 0);
        assertEquals(1_000, s.generate(5_000));
        assertFalse(s.tryConsume(1_001));
        assertTrue(s.tryConsume(400));
        assertEquals(600, s.consume(10_000));
        assertTrue(s.isEmpty());
    }

    @Test
    void negativeArgumentsAreRejectedOrIgnored() {
        assertThrows(IllegalArgumentException.class, () -> new EnergyStorage(-1));
        EnergyStorage s = new EnergyStorage(100, 100, 100, 50);
        assertEquals(0, s.receiveEnergy(-5, false));
        assertEquals(0, s.extractEnergy(-5, false));
        assertEquals(50, s.getEnergyStored());
    }

    @Test
    void saveAndLoadClampToCapacity() {
        EnergyStorage s = new EnergyStorage(1_000, 10, 10, 750);
        NodeData data = new NodeData();
        s.save(data);
        EnergyStorage smaller = new EnergyStorage(500, 10, 10);
        smaller.load(data);
        assertEquals(500, smaller.getEnergyStored());
    }

    @Test
    void transferMovesEnergyBetweenStorages() {
        EnergyStorage from = new EnergyStorage(1_000, 0, 30, 1_000);
        EnergyStorage to = new EnergyStorage(1_000, 20, 0);
        assertEquals(20, EnergyUtil.transfer(from, to, 100));
        assertEquals(980, from.getEnergyStored());
        assertEquals(20, to.getEnergyStored());
    }

    @Test
    void formattingAndSaturation() {
        assertEquals("9999 HF", EnergyUtil.format(9_999));
        assertEquals("12.5k HF", EnergyUtil.format(12_500));
        assertEquals("3.20M HF/t", EnergyUtil.formatRate(3_200_000));
        assertEquals("unlimited", EnergyUtil.formatNumber(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, EnergyUtil.saturatedAdd(Long.MAX_VALUE, 1));
    }

    @Test
    void nodeDataRoundTripsTypes() {
        NodeData data = new NodeData()
            .putLong("L", 5_000_000_000L)
            .putInt("I", 7)
            .putDouble("D", 1.5)
            .putBoolean("B", true)
            .putString("S", "hi");
        data.getOrCreateCompound("Child").putInt("X", 1);
        NodeData copy = data.copy();
        assertEquals(data, copy);
        assertEquals(5_000_000_000L, copy.getLong("L", 0));
        assertEquals(7, copy.getInt("I", 0));
        assertEquals(1.5, copy.getDouble("D", 0));
        assertTrue(copy.getBoolean("B", false));
        assertEquals("hi", copy.getString("S", null));
        assertEquals(1, copy.getCompound("Child").getInt("X", 0));
        assertEquals(42, copy.getInt("Missing", 42));
        assertThrows(IllegalArgumentException.class, () -> data.putRaw("Bad", new Object()));
    }

    @Test
    void baseBlockIdStripsStateVariants() {
        assertEquals("My_Lamp", BlockEnergyRegistry.baseBlockId("*My_Lamp_State_Definitions_On"));
        assertEquals("My_Lamp", BlockEnergyRegistry.baseBlockId("My_Lamp"));
        assertEquals("block:my_lamp", BlockEnergyRegistry.defaultTypeId("*My_Lamp_State_Definitions_On"));
    }
}
