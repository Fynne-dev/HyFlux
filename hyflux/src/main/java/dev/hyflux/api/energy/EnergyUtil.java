package dev.hyflux.api.energy;

import java.util.Locale;

/** Small helpers for working with energy amounts. */
public final class EnergyUtil {

    /** Display name of the HyFlux energy unit. */
    public static final String UNIT = "HF";

    private EnergyUtil() {
    }

    /** {@code a + b} that clamps to {@link Long#MAX_VALUE} instead of overflowing. Negative inputs are treated as 0. */
    public static long saturatedAdd(long a, long b) {
        a = Math.max(0, a);
        b = Math.max(0, b);
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r;
    }

    /**
     * Moves up to {@code max} energy from {@code from} to {@code to}, the way two adjacent
     * blocks exchange energy without a network. Returns the amount moved.
     */
    public static long transfer(IEnergyStorage from, IEnergyStorage to, long max) {
        if (from == null || to == null || max <= 0 || !from.canExtract() || !to.canReceive()) {
            return 0;
        }
        long offered = from.extractEnergy(max, true);
        if (offered <= 0) {
            return 0;
        }
        long accepted = to.receiveEnergy(offered, true);
        if (accepted <= 0) {
            return 0;
        }
        long extracted = from.extractEnergy(accepted, false);
        long received = to.receiveEnergy(extracted, false);
        if (received < extracted) {
            // The receiver accepted less than it promised, so hand the rest back to the sender.
            from.receiveEnergy(extracted - received, false);
        }
        return received;
    }

    /** Formats an amount like {@code 12.5k HF} or {@code 3.20M HF}. */
    public static String format(long amount) {
        return formatNumber(amount) + " " + UNIT;
    }

    /** Formats a per-tick rate like {@code 250 HF/t}. */
    public static String formatRate(long perTick) {
        return formatNumber(perTick) + " " + UNIT + "/t";
    }

    public static String formatNumber(long amount) {
        if (amount == Long.MAX_VALUE) {
            return "unlimited";
        }
        long abs = Math.abs(amount);
        if (abs < 10_000) {
            return Long.toString(amount);
        }
        String[] suffixes = {"k", "M", "G", "T", "P", "E"};
        double value = amount;
        int i = -1;
        while (Math.abs(value) >= 1000 && i < suffixes.length - 1) {
            value /= 1000.0;
            i++;
        }
        String pattern = Math.abs(value) >= 100 ? "%.0f%s" : Math.abs(value) >= 10 ? "%.1f%s" : "%.2f%s";
        return String.format(Locale.ROOT, pattern, value, suffixes[i]);
    }

    /** A 10-segment text bar such as {@code [|||||.....]}. */
    public static String bar(IEnergyStorage storage) {
        int filled = (int) Math.round(storage.getFillRatio() * 10);
        return "[" + "|".repeat(filled) + ".".repeat(10 - filled) + "]";
    }
}
