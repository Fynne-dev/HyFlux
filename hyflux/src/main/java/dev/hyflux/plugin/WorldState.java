package dev.hyflux.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hyflux.api.event.EnergyListener;
import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.node.DetachReason;
import dev.hyflux.api.node.EnergyNode;
import dev.hyflux.api.node.NodeData;
import dev.hyflux.api.node.NodeType;
import dev.hyflux.api.registry.BlockEnergyRegistry;
import dev.hyflux.core.EnergyWorldImpl;
import dev.hyflux.plugin.persist.WorldSaveFile;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Binds one Hytale {@link World} to its {@link EnergyWorldImpl}: blocks, chunks, threading and saving. */
final class WorldState implements EnergyWorldImpl.Host {

    private final HyFluxService service;
    final World world;
    final EnergyWorldImpl energy;
    private final Path saveFile;

    /** Saved nodes whose type is not registered (their mod is missing). Kept so their data is not lost. */
    private final List<JsonObject> orphans = new ArrayList<>();
    private final Long2BooleanOpenHashMap loadedChunks = new Long2BooleanOpenHashMap();
    private long loadedChunksTick = Long.MIN_VALUE;
    private List<BlockPos> validationQueue = List.of();
    private int validationCursor;
    private long lastSaveNanos = System.nanoTime();
    private volatile boolean closed;

    WorldState(HyFluxService service, World world) {
        this.service = service;
        this.world = world;
        this.energy = new EnergyWorldImpl(world.getName(), this);
        this.saveFile = world.getSavePath().resolve("HyFlux").resolve("energy.json");
    }

    boolean isClosed() {
        return closed;
    }

    // ---- EnergyWorldImpl.Host ----------------------------------------------------------

    @Override
    public boolean isActive(BlockPos pos, String blockTypeId) {
        if (blockTypeId == null || service.config().tickUnloadedChunks) {
            return true;
        }
        long tick = energy.getTick();
        if (tick != loadedChunksTick) {
            loadedChunks.clear();
            loadedChunksTick = tick;
        }
        long index = ChunkUtil.indexChunkFromBlock(pos.x(), pos.z());
        if (loadedChunks.containsKey(index)) {
            return loadedChunks.get(index);
        }
        boolean loaded = ChunkAccess.isLoaded(world, index);
        loadedChunks.put(index, loaded);
        return loaded;
    }

    @Override
    public EnergyNode refreshBlock(EnergyWorldImpl w, BlockPos pos) {
        BlockType blockType = ChunkAccess.blockType(world, pos.x(), pos.y(), pos.z());
        if (blockType == null) {
            return energy.getNode(pos); // Cannot see the block; leave the node as it is.
        }
        String blockId = blockType == null ? null : blockType.getId();
        NodeType binding = blockId == null ? null : service.blocks().getBinding(blockId);
        NodeType current = energy.getNodeType(pos);

        if (current != null && energy.getBoundBlockId(pos) == null) {
            return energy.getNode(pos); // Added through the API, not ours to manage.
        }
        if (binding == null) {
            if (current != null) {
                energy.removeNode(pos, DetachReason.BLOCK_BROKEN);
                service.debug("Removed " + current.id() + " at " + pos + " in " + world.getName() + " (block is now " + blockId + ")");
            }
            return null;
        }
        if (current != null) {
            if (current.equals(binding)) {
                return energy.getNode(pos);
            }
            energy.removeNode(pos, DetachReason.REPLACED);
        }
        EnergyNode node = energy.addBlockNode(pos, binding, BlockEnergyRegistry.baseBlockId(blockId));
        service.debug("Added " + binding.id() + " at " + pos + " in " + world.getName());
        return node;
    }

    @Override
    public void checkThread() {
        if (!closed && !world.isInThread()) {
            throw new IllegalStateException("HyFlux energy world '" + world.getName()
                + "' was modified off its world thread. Wrap the call in world.execute(...).");
        }
    }

    @Override
    public void reportError(String message, Throwable error) {
        service.logError(message, error);
    }

    @Override
    public Collection<EnergyListener> listeners() {
        return service.listeners();
    }

    @Override
    public void setBlockState(BlockPos pos, String state) {
        ChunkAccess.setBlockState(world, pos.x(), pos.y(), pos.z(), state);
    }

    @Override
    public boolean allowDirectTransfer() {
        return service.config().directTransfer;
    }

    // ---- ticking -----------------------------------------------------------------------

    void tick() {
        energy.tick();
        HyFluxConfig config = service.config();
        if (energy.getTick() % config.validateIntervalTicks == 0) {
            validateSome(config.validateBatchSize);
        }
        if (energy.isDirty() && System.nanoTime() - lastSaveNanos >= TimeUnit.SECONDS.toNanos(config.autosaveIntervalSeconds)) {
            saveAsync();
        }
    }

    /** Re-checks a slice of block-bound nodes against the blocks in the world. */
    private void validateSome(int budget) {
        if (validationCursor >= validationQueue.size()) {
            if (energy.getNodeCount() == 0) {
                return;
            }
            validationQueue = new ArrayList<>(energy.getNodePositions());
            validationCursor = 0;
        }
        int end = Math.min(validationQueue.size(), validationCursor + budget);
        for (; validationCursor < end; validationCursor++) {
            BlockPos pos = validationQueue.get(validationCursor);
            if (energy.getBoundBlockId(pos) == null) {
                continue;
            }
            try {
                refreshBlock(energy, pos);
            } catch (Throwable t) {
                service.logError("Validation failed at " + pos + " in " + world.getName(), t);
            }
        }
    }

