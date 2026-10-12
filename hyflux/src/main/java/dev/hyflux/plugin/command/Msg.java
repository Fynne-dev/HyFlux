package dev.hyflux.plugin.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;

/** Chat formatting for HyFlux commands. */
final class Msg {

    static final String GOLD = "#FFB000";
    static final String GREEN = "#55FF55";
    static final String RED = "#FF5555";
    static final String GRAY = "#AAAAAA";
    static final String WHITE = "#FFFFFF";
    static final String AQUA = "#55FFFF";

    private Msg() {
    }

    static void header(CommandContext ctx, String text) {
        ctx.sendMessage(Message.raw("== " + text + " ==").color(GOLD).bold(true));
    }

    static void line(CommandContext ctx, String text) {
        ctx.sendMessage(Message.raw(text).color(WHITE));
    }

    static void field(CommandContext ctx, String label, String value) {
        ctx.sendMessage(Message.join(Message.raw(label + ": ").color(GRAY), Message.raw(value).color(WHITE)));
    }

    static void ok(CommandContext ctx, String text) {
        ctx.sendMessage(Message.raw(text).color(GREEN));
    }

    static void error(CommandContext ctx, String text) {
        ctx.sendMessage(Message.raw(text).color(RED));
    }

    static void note(CommandContext ctx, String text) {
        ctx.sendMessage(Message.raw(text).color(GRAY));
    }
}
