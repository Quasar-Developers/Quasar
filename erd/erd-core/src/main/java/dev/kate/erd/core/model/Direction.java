package dev.kate.erd.core.model;

/**
 * Represents the six cardinal directions in 3D space.
 *
 * <p>Used for adjacency calculations in network topology operations.
 *
 * <p>Thread-safety: Enum values are inherently thread-safe.
 */
public enum Direction {
    /** Positive X direction (East) */
    EAST(1, 0, 0),
    /** Negative X direction (West) */
    WEST(-1, 0, 0),
    /** Positive Y direction (Up) */
    UP(0, 1, 0),
    /** Negative Y direction (Down) */
    DOWN(0, -1, 0),
    /** Positive Z direction (South) */
    SOUTH(0, 0, 1),
    /** Negative Z direction (North) */
    NORTH(0, 0, -1);

    private final int dx;
    private final int dy;
    private final int dz;

    Direction(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    /**
     * @return the X offset for this direction
     */
    public int dx() {
        return dx;
    }

    /**
     * @return the Y offset for this direction
     */
    public int dy() {
        return dy;
    }

    /**
     * @return the Z offset for this direction
     */
    public int dz() {
        return dz;
    }

    /**
     * Returns the opposite direction.
     *
     * @return the direction opposite to this one
     */
    public Direction opposite() {
        return switch (this) {
            case EAST -> WEST;
            case WEST -> EAST;
            case UP -> DOWN;
            case DOWN -> UP;
            case SOUTH -> NORTH;
            case NORTH -> SOUTH;
        };
    }

    /**
     * All six cardinal directions for iteration.
     */
    public static final Direction[] ALL = values();
}
