package dev.hyflux.api.network;

/**
 * Energy one node took in and gave out during the last tick, through its network and through
 * the blocks it touches combined. Useful for machine UIs ("receiving 20 HF/t").
 *
 * @param received energy put into the node
 * @param sent     energy taken out of the node
 */
public record NodeFlow(long received, long sent) {

    public static final NodeFlow NONE = new NodeFlow(0, 0);
}
