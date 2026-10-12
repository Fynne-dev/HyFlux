package dev.hyflux.api.node;

/** Why a node left its world. */
public enum DetachReason {
    /** Removed through the API ({@code EnergyWorld.removeNode}). */
    REMOVED,
    /** The block it was bound to was broken or replaced by an unbound block. */
    BLOCK_BROKEN,
    /** The block it was bound to was replaced by a block bound to a different node type. */
    REPLACED,
    /** The world is unloading or the server is stopping. The node will be saved and restored. */
    UNLOADED
}
