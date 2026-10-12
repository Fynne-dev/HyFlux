package dev.hyflux.api.math;

/**
 * An immutable integer block position. Independent of Hytale's vector types so the
 * energy API can be used (and unit tested) without a running server.
 */
public record BlockPos(int x, int y, int z) implements Comparable<BlockPos> {

    public static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    public static BlockPos of(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    public BlockPos offset(Direction direction) {
        return new BlockPos(x + direction.dx(), y + direction.dy(), z + direction.dz());
    }

    public BlockPos offset(Direction direction, int distance) {
        return new BlockPos(x + direction.dx() * distance, y + direction.dy() * distance, z + direction.dz() * distance);
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    public int manhattanDistance(BlockPos other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z);
    }

    public long distanceSquared(BlockPos other) {
        long ddx = (long) x - other.x;
        long ddy = (long) y - other.y;
        long ddz = (long) z - other.z;
        return ddx * ddx + ddy * ddy + ddz * ddz;
    }

    /** Compact {@code "x,y,z"} form, the same format {@link #parse(String)} reads. */
    public String toShortString() {
        return x + "," + y + "," + z;
    }

    public static BlockPos parse(String text) {
        String[] parts = text.trim().split("\s*,\s*");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected x,y,z but got: " + text);
        }
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    @Override
    public int compareTo(BlockPos o) {
        int c = Integer.compare(y, o.y);
        if (c != 0) return c;
        c = Integer.compare(z, o.z);
        if (c != 0) return c;
        return Integer.compare(x, o.x);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
