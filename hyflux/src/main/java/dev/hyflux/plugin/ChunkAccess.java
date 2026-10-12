package dev.hyflux.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Block lookups that never load chunks. Goes through the chunk store components rather
 * than the deprecated {@code WorldChunk} accessors. Call on the world thread.
 */
final class ChunkAccess {

    /** World build height used by the server for block lookups. */
    static final int MIN_Y = 0;
    static final int MAX_Y = 319;

    private ChunkAccess() {
    }

    static boolean isLoaded(World world, long chunkIndex) {
        Ref<ChunkStore> ref = world.getChunkStore().getChunkReference(chunkIndex);
        return ref != null && ref.isValid();
    }

    static boolean isLoaded(World world, int blockX, int blockZ) {
        return isLoaded(world, ChunkUtil.indexChunkFromBlock(blockX, blockZ));
    }

    /** The block chunk containing a column, or null if that chunk is not loaded. */
    static BlockChunk blockChunk(World world, int blockX, int blockZ) {
        return world.getChunkStore().getChunkComponent(ChunkUtil.indexChunkFromBlock(blockX, blockZ), BlockChunk.getComponentType());
    }

    /** The block type in a loaded chunk; positions outside the build height read as empty. */
    static BlockType blockType(BlockChunk chunk, int x, int y, int z) {
        if (y < MIN_Y || y > MAX_Y) {
            return BlockType.EMPTY;
        }
        return BlockType.getAssetMap().getAsset(chunk.getBlock(x, y, z));
    }
}
