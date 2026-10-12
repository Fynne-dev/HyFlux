package dev.hyflux.api.network;

/**
 * What a network did during one tick. All amounts are HF for that tick.
 *
 * @param tick       world energy tick the stats belong to
 * @param available  energy producers offered
 * @param demand     energy consumers asked for
 * @param produced   energy actually taken from producers
 * @param consumed   energy actually delivered to consumers
 * @param charged    energy put into batteries
 * @param discharged energy taken out of batteries
 * @param voided     energy lost because a storage accepted less than it promised (normally 0)
 * @param limit      most energy the network could move this tick ({@link Long#MAX_VALUE} without conduits)
 * @param limited    true if the conduit limit held back energy that would otherwise have moved
 */
public record NetworkStats(
    long tick,
    long available,
    long demand,
    long produced,
    long consumed,
    long charged,
    long discharged,
    long voided,
    long limit,
    boolean limited
) {

    public static final NetworkStats EMPTY = new NetworkStats(0, 0, 0, 0, 0, 0, 0, 0, Long.MAX_VALUE, false);

    /** Energy that moved through the network (to consumers plus into batteries). */
    public long transferred() {
        return consumed + charged;
    }

    /** Share of consumer demand that was met, from 0.0 to 1.0 (1.0 if nothing was asked for). */
    public double satisfaction() {
        return demand <= 0 ? 1.0 : Math.min(1.0, (double) consumed / (double) demand);
    }

    /** True if every consumer got all it asked for. */
    public boolean isSatisfied() {
        return consumed >= demand;
    }

    /** Change in stored battery energy this tick (positive when charging). */
    public long storageDelta() {
        return charged - discharged;
    }
}
