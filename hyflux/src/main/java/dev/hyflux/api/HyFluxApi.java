package dev.hyflux.api;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hyflux.api.event.EnergyListener;
import dev.hyflux.api.network.EnergyWorld;
import dev.hyflux.api.registry.BlockEnergyRegistry;
import dev.hyflux.api.registry.ItemEnergyRegistry;
import dev.hyflux.api.registry.NodeTypeRegistry;

import java.util.Collection;

/** The HyFlux service. Reach it through the static helpers on {@link HyFlux}. */
public interface HyFluxApi {

    /** HyFlux version string, such as {@code 1.0.0}. */
    String getVersion();

    /** Version of this API surface. Bumped on breaking changes. */
    int getApiVersion();

    NodeTypeRegistry nodeTypes();

    BlockEnergyRegistry blocks();

    ItemEnergyRegistry items();

    /** The energy world of a Hytale world, loading its saved nodes on first use. Call on the world thread. */
    EnergyWorld getWorld(World world);

    /** The energy world for a world name if it is loaded, otherwise null. */
    EnergyWorld getWorld(String worldName);

    /** All loaded energy worlds. */
    Collection<? extends EnergyWorld> getWorlds();

    /** The Hytale world behind an energy world, or null if it is not backed by one. */
    World getHytaleWorld(EnergyWorld world);

    void addListener(EnergyListener listener);

    void removeListener(EnergyListener listener);
}
