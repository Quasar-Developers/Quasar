package dev.kate.erd.core.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable representation of a chunk's coordinates in a specific world.
 *
 * <p>A chunk is a 16x16 column of blocks extending from the world's minimum
 * to maximum Y level. ChunkKey is used for chunk-level operations such as
 * load/unload tracking and spatial partitioning.
 *
 * <p>Thread-safety: This class is immutable and therefore thread-safe.
 *
 * @param worldId the UUID of the world containing this chunk
 * @param chunkX the chunk X coordinate (block X >> 4)
 * @param chunkZ the chunk Z coordinate (block Z >> 4)
 */
public record ChunkKey(UUID worldId, int chunkX, int chunkZ) {

    /**
     * Constructs a new ChunkKey with validation.
     *
     * @param worldId the world UUID, must not be null
     * @param chunkX the chunk X coordinate
     * @param chunkZ the chunk Z coordinate
     * @throws NullPointerException if worldId is null
     */
    public ChunkKey {
        Objects.requireNonNull(worldId, "worldId must not be null");
    }

    /**
     * Creates a ChunkKey from a block position.
     *
     * @param pos the block position
     * @return a ChunkKey representing the chunk containing the block
     * @throws NullPointerException if pos is null
     */
    public static ChunkKey fromBlockPos(BlockPos pos) {
        Objects.requireNonNull(pos, "pos must not be null");
        return pos.toChunkKey();
    }

    /**
     * Returns the minimum block X coordinate in this chunk.
     *
     * @return the minimum X coordinate
     */
    public int minBlockX() {
        return chunkX << 4;
    }

    /**
     * Returns the minimum block Z coordinate in this chunk.
     *
     * @return the minimum Z coordinate
     */
    public int minBlockZ() {
        return chunkZ << 4;
    }

    /**
     * Returns the maximum block X coordinate in this chunk.
     *
     * @return the maximum X coordinate (inclusive)
     */
    public int maxBlockX() {
        return (chunkX << 4) + 15;
    }

    /**
     * Returns the maximum block Z coordinate in this chunk.
     *
     * @return the maximum Z coordinate (inclusive)
     */
    public int maxBlockZ() {
        return (chunkZ << 4) + 15;
    }

    /**
     * Checks if the given block position is within this chunk (ignoring Y).
     *
     * @param pos the position to check
     * @return true if the position is within this chunk's XZ bounds
     */
    public boolean contains(BlockPos pos) {
        if (pos == null || !pos.worldId().equals(worldId)) {
            return false;
        }
        return (pos.x() >> 4) == chunkX && (pos.z() >> 4) == chunkZ;
    }

    @Override
    public String toString() {
        return String.format("ChunkKey[world=%s, x=%d, z=%d]",
            worldId.toString().substring(0, 8), chunkX, chunkZ);
    }
}
