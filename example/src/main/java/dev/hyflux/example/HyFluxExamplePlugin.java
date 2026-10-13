package dev.hyflux.example;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hyflux.api.HyFlux;

/**
 * Example mod built on HyFlux. The cable, battery and lamp need no Java at all: they are
 * declared in {@code hyflux.json} at the root of this jar. Only the generator, which has its
 * own logic, is bound in code.
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
        HyFlux.blocks().bind(GENERATOR, FluxGeneratorNode.TYPE);
        getCommandRegistry().registerCommand(new DemoCommand());
    }
}
