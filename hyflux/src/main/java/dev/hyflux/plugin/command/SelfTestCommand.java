package dev.hyflux.plugin.command;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import dev.hyflux.core.SelfTest;

import java.util.List;

/** {@code /hyflux selftest}: runs the energy engine against scripted networks in a scratch world. */
final class SelfTestCommand extends CommandBase {

    SelfTestCommand() {
        super("selftest", "Runs the HyFlux engine self-test in a scratch world");
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        List<SelfTest.Check> checks = SelfTest.run();
        long passed = checks.stream().filter(SelfTest.Check::passed).count();
        Msg.header(ctx, "HyFlux self-test: " + passed + "/" + checks.size() + " passed");
        for (SelfTest.Check check : checks) {
            String text = (check.passed() ? "PASS " : "FAIL ") + check.name() + " - " + check.detail();
            if (check.passed()) {
                Msg.ok(ctx, text);
            } else {
                Msg.error(ctx, text);
            }
        }
    }
}
