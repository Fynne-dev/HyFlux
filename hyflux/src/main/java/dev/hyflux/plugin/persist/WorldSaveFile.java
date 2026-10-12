package dev.hyflux.plugin.persist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.hyflux.api.node.NodeData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Reads and writes the per-world energy save, {@code <world save>/HyFlux/energy.json}.
 *
 * <pre>
 * { "Format": 1, "Nodes": [ { "Pos": "x,y,z", "Type": "mod:id", "Block": "Block_Id", "Data": { ... } } ] }
 * </pre>
 */
public final class WorldSaveFile {

    public static final int FORMAT = 1;

    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .serializeSpecialFloatingPointValues()
        .create();

    private WorldSaveFile() {
    }

    public static JsonObject read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        return root != null && root.isJsonObject() ? root.getAsJsonObject() : null;
    }

    public static String toJson(JsonObject root) {
        return GSON.toJson(root);
    }

    /** Writes through a temporary file so a crash mid-write never leaves a truncated save. */
    public static void writeAtomically(Path file, String json) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static JsonObject newRoot(String version) {
        JsonObject root = new JsonObject();
        root.addProperty("Format", FORMAT);
        root.addProperty("HyFluxVersion", version);
        root.add("Nodes", new JsonArray());
        return root;
    }

    public static JsonObject toJson(NodeData data) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Object> e : data.asMap().entrySet()) {
            Object v = e.getValue();
            switch (v) {
                case NodeData child -> obj.add(e.getKey(), toJson(child));
                case Number n -> obj.add(e.getKey(), new JsonPrimitive(n));
                case Boolean b -> obj.add(e.getKey(), new JsonPrimitive(b));
                case String s -> obj.add(e.getKey(), new JsonPrimitive(s));
                default -> {
                    // NodeData only holds the types above.
                }
            }
        }
        return obj;
    }

    public static NodeData fromJson(JsonObject obj) {
        NodeData data = new NodeData();
        if (obj == null) {
            return data;
        }
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            JsonElement v = e.getValue();
            if (v.isJsonObject()) {
                data.putCompound(e.getKey(), fromJson(v.getAsJsonObject()));
            } else if (v.isJsonPrimitive()) {
                JsonPrimitive p = v.getAsJsonPrimitive();
                if (p.isBoolean()) {
                    data.putBoolean(e.getKey(), p.getAsBoolean());
                } else if (p.isString()) {
                    data.putString(e.getKey(), p.getAsString());
                } else if (p.isNumber()) {
                    String text = p.getAsString();
                    if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0
                        || text.equals("NaN") || text.contains("Infinity")) {
                        data.putDouble(e.getKey(), p.getAsDouble());
                    } else {
                        data.putLong(e.getKey(), p.getAsLong());
                    }
                }
            }
        }
        return data;
    }
}
