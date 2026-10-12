package dev.hyflux.api;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hyflux.api.event.EnergyListener;
import dev.hyflux.api.network.EnergyWorld;
import dev.hyflux.api.node.NodeContext;
import dev.hyflux.api.registry.BlockEnergyRegistry;
import dev.hyflux.api.registry.ItemEnergyRegistry;
import dev.hyflux.api.registry.NodeTypeRegistry;

import java.util.Objects;

/**
 * Entry point of the HyFlux energy API.
 *
 * <p>Add {@code "HyFlux:HyFlux": "*"} to the {@code Dependencies} of your
 * {@code manifest.json} so HyFlux loads first, then register your content in your
 * plugin {@code setup()}:
 *
 * <pre>{@code
 * @Override
 * protected void setup() {
 *     HyFlux.blocks().bind("MyMod_Generator", () -> new ProducerNode(10_000, 200, 40));
 *     HyFlux.blocks().bind("MyMod_Cable", () -> new CableNode(512));
 *     HyFlux.blocks().bind("MyMod_Battery", () -> new BatteryNode(1_000_000, 1_000));
 *     HyFlux.blocks().bind("MyMod_Grinder", GrinderNode::new);
 * }
 * }</pre>
 */
public final class HyFlux {

    /** Plugin identifier to put in your manifest {@code Dependencies}. */
    public static final String PLUGIN_ID = "HyFlux:HyFlux";

    /** Current {@link HyFluxApi#getApiVersion()}. */
    public static final int API_VERSION = 1;

    private static volatile HyFluxApi instance;

    private HyFlux() {
    }

    /** The HyFlux service. Throws if the HyFlux plugin has not been loaded. */
    public static HyFluxApi api() {
        HyFluxApi api = instance;
        if (api == null) {
            throw new IllegalStateException("HyFlux is not loaded. Add \"" + PLUGIN_ID
                + "\": \"*\" to the Dependencies of your manifest.json and put HyFlux in the Mods folder.");
        }
        return api;
    }

    /** True once the HyFlux plugin has started. Use it to make HyFlux an optional dependency. */
    public static boolean isLoaded() {
        return instance != null;
    }

    public static NodeTypeRegistry nodeTypes() {
        return api().nodeTypes();
    }

    public static BlockEnergyRegistry blocks() {
        return api().blocks();
    }

    public static ItemEnergyRegistry items() {
        return api().items();
    }

    /** The energy world of a Hytale world. Call on that world thread. */
    public static EnergyWorld world(World world) {
        return api().getWorld(world);
    }

    /** The Hytale world behind an energy world, or null. */
    public static World hytaleWorld(EnergyWorld world) {
        return api().getHytaleWorld(world);
    }

    /** The Hytale world a node lives in, or null. */
    public static World hytaleWorld(NodeContext context) {
        return api().getHytaleWorld(context.world());
    }

    public static void addListener(EnergyListener listener) {
        api().addListener(listener);
    }

    public static void removeListener(EnergyListener listener) {
        api().removeListener(listener);
    }

    /**
     * Installs the service. Called once by the HyFlux plugin; mods must not call it.
     *
     * @throws IllegalStateException if already installed
     */
    public static synchronized void install(HyFluxApi api) {
        if (instance != null && api != null) {
            throw new IllegalStateException("HyFlux API is already installed");
        }
        instance = Objects.requireNonNull(api, "api");
    }

    /** Removes the service on plugin shutdown. Mods must not call it. */
    public static synchronized void uninstall(HyFluxApi api) {
        if (instance == api) {
            instance = null;
        }
    }
}
