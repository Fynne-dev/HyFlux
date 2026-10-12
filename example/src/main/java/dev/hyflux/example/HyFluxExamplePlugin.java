package dev.hyflux.example;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hyflux.api.HyFlux;
import dev.hyflux.api.node.BatteryNode;
import dev.hyflux.api.node.CableNode;

/**
 * Example mod built on HyFlux. Everything energy-related is one line per block in
 * {@link #setup()}: HyFlux creates the node when the block is placed, connects it to its
 * neighbours, ticks it, saves it and removes it when the block is broken.
 */
public final class HyFluxExamplePlugin extends JavaPlugin {

    public static final String GENERATOR = "HyFlux_Example_Generator";
    public static final String CABLE = "HyFlux_Example_Cable";
    public static final String BATTERY = "HyFlux_Example_Battery";
    public static final String LAMP = "HyFlux_Example_Lamp";

    public HyFluxExamplePlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // Custom node classes, with their own stable node type ids.
        HyFlux.blocks().bind(GENERATOR, FluxGeneratorNode.TYPE);
        HyFlux.blocks().bind(LAMP, LampNode.TYPE);

        // Stock node classes need no subclass at all.
        HyFlux.blocks().bind(CABLE, () -> new CableNode(256));
        HyFlux.blocks().bind(BATTERY, () -> new BatteryNode(100_000, 500));

        getCommandRegistry().registerCommand(new DemoCommand());
    }
}
