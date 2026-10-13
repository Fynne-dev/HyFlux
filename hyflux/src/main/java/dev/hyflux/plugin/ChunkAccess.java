package dev.hyflux.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hyflux.api.registry.BlockEnergyRegistry;

/**
 * Every block read and write HyFlux makes, in one place, written against the Hytale 0.7 chunk
 * API (chunk sections, {@link BlockSection} and {@link BlockOperations}). Lookups never load
 * chunks. Call on the world thread.
 */
final class ChunkAccess {

    /** Vertical range scanned by {@code /hyflux scan}. */
    static final int MIN_Y = 0;
    static final int MAX_Y = 319;

    /** State name of a block's normal look. */
    static final String DEFAULT_STATE = "default";

    private ChunkAccess() {
    }

    static boolean isLoaded(World world, long chunkIndex) {
        Ref<ChunkStore> ref = world.getChunkStore().getChunkReference(chunkIndex);
        return ref != null && ref.isValid();
    }

    static boolean isLoaded(World world, int blockX, int blockZ) {
        return isLoaded(world, ChunkUtil.indexChunkFromBlock(blockX, blockZ));
    }

    /** The loaded chunk section holding a block, or null if it is not in memory. */
    private static Ref<ChunkStore> section(World world, int x, int y, int z) {
        Ref<ChunkStore> ref = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        return ref != null && ref.isValid() ? ref : null;
    }

    /** The block type at a position, or null if its chunk section is not loaded. */
    static BlockType blockType(World world, int x, int y, int z) {
        Ref<ChunkStore> ref = section(world, x, y, z);
        if (ref == null) {
            return null;
        }
        BlockSection blocks = world.getChunkStore().getStore().getComponent(ref, BlockSection.getComponentType());
        if (blocks == null) {
            return null;
        }
        BlockType type = BlockType.getAssetMap().getAsset(blocks.get(x, y, z));
        return type == null ? BlockType.EMPTY : type;
    }

    /** Places a block. Returns false if the id is unknown or the chunk section is not loaded. */
    static boolean setBlock(World world, int x, int y, int z, String blockTypeId) {
        BlockType type = BlockType.getAssetMap().getAsset(blockTypeId);
        Ref<ChunkStore> ref = section(world, x, y, z);
        if (type == null || ref == null) {
            return false;
        }
        int id = BlockType.getAssetMap().getIndex(blockTypeId);
        return BlockOperations.setBlock(world.getChunkStore(), ref, x, y, z, id, type, 0, 0, 0);
    }

    /**
     * Switches a block to one of its state variants ({@link #DEFAULT_STATE} for the normal look).
     * Does nothing if the block already shows that state. Returns false if the section is not loaded.
     */
    static boolean setBlockState(World world, int x, int y, int z, String state) {
        BlockType current = blockType(world, x, y, z);
        Ref<ChunkStore> ref = section(world, x, y, z);
        if (current == null || ref == null) {
            return false;
        }
        String id = current.getId();
        boolean showing = DEFAULT_STATE.equals(state)
            ? id.equals(BlockEnergyRegistry.baseBlockId(id))
            : id.endsWith("_State_Definitions_" + state);
        if (!showing) {
            BlockOperations.setBlockInteractionState(world.getChunkStore(), ref, x, y, z, current, state, true);
        }
        return true;
    }
}
