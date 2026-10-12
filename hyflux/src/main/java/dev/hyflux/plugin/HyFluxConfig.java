package dev.hyflux.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server-side settings, stored in {@code mods/HyFlux_HyFlux/config.json} (the plugin data directory). */
public final class HyFluxConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Seconds between autosaves of worlds whose energy state changed. */
    @SerializedName("AutosaveIntervalSeconds")
    public int autosaveIntervalSeconds = 60;

    /** Ticks between block validation passes, which drop nodes whose block disappeared without an event. */
    @SerializedName("ValidateIntervalTicks")
    public int validateIntervalTicks = 20;

    /** Nodes checked per validation pass. */
    @SerializedName("ValidateBatchSize")
    public int validateBatchSize = 64;

    /**
     * If true, generators and batteries push energy into the machines and batteries they touch,
     * without a cable (machines never pass energy on). If false, energy only moves through
     * cables. Applies to worlds loaded after the change.
     */
    @SerializedName("DirectTransfer")
    public boolean directTransfer = true;

    /** If true, nodes in unloaded chunks keep running. If false they freeze until their chunk loads. */
    @SerializedName("TickUnloadedChunks")
    public boolean tickUnloadedChunks = false;

    /** Largest radius accepted by {@code /hyflux scan}. */
    @SerializedName("MaxScanRadius")
    public int maxScanRadius = 32;

    /** Logs node creation and removal. */
    @SerializedName("DebugLogging")
    public boolean debugLogging = false;

    public static HyFluxConfig loadOrCreate(Path file) throws IOException {
        HyFluxConfig config = null;
        if (Files.isRegularFile(file)) {
            config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), HyFluxConfig.class);
        }
        if (config == null) {
            config = new HyFluxConfig();
        }
        config.sanitize();
        // Write back so new options appear in existing files.
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(config), StandardCharsets.UTF_8);
        return config;
    }

    private void sanitize() {
        autosaveIntervalSeconds = Math.max(5, autosaveIntervalSeconds);
        validateIntervalTicks = Math.max(1, validateIntervalTicks);
        validateBatchSize = Math.max(1, validateBatchSize);
        maxScanRadius = Math.max(1, Math.min(64, maxScanRadius));
    }
}
