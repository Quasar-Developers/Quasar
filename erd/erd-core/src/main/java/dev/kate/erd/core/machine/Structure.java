package dev.kate.erd.core.machine;

import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ChunkKey;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable value object representing the physical structure of a machine or component.
 *
 * <p>A Structure captures:
 * <ul>
 *   <li>All block positions that make up the structure</li>
 *   <li>Endpoints/ports for network connectivity</li>
 *   <li>Computed metrics (dimensions, block count, tier)</li>
 *   <li>Which chunks the structure spans (for persistence)</li>
 * </ul>
 *
 * <p>Structures are immutable. When a machine is upgraded/resized, a new Structure
 * is created and the machine's {@code updateStructure()} method is called.
 *
 * <p>Example usage:
 * <pre>{@code
 * Structure structure = Structure.builder()
 *     .positions(occupiedBlocks)
 *     .endpoints(detectedEndpoints)
 *     .tier(2)
 *     .build();
 *
 * // Query structure properties
 * int blockCount = structure.metrics().blockCount();
 * Set<ChunkKey> chunks = structure.spannedChunks();
 * }</pre>
 *
 * <p>Thread-safety: This class is immutable and therefore thread-safe.
 */
public final class Structure {

    private final Set<BlockPos> positions;
    private final List<Endpoint> endpoints;
    private final StructureMetrics metrics;
    private final Set<ChunkKey> spannedChunks;

    private Structure(Set<BlockPos> positions, List<Endpoint> endpoints, int tier) {
        this.positions = Set.copyOf(Objects.requireNonNull(positions, "positions must not be null"));
        this.endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints must not be null"));
        this.metrics = computeMetrics(this.positions, tier);
        this.spannedChunks = computeSpannedChunks(this.positions);
    }

    /**
     * @return all block positions occupied by this structure (immutable)
     */
    public Set<BlockPos> positions() {
        return positions;
    }

    /**
     * @return all endpoints/ports on this structure (immutable)
     */
    public List<Endpoint> endpoints() {
        return endpoints;
    }

    /**
     * @return computed metrics about this structure
     */
    public StructureMetrics metrics() {
        return metrics;
    }

    /**
     * @return all chunks this structure spans (immutable)
     */
    public Set<ChunkKey> spannedChunks() {
        return spannedChunks;
    }

    /**
     * @return the number of blocks in this structure
     */
    public int size() {
        return positions.size();
    }

    /**
     * Checks if this structure occupies the given position.
     *
     * @param pos the position to check
     * @return true if the position is part of this structure
     */
    public boolean contains(BlockPos pos) {
        return positions.contains(pos);
    }

    /**
     * Checks if this structure spans multiple chunks.
     *
     * @return true if the structure spans more than one chunk
     */
    public boolean isMultiChunk() {
        return spannedChunks.size() > 1;
    }

    /**
     * Checks if this structure spans the given chunk.
     *
     * @param chunk the chunk to check
     * @return true if any block in this structure is in the chunk
     */
    public boolean spansChunk(ChunkKey chunk) {
        return spannedChunks.contains(chunk);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Structure that)) return false;
        return positions.equals(that.positions) && endpoints.equals(that.endpoints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(positions, endpoints);
    }

    @Override
    public String toString() {
        return String.format("Structure[blocks=%d, endpoints=%d, chunks=%d, metrics=%s]",
                positions.size(), endpoints.size(), spannedChunks.size(), metrics);
    }

    // ========== Factory Methods ==========

    /**
     * Creates a new builder for constructing a Structure.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a Structure from validation result data.
     *
     * @param positions the occupied positions
     * @param endpoints the detected endpoints
     * @return a new Structure
     */
    public static Structure of(Set<BlockPos> positions, List<Endpoint> endpoints) {
        return new Structure(positions, endpoints, 0);
    }

    /**
     * Creates a Structure from validation result data with a tier.
     *
     * @param positions the occupied positions
     * @param endpoints the detected endpoints
     * @param tier the structure tier/level
     * @return a new Structure
     */
    public static Structure of(Set<BlockPos> positions, List<Endpoint> endpoints, int tier) {
        return new Structure(positions, endpoints, tier);
    }

    /**
     * Creates a single-block Structure.
     *
     * @param position the single block position
     * @param endpoints the endpoints (may be empty)
     * @return a new single-block Structure
     */
    public static Structure singleBlock(BlockPos position, List<Endpoint> endpoints) {
        return new Structure(Set.of(position), endpoints, 0);
    }

    // ========== Internal ==========

    private static StructureMetrics computeMetrics(Set<BlockPos> positions, int tier) {
        if (positions.isEmpty()) {
            return new StructureMetrics(0, 0, 0, 0, tier);
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.x());
            maxX = Math.max(maxX, pos.x());
            minY = Math.min(minY, pos.y());
            maxY = Math.max(maxY, pos.y());
            minZ = Math.min(minZ, pos.z());
            maxZ = Math.max(maxZ, pos.z());
        }

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;

        return new StructureMetrics(width, height, depth, positions.size(), tier);
    }

    private static Set<ChunkKey> computeSpannedChunks(Set<BlockPos> positions) {
        return positions.stream()
                .map(BlockPos::toChunkKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    // ========== Builder ==========

    /**
     * Builder for creating Structure instances.
     */
    public static final class Builder {
        private Set<BlockPos> positions = Set.of();
        private List<Endpoint> endpoints = List.of();
        private int tier = 0;

        private Builder() {}

        /**
         * Sets the block positions.
         *
         * @param positions the positions
         * @return this builder
         */
        public Builder positions(Set<BlockPos> positions) {
            this.positions = positions;
            return this;
        }

        /**
         * Sets the endpoints.
         *
         * @param endpoints the endpoints
         * @return this builder
         */
        public Builder endpoints(List<Endpoint> endpoints) {
            this.endpoints = endpoints;
            return this;
        }

        /**
         * Sets the tier/level.
         *
         * @param tier the tier
         * @return this builder
         */
        public Builder tier(int tier) {
            this.tier = tier;
            return this;
        }

        /**
         * Builds the Structure.
         *
         * @return a new Structure
         * @throws IllegalStateException if positions is empty
         */
        public Structure build() {
            if (positions.isEmpty()) {
                throw new IllegalStateException("Structure must have at least one position");
            }
            return new Structure(positions, endpoints, tier);
        }
    }
}

