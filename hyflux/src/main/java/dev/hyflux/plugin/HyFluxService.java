package dev.hyflux.plugin;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hyflux.api.HyFlux;
import dev.hyflux.api.HyFluxApi;
import dev.hyflux.api.event.EnergyListener;
import dev.hyflux.api.math.BlockPos;
import dev.hyflux.api.network.EnergyWorld;
import dev.hyflux.core.BlockEnergyRegistryImpl;
import dev.hyflux.core.EnergyWorldImpl;
import dev.hyflux.core.ItemEnergyRegistryImpl;
import dev.hyflux.core.NodeTypeRegistryImpl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** The {@link HyFluxApi} implementation installed by {@link HyFluxPlugin}. */
public final class HyFluxService implements HyFluxApi {

    private final HyFluxPlugin plugin;
    private final NodeTypeRegistryImpl nodeTypes = new NodeTypeRegistryImpl();
    private final BlockEnergyRegistryImpl blocks = new BlockEnergyRegistryImpl(nodeTypes);
    private final ItemEnergyRegistryImpl items = new ItemEnergyRegistryImpl();
    private final List<EnergyListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, WorldState> worlds = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "HyFlux-IO");
        thread.setDaemon(true);
        return thread;
    });
    private volatile HyFluxConfig config = new HyFluxConfig();
    private volatile boolean bindingsChecked;
    private volatile boolean shuttingDown;

    HyFluxService(HyFluxPlugin plugin) {
        this.plugin = plugin;
    }

    void loadConfig(Path file) {
        try {
            config = HyFluxConfig.loadOrCreate(file);
        } catch (Exception e) {
            logError("Could not read " + file + "; using defaults", e);
            config = new HyFluxConfig();
        }
    }

    public HyFluxConfig config() {
        return config;
    }

    ExecutorService io() {
        return io;
    }

    List<EnergyListener> listeners() {
        return listeners;
    }

    // ---- HyFluxApi ---------------------------------------------------------------------

    @Override
    public String getVersion() {
        return String.valueOf(plugin.getManifest().getVersion());
    }

    @Override
    public int getApiVersion() {
        return HyFlux.API_VERSION;
    }

    @Override
    public NodeTypeRegistryImpl nodeTypes() {
        return nodeTypes;
    }

    @Override
    public BlockEnergyRegistryImpl blocks() {
        return blocks;
    }

    @Override
    public ItemEnergyRegistryImpl items() {
        return items;
    }

    @Override
    public EnergyWorld getWorld(World world) {
        return state(world).energy;
    }

    @Override
    public EnergyWorld getWorld(String worldName) {
        WorldState state = worldName == null ? null : worlds.get(worldName);
        return state == null ? null : state.energy;
    }

    @Override
    public Collection<? extends EnergyWorld> getWorlds() {
        List<EnergyWorld> result = new ArrayList<>();
        worlds.values().forEach(s -> result.add(s.energy));
        return result;
    }

    @Override
    public World getHytaleWorld(EnergyWorld world) {
        if (world instanceof EnergyWorldImpl impl && impl.host() instanceof WorldState state) {
            return state.world;
        }
        return null;
    }

    @Override
    public String getBlockId(World world, BlockPos pos) {
        var type = ChunkAccess.blockType(world, pos.x(), pos.y(), pos.z());
        return type == null ? null : type.getId();
    }

    @Override
    public boolean placeBlock(World world, BlockPos pos, String blockTypeId) {
        if (!ChunkAccess.setBlock(world, pos.x(), pos.y(), pos.z(), blockTypeId)) {
            return false;
        }
        state(world).energy.refreshBlock(pos);
        return true;
    }

    @Override
    public boolean setBlockState(World world, BlockPos pos, String state) {
        return ChunkAccess.setBlockState(world, pos.x(), pos.y(), pos.z(), state);
    }

    @Override
    public void addListener(EnergyListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void removeListener(EnergyListener listener) {
        listeners.remove(listener);
    }

    // ---- world bookkeeping -------------------------------------------------------------

    /** The state of a world, created (and its save loaded) on first use on the world thread. */
    WorldState state(World world) {
        Objects.requireNonNull(world, "world");
        WorldState state = worlds.get(world.getName());
        if (state != null && state.world == world && !state.isClosed()) {
            return state;
        }
        if (shuttingDown) {
            throw new IllegalStateException("HyFlux is shutting down");
        }
        if (!world.isInThread()) {
            throw new IllegalStateException("HyFlux.world(" + world.getName()
                + ") was first called off the world thread. Wrap the call in world.execute(...).");
        }
        checkBindingsOnce();
        WorldState created = new WorldState(this, world);
        WorldState previous = worlds.put(world.getName(), created);
        if (previous != null && previous != created) {
            previous.close();
        }
        created.load();
        return created;
    }

    /** Ticks a world. Called by {@link dev.hyflux.plugin.system.EnergyTickSystem} on the world thread. */
    public void tickWorld(World world) {
        if (shuttingDown || !world.isAlive()) {
            return;
        }
        WorldState state = worlds.get(world.getName());
        if (state == null || state.world != world) {
            state = state(world);
        }
        if (!state.isClosed()) {
            state.tick();
        }
    }

    /** Called when a block may have changed; re-checks it on the world thread shortly after. */
    public void scheduleBlockCheck(World world, BlockPos pos) {
        if (shuttingDown || blocks.all().isEmpty()) {
            return;
        }
        world.execute(() -> {
            if (shuttingDown || !world.isAlive()) {
                return;
            }
            try {
                WorldState state = state(world);
                if (!state.isClosed()) {
                    state.energy.refreshBlock(pos);
                }
            } catch (Throwable t) {
                logError("Block check at " + pos + " in " + world.getName() + " failed", t);
            }
        });
    }

    void closeWorld(World world) {
        WorldState state = worlds.get(world.getName());
        if (state != null && state.world == world) {
            worlds.remove(world.getName(), state);
            state.close();
            logInfo("Saved and unloaded energy world " + world.getName());
        }
    }

    /** Requests a save of every loaded energy world. Safe from any thread. */
    public void saveAll() {
        worlds.values().forEach(WorldState::requestSave);
    }

    /** Number of saved nodes in a world whose node type is not registered. */
    public int orphanCount(String worldName) {
        WorldState state = worlds.get(worldName);
        return state == null ? 0 : state.orphanCount();
    }

    /** Result of {@link #scan}. */
    public record ScanResult(long checked, int added, int removed, int nodesAfter) {
    }

    /**
     * Re-checks every block in a cube around {@code center} against the block bindings,
     * creating missing nodes and removing stale ones. Unloaded chunks are skipped.
     * Must be called on the world thread.
     */
    public ScanResult scan(World world, BlockPos center, int radius) {
        WorldState state = state(world);
        int r = Math.max(0, Math.min(radius, config.maxScanRadius));
        int minY = Math.max(ChunkAccess.MIN_Y, center.y() - r);
        int maxY = Math.min(ChunkAccess.MAX_Y, center.y() + r);
        long checked = 0;
        int added = 0;
        int removed = 0;
        for (int x = center.x() - r; x <= center.x() + r; x++) {
            for (int z = center.z() - r; z <= center.z() + r; z++) {
                if (!ChunkAccess.isLoaded(world, x, z)) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    boolean had = state.energy.hasNode(pos);
                    boolean has = state.refreshBlock(state.energy, pos) != null;
                    checked++;
                    if (has && !had) {
                        added++;
                    } else if (had && !has) {
                        removed++;
                    }
                }
            }
        }
        return new ScanResult(checked, added, removed, state.energy.getNodeCount());
    }

    void shutdown() {
        shuttingDown = true;
        for (WorldState state : new ArrayList<>(worlds.values())) {
            try {
                state.close();
            } catch (Throwable t) {
                logError("Failed to save energy world " + state.world.getName(), t);
            }
        }
        worlds.clear();
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                logWarning("Timed out waiting for energy saves to finish");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Warns once about bindings to block ids that do not exist (usually a typo). */
    private void checkBindingsOnce() {
        if (bindingsChecked) {
            return;
        }
        bindingsChecked = true;
        try {
            int fromFiles = BlockDefinitions.loadAll(this, plugin.getDataDirectory(), plugin.getFile());
            if (fromFiles > 0) {
                logInfo("Loaded " + fromFiles + " energy blocks from " + BlockDefinitions.FILE_NAME + " files");
            }
        } catch (Throwable t) {
            logError("Could not load " + BlockDefinitions.FILE_NAME + " files", t);
        }
        try {
            var assets = BlockType.getAssetMap();
            for (var binding : blocks.all().entrySet()) {
                if (assets.getAsset(binding.getKey()) == null) {
                    logWarning("Block type '" + binding.getKey() + "' is bound to " + binding.getValue().id()
                        + " but no such block exists. Check the id in your asset pack.");
                }
            }
            logInfo(nodeTypes.all().size() + " node types, " + blocks.all().size() + " bound blocks, "
                + items.all().size() + " energy items registered");
        } catch (Throwable t) {
            logError("Could not check block bindings", t);
        }
    }

    // ---- logging -----------------------------------------------------------------------

    void logInfo(String message) {
        plugin.getLogger().at(Level.INFO).log(message);
    }

    void logWarning(String message) {
        plugin.getLogger().at(Level.WARNING).log(message);
    }

    void logError(String message, Throwable error) {
        if (error == null) {
            plugin.getLogger().at(Level.SEVERE).log(message);
        } else {
            plugin.getLogger().at(Level.SEVERE).withCause(error).log(message);
        }
    }

    void debug(String message) {
        if (config.debugLogging) {
            logInfo("[debug] " + message);
        }
    }
}
