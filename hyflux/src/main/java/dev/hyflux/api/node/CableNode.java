package dev.hyflux.api.node;

/**
 * A cable. It holds no energy; it joins nodes into one network and caps how much that
 * network can move per tick. A network is only as fast as its slowest cable. Blocks further
 * apart than touching need cables between them to exchange energy.
 */
public class CableNode extends AbstractEnergyNode {

    private long transferRate;

    /** @param transferRate most HF per tick the network may move through this cable */
    public CableNode(long transferRate) {
        super(EnergyRole.CONDUIT, null);
        if (transferRate <= 0) {
            throw new IllegalArgumentException("transferRate must be > 0");
        }
        this.transferRate = transferRate;
    }

    @Override
    public long getTransferRate() {
        return transferRate;
    }

    public void setTransferRate(long transferRate) {
        if (transferRate <= 0) {
            throw new IllegalArgumentException("transferRate must be > 0");
        }
        this.transferRate = transferRate;
    }
}
