package dev.hyflux.api.math;

/**
 * The six faces of a block. Energy connections are made across faces: two nodes are
 * connected when each accepts a connection on the face that touches the other.
 */
public enum Direction {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    private static final Direction[] VALUES = values();

    private final int dx;
    private final int dy;
    private final int dz;

    Direction(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public int dz() {
        return dz;
    }

    public Direction opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    public boolean isHorizontal() {
        return dy == 0;
    }

    /** Cached copy of {@link #values()} that callers must not modify. */
    public static Direction[] all() {
        return VALUES;
    }

    /** Returns the direction pointing from {@code from} to the adjacent {@code to}, or null if not adjacent. */
    public static Direction between(BlockPos from, BlockPos to) {
        int x = to.x() - from.x();
        int y = to.y() - from.y();
        int z = to.z() - from.z();
        for (Direction d : VALUES) {
            if (d.dx == x && d.dy == y && d.dz == z) {
                return d;
            }
        }
        return null;
    }
}
