package dev.hyflux.plugin.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.hyflux.plugin.HyFluxService;

/** {@code /hyflux} (alias {@code /hf}): inspection and maintenance tools for energy networks. */
public final class HyFluxCommand extends AbstractCommandCollection {

    public HyFluxCommand(HyFluxService service) {
        super("hyflux", "HyFlux energy network tools");
        addAliases("hf");
        addSubCommand(new InfoCommand(service));
        addSubCommand(new ProbeCommand(service));
        addSubCommand(new NetworksCommand(service));
        addSubCommand(new ScanCommand(service));
        addSubCommand(new SaveCommand(service));
        addSubCommand(new SelfTestCommand());
    }
}
