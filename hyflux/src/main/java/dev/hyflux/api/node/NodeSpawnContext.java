package dev.hyflux.api.node;

import dev.hyflux.api.math.BlockPos;

/**
 * Information handed to a {@link NodeFactory} when HyFlux creates a node, either because
 * a bound block was placed or because a saved node is being restored.
 *
 * @param worldName   name of the world the node is created in
 * @param pos         position of the node
 * @param blockTypeId Hytale block type id the node is bound to, or null for nodes added directly through the API
 * @param restoring   true when the node is being recreated from saved data (its {@code load} is called next)
 */
public record NodeSpawnContext(String worldName, BlockPos pos, String blockTypeId, boolean restoring) {
}
