package dev.hyflux.api.node;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A small key/value document used to save node state between server restarts.
 * Values are longs, doubles, booleans, strings or nested {@code NodeData}.
 * HyFlux stores it as JSON in the world save folder.
 *
 * <pre>{@code
 * public void save(NodeData data) {
 *     storage.save(data);
 *     data.putInt("BurnTime", burnTime);
 * }
 * }</pre>
 */
public final class NodeData {

    private final Map<String, Object> values = new LinkedHashMap<>();

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public void remove(String key) {
        values.remove(key);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public NodeData putLong(String key, long value) {
        values.put(key, value);
        return this;
    }

    public NodeData putInt(String key, int value) {
        values.put(key, (long) value);
        return this;
    }

    public NodeData putDouble(String key, double value) {
        values.put(key, value);
        return this;
    }

    public NodeData putBoolean(String key, boolean value) {
        values.put(key, value);
        return this;
    }

    public NodeData putString(String key, String value) {
        return putRaw(key, value);
    }

    public NodeData putCompound(String key, NodeData value) {
        return putRaw(key, value);
    }

    public long getLong(String key, long fallback) {
        return values.get(key) instanceof Number n ? n.longValue() : fallback;
    }

    public int getInt(String key, int fallback) {
        if (values.get(key) instanceof Number n) {
            return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, n.longValue()));
        }
        return fallback;
    }

    public double getDouble(String key, double fallback) {
        return values.get(key) instanceof Number n ? n.doubleValue() : fallback;
    }

    public boolean getBoolean(String key, boolean fallback) {
        return values.get(key) instanceof Boolean b ? b : fallback;
    }

    public String getString(String key, String fallback) {
        return values.get(key) instanceof String s ? s : fallback;
    }

    /** Returns the nested document, or an empty detached one if absent. */
    public NodeData getCompound(String key) {
        return values.get(key) instanceof NodeData d ? d : new NodeData();
    }

    /** Returns the nested document, creating and storing it if absent. */
    public NodeData getOrCreateCompound(String key) {
        if (values.get(key) instanceof NodeData d) {
            return d;
        }
        NodeData d = new NodeData();
        values.put(key, d);
        return d;
    }

    /** Read-only raw view used by the persistence layer. Values are Long, Double, Boolean, String or NodeData. */
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * Stores a raw value. Integral numbers become longs and other numbers doubles;
     * null removes the key. Unsupported types are rejected.
     */
    public NodeData putRaw(String key, Object value) {
        switch (value) {
            case null -> values.remove(key);
            case Long l -> values.put(key, l);
            case Integer i -> values.put(key, (long) i);
            case Short s -> values.put(key, (long) s);
            case Byte b -> values.put(key, (long) b);
            case Number n -> values.put(key, n.doubleValue());
            case Boolean b -> values.put(key, b);
            case String s -> values.put(key, s);
            case NodeData d -> values.put(key, d);
            default -> throw new IllegalArgumentException("Unsupported NodeData value: " + value.getClass().getName());
        }
        return this;
    }

    public NodeData copy() {
        NodeData copy = new NodeData();
        values.forEach((k, v) -> copy.values.put(k, v instanceof NodeData d ? d.copy() : v));
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NodeData other && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
