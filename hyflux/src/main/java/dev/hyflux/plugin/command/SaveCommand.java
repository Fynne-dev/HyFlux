package dev.hyflux.plugin.command;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import dev.hyflux.plugin.HyFluxService;

/** {@code /hyflux save}: writes every energy world to disk now. */
final class SaveCommand extends CommandBase {

    private final HyFluxService service;

    SaveCommand(HyFluxService service) {
        super("save", "Saves all energy networks now");
        this.service = service;
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        service.saveAll();
        Msg.ok(ctx, "Saving " + service.getWorlds().size() + " energy worlds.");
    }
}
