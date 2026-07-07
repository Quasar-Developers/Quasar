package dev.kate.erd.core.machine;

import dev.kate.erd.core.model.BlockPos;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of block structure for validation purposes.
 *
 * <p>This snapshot captures the block states in a region without any
 * Bukkit dependencies. Block types are represented as string keys that
 * the Bukkit adapter maps to actual materials.
 *
 * <p>Thread-safety: This class is immutable and therefore thread-safe.
 */
public final class StructureSnapshot {

    private final Map<BlockPos, BlockData> blocks;
    private final BlockPos origin;

    /**
     * Creates a structure snapshot.
     *
     * @param blocks mapping of positions to block data
     * @param origin the reference/anchor position for the structure
     */
    public StructureSnapshot(Map<BlockPos, BlockData> blocks, BlockPos origin) {
        this.blocks = Map.copyOf(Objects.requireNonNull(blocks, "blocks must not be null"));
        this.origin = Objects.requireNonNull(origin, "origin must not be null");
    }

    /**
     * @return the reference position for this structure
     */
    public BlockPos origin() {
        return origin;
    }

    /**
     * Gets the block data at a position.
     *
     * @param pos the position
     * @return the block data, or null if not in snapshot
     */
    public BlockData getBlock(BlockPos pos) {
        return blocks.get(pos);
    }

    /**
     * Gets the block data at a position relative to origin.
     *
     * @param dx X offset from origin
     * @param dy Y offset from origin
     * @param dz Z offset from origin
     * @return the block data, or null if not in snapshot
     */
    public BlockData getBlockRelative(int dx, int dy, int dz) {
        return blocks.get(origin.offset(dx, dy, dz));
    }

    /**
     * Checks if a position has a specific block type.
     *
     * @param pos the position
     * @param typeKey the block type key to check
     * @return true if the position has the specified type
     */
    public boolean isBlockType(BlockPos pos, String typeKey) {
        BlockData data = blocks.get(pos);
        return data != null && data.typeKey().equals(typeKey);
    }

    /**
     * @return all positions in this snapshot
     */
    public Set<BlockPos> positions() {
        return blocks.keySet();
    }

    /**
     * @return the number of blocks in this snapshot
     */
    public int size() {
        return blocks.size();
    }

    /**
     * Represents the data for a single block in the snapshot.
     *
     * @param typeKey the block type identifier (e.g., "minecraft:iron_block")
     * @param properties additional block properties as key-value pairs
     */
    public record BlockData(String typeKey, Map<String, String> properties) {
        public BlockData {
            Objects.requireNonNull(typeKey, "typeKey must not be null");
            properties = properties != null ? Map.copyOf(properties) : Map.of();
        }

        /**
         * Creates block data with just a type key.
         *
         * @param typeKey the block type
         */
        public BlockData(String typeKey) {
            this(typeKey, Map.of());
        }

        /**
         * Gets a property value.
         *
         * @param key the property key
         * @return the value, or null if not present
         */
        public String getProperty(String key) {
            return properties.get(key);
        }
    }

    /**
     * Builder for creating StructureSnapshot instances.
     */
    public static final class Builder {
        private final java.util.HashMap<BlockPos, BlockData> blocks = new java.util.HashMap<>();
        private BlockPos origin;

        /**
         * Sets the origin position.
         *
         * @param origin the origin
         * @return this builder
         */
        public Builder origin(BlockPos origin) {
            this.origin = origin;
            return this;
        }

        /**
         * Adds a block to the snapshot.
         *
         * @param pos the position
         * @param data the block data
         * @return this builder
         */
        public Builder addBlock(BlockPos pos, BlockData data) {
            blocks.put(pos, data);
            return this;
        }

        /**
         * Adds a block with just a type key.
         *
         * @param pos the position
         * @param typeKey the block type
         * @return this builder
         */
        public Builder addBlock(BlockPos pos, String typeKey) {
            return addBlock(pos, new BlockData(typeKey));
        }

        /**
         * Builds the snapshot.
         *
         * @return the immutable snapshot
         * @throws IllegalStateException if origin is not set
         */
        public StructureSnapshot build() {
            if (origin == null) {
                throw new IllegalStateException("Origin must be set");
            }
            return new StructureSnapshot(blocks, origin);
        }
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}
