package dev.kate.erd.core.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable representation of a block position in a specific world.
 *
 * <p>This is the primary spatial identifier used throughout the ERD core system.
 * It includes the world UUID to support multi-world scenarios and provides
 * utility methods for coordinate manipulation and chunk calculations.
 *
 * <p>Thread-safety: This class is immutable and therefore thread-safe.
 *
 * @param worldId the UUID of the world containing this position
 * @param x the X coordinate
 * @param y the Y coordinate
 * @param z the Z coordinate
 */
public record BlockPos(UUID worldId, int x, int y, int z) {

    /**
     * Constructs a new BlockPos with validation.
     *
     * @param worldId the world UUID, must not be null
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param z the Z coordinate
     * @throws NullPointerException if worldId is null
     */
    public BlockPos {
        Objects.requireNonNull(worldId, "worldId must not be null");
    }

    /**
     * Creates a new BlockPos offset from this position by the given deltas.
     *
     * @param dx the X offset
     * @param dy the Y offset
     * @param dz the Z offset
     * @return a new BlockPos at the offset position in the same world
     */
    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(worldId, x + dx, y + dy, z + dz);
    }

    /**
     * Creates a new BlockPos adjacent to this position in the given direction.
     *
     * @param direction the direction to offset
     * @return a new BlockPos adjacent in the specified direction
     * @throws NullPointerException if direction is null
     */
    public BlockPos adjacent(Direction direction) {
        Objects.requireNonNull(direction, "direction must not be null");
        return offset(direction.dx(), direction.dy(), direction.dz());
    }

    /**
     * Converts this block position to a chunk key.
     *
     * @return the ChunkKey containing this block position
     */
    public ChunkKey toChunkKey() {
        return new ChunkKey(worldId, x >> 4, z >> 4);
    }

    /**
     * Returns the chunk-local X coordinate (0-15).
     *
     * @return the X coordinate within the chunk
     */
    public int chunkLocalX() {
        return x & 15;
    }

    /**
     * Returns the chunk-local Z coordinate (0-15).
     *
     * @return the Z coordinate within the chunk
     */
    public int chunkLocalZ() {
        return z & 15;
    }

    @Override
    public String toString() {
        return String.format("BlockPos[world=%s, x=%d, y=%d, z=%d]",
            worldId.toString().substring(0, 8), x, y, z);
    }
}