    // ---- persistence -------------------------------------------------------------------

    void load() {
        JsonObject root;
        try {
            root = WorldSaveFile.read(saveFile);
        } catch (Exception e) {
            Path backup = saveFile.resolveSibling("energy.json.corrupt-" + System.currentTimeMillis());
            service.logError("Could not read " + saveFile + "; moving it to " + backup.getFileName() + " and starting empty", e);
            try {
                Files.move(saveFile, backup);
            } catch (Exception moveError) {
                service.logError("Could not move the unreadable save aside", moveError);
            }
            return;
        }
        if (root == null || !root.has("Nodes") || !root.get("Nodes").isJsonArray()) {
            return;
        }
        int restored = 0;
        for (JsonElement element : root.getAsJsonArray("Nodes")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject saved = element.getAsJsonObject();
            try {
                BlockPos pos = BlockPos.parse(saved.get("Pos").getAsString());
                String typeId = saved.get("Type").getAsString();
                String block = saved.has("Block") && !saved.get("Block").isJsonNull() ? saved.get("Block").getAsString() : null;
                NodeType type = service.nodeTypes().get(typeId);
                if (type == null) {
                    orphans.add(saved);
                    continue;
                }
                NodeData data = WorldSaveFile.fromJson(saved.has("Data") ? saved.getAsJsonObject("Data") : null);
                if (energy.restoreNode(pos, type, block, data) != null) {
                    restored++;
                }
            } catch (Exception e) {
                service.logError("Keeping unreadable saved node as-is: " + saved, e);
                orphans.add(saved);
            }
        }
        energy.clearDirty();
        service.logInfo("World " + world.getName() + ": restored " + restored + " energy nodes in "
            + energy.getNetworks().size() + " networks");
        if (!orphans.isEmpty()) {
            service.logWarning("World " + world.getName() + ": " + orphans.size()
                + " saved nodes belong to node types that are not registered (missing mod?). They are kept but inactive.");
        }
    }

    /** Builds the save file contents. Must run on the world thread. */
    String serialize() {
        JsonObject root = WorldSaveFile.newRoot(service.getVersion());
        JsonArray nodes = root.getAsJsonArray("Nodes");
        energy.forEachNode((pos, type, block, node) -> {
            NodeData data = new NodeData();
            try {
                node.save(data);
            } catch (Throwable t) {
                service.logError("Node " + type.id() + " at " + pos + " threw in save(); saving it without data", t);
                data = new NodeData();
            }
            JsonObject saved = new JsonObject();
            saved.addProperty("Pos", pos.toShortString());
            saved.addProperty("Type", type.id());
            if (block != null) {
                saved.addProperty("Block", block);
            }
            if (!data.isEmpty()) {
                saved.add("Data", WorldSaveFile.toJson(data));
            }
            nodes.add(saved);
        });
        for (JsonObject orphan : orphans) {
            try {
                if (energy.hasNode(BlockPos.parse(orphan.get("Pos").getAsString()))) {
                    continue; // Something new was built there.
                }
            } catch (Exception ignored) {
                // Keep orphans we cannot parse.
            }
            nodes.add(orphan);
        }
        return WorldSaveFile.toJson(root);
    }

    /** Serializes now on the world thread and writes in the background. */
    void saveAsync() {
        String json = serialize();
        energy.clearDirty();
        lastSaveNanos = System.nanoTime();
        service.io().execute(() -> write(json));
    }

    /** Asks the world thread to save; safe from any thread. */
    void requestSave() {
        if (world.isInThread()) {
            saveAsync();
        } else {
            world.execute(this::saveAsync);
        }
    }

    private void write(String json) {
        try {
            if (energy.getNodeCount() == 0 && orphans.isEmpty() && !Files.exists(saveFile)) {
                return;
            }
            WorldSaveFile.writeAtomically(saveFile, json);
        } catch (Exception e) {
            service.logError("Could not write " + saveFile, e);
        }
    }

    /**
     * Saves and detaches every node. Runs on the world thread when it is still alive, and
     * directly otherwise (the world has stopped ticking, so nothing else touches the nodes).
     */
    void close() {
        if (closed) {
            return;
        }
        Runnable task = () -> {
            try {
                write(serialize());
            } finally {
                energy.unloadAll();
            }
        };
        boolean done = false;
        if (world.isInThread()) {
            task.run();
            done = true;
        } else if (world.isAlive()) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            try {
                world.execute(() -> {
                    try {
                        task.run();
                        future.complete(null);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
                future.get(5, TimeUnit.SECONDS);
                done = true;
            } catch (Exception e) {
                service.logWarning("World " + world.getName() + " did not run the energy save in time; saving directly");
            }
        }
        closed = true;
        if (!done) {
            task.run();
        }
    }

    Path saveFile() {
        return saveFile;
    }

    int orphanCount() {
        return orphans.size();
    }
}
