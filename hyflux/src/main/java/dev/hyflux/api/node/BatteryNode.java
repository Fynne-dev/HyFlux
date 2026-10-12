package dev.hyflux.api.node;

import dev.hyflux.api.energy.EnergyStorage;

/**
 * A battery or capacitor. The network charges it with surplus production and drains it
 * when producers fall short of consumer demand.
 */
public class BatteryNode extends AbstractEnergyNode {

    /**
     * @param capacity  most energy held
     * @param maxInput  most HF per tick it charges
     * @param maxOutput most HF per tick it discharges
     */
    public BatteryNode(long capacity, long maxInput, long maxOutput) {
        super(EnergyRole.STORAGE, new EnergyStorage(capacity, maxInput, maxOutput));
    }

    public BatteryNode(long capacity, long maxTransfer) {
        this(capacity, maxTransfer, maxTransfer);
    }
}
