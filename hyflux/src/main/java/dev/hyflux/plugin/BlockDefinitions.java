package dev.hyflux.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import dev.hyflux.api.node.AbstractEnergyNode;
import dev.hyflux.api.node.BatteryNode;
import dev.hyflux.api.node.CableNode;
import dev.hyflux.api.node.ConsumerNode;
import dev.hyflux.api.node.NodeType;
import dev.hyflux.api.node.ProducerNode;
import dev.hyflux.api.registry.BlockEnergyRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Energy blocks declared in JSON instead of Java. HyFlux reads {@code hyflux.json} from the root
 * of every installed mod jar, plus every {@code *.json} in its own {@code blocks/} data folder.
 * See the README for the format.
 */
final class BlockDefinitions {

    static final String FILE_NAME = "hyflux.json";

    private final HyFluxService service;
    private int defined;

    private BlockDefinitions(HyFluxService service) {
        this.service = service;
    }

    /**
     * Loads every definition file and binds its blocks. Looks in every Java plugin, and in every
     * jar, zip and folder next to HyFlux in the mods folder (content-only mods with no Java).
     * Returns how many blocks were bound.
     */
    static int loadAll(HyFluxService service, Path dataDirectory, Path hyfluxJar) {
        BlockDefinitions loader = new BlockDefinitions(service);
        Map<Path, String> mods = new LinkedHashMap<>();
        for (PluginBase plugin : PluginManager.get().getPlugins()) {
            if (plugin instanceof JavaPlugin javaPlugin && javaPlugin.getFile() != null) {
                mods.putIfAbsent(normalize(javaPlugin.getFile()), javaPlugin.getName());
            }
        }
        Path modsFolder = hyfluxJar == null ? null : hyfluxJar.toAbsolutePath().getParent();
        if (modsFolder != null && Files.isDirectory(modsFolder)) {
            try (Stream<Path> entries = Files.list(modsFolder)) {
                entries.filter(f -> Files.isDirectory(f) || f.toString().endsWith(".jar") || f.toString().endsWith(".zip"))
                    .sorted()
                    .forEach(f -> mods.putIfAbsent(normalize(f), f.getFileName().toString()));
            } catch (Exception e) {
                service.logError("Could not list " + modsFolder, e);
            }
        }
        mods.forEach(loader::readMod);
        Path folder = dataDirectory.resolve("blocks");
        if (Files.isDirectory(folder)) {
            try (Stream<Path> files = Files.list(folder)) {
                files.filter(f -> f.toString().endsWith(".json")).sorted()
                    .forEach(f -> loader.readFile(f, "blocks/" + f.getFileName()));
            } catch (Exception e) {
                service.logError("Could not list " + folder, e);
            }
        }
        return loader.defined;
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private void readMod(Path file, String name) {
        String source = name + "/" + FILE_NAME;
        try {
            if (Files.isDirectory(file)) {
                readFile(file.resolve(FILE_NAME), source);
            } else if (Files.isRegularFile(file)) {
                try (FileSystem jar = FileSystems.newFileSystem(file)) {
                    readFile(jar.getPath("/" + FILE_NAME), source);
                }
            }
        } catch (Exception e) {
            service.logError("Could not open " + file + " to look for " + FILE_NAME, e);
        }
    }

    private void readFile(Path file, String source) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        JsonObject blocks;
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject() || !root.getAsJsonObject().has("Blocks") || !root.getAsJsonObject().get("Blocks").isJsonObject()) {
                service.logWarning(source + ": expected an object with a \"Blocks\" object; skipping");
                return;
            }
            blocks = root.getAsJsonObject().getAsJsonObject("Blocks");
        } catch (Exception e) {
            service.logError(source + " is not valid JSON; skipping", e);
            return;
        }
        for (Map.Entry<String, JsonElement> entry : blocks.entrySet()) {
            try {
                define(entry.getKey(), entry.getValue().getAsJsonObject(), source);
            } catch (Exception e) {
                service.logWarning(source + ": block " + entry.getKey() + " skipped: " + e.getMessage());
            }
        }
    }

    private void define(String blockId, JsonObject spec, String source) {
        if (service.blocks().isBound(blockId)) {
            service.logWarning(source + ": " + blockId + " is already bound (by code or another file); skipping");
            return;
        }
        String kind = text(spec, "Type", "").toLowerCase(Locale.ROOT);
        int priority = (int) number(spec, "Priority", 0);
        String activeState = text(spec, "ActiveState", null);
        Supplier<AbstractEnergyNode> factory = switch (kind) {
            case "generator", "producer" -> {
                long capacity = positive(spec, "Capacity", 1_000);
                long output = number(spec, "Output", capacity);
                long generation = number(spec, "Generation", 0);
                yield () -> new ProducerNode(capacity, output, generation);
            }
            case "machine", "consumer" -> {
                long capacity = positive(spec, "Capacity", 1_000);
                long input = number(spec, "Input", capacity);
                long usage = number(spec, "Usage", 0);
                yield () -> new ConsumerNode(capacity, input, usage);
            }
            case "battery", "storage" -> {
                long capacity = positive(spec, "Capacity", 100_000);
                long rate = number(spec, "Rate", capacity);
                long input = number(spec, "Input", rate);
                long output = number(spec, "Output", rate);
                yield () -> new BatteryNode(capacity, input, output);
            }
            case "cable", "conduit" -> {
                long rate = positive(spec, "Rate", -1);
                yield () -> new CableNode(rate);
            }
            default -> throw new IllegalArgumentException(
                "\"Type\" must be Generator, Machine, Battery or Cable, got \"" + kind + "\"");
        };
        factory.get(); // Fail now, naming the file, if the numbers are invalid.
        NodeType type = NodeType.of(BlockEnergyRegistry.defaultTypeId(blockId), () -> {
            AbstractEnergyNode node = factory.get();
            node.setPriority(priority);
            node.setActiveState(activeState);
            return node;
        });
        service.blocks().bind(blockId, type);
        defined++;
        service.debug(source + ": bound " + blockId + " as " + kind);
    }

    private static String text(JsonObject spec, String key, String fallback) {
        JsonElement value = spec.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static long number(JsonObject spec, String key, long fallback) {
        JsonElement value = spec.get(key);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        long n = value.getAsLong();
        if (n < 0) {
            throw new IllegalArgumentException("\"" + key + "\" must not be negative");
        }
        return n;
    }

    private static long positive(JsonObject spec, String key, long fallback) {
        long n = number(spec, key, fallback);
        if (n <= 0) {
            throw new IllegalArgumentException("\"" + key + "\" must be greater than 0");
        }
        return n;
    }
}
