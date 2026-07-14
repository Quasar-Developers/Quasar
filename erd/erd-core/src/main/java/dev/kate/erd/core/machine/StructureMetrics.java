package dev.kate.erd.core.machine;

/**
 * Computed metrics about a machine or component structure.
 *
 * <p>These metrics are derived from the structure's block positions and
 * can be used to calculate capacity, throughput, power scaling, and other
 * stats that depend on structure size.
 *
 * <p>Example usage:
 * <pre>{@code
 * Structure structure = machine.structure();
 * StructureMetrics metrics = structure.metrics();
 *
 * int capacity = BASE_CAPACITY * metrics.blockCount();
 * int powerOutput = BASE_POWER * metrics.tier();
 * }</pre>
 *
 * <p>Thread-safety: This record is immutable and therefore thread-safe.
 *
 * @param width the X-axis span (maxX - minX + 1)
 * @param height the Y-axis span (maxY - minY + 1)
 * @param depth the Z-axis span (maxZ - minZ + 1)
 * @param blockCount total number of blocks in the structure
 * @param tier optional tier/level derived from structure (0 if not tiered)
 */
public record StructureMetrics(
        int width,
        int height,
        int depth,
        int blockCount,
        int tier
) {
    /**
     * Creates metrics with default tier of 0.
     */
    public StructureMetrics(int width, int height, int depth, int blockCount) {
        this(width, height, depth, blockCount, 0);
    }

    /**
     * @return the volume of the bounding box (width * height * depth)
     */
    public int boundingVolume() {
        return width * height * depth;
    }

    /**
     * @return the fill ratio (blockCount / boundingVolume), 0.0 to 1.0
     */
    public double fillRatio() {
        int volume = boundingVolume();
        return volume > 0 ? (double) blockCount / volume : 0.0;
    }

    /**
     * @return true if the structure is a solid cuboid (fill ratio == 1.0)
     */
    public boolean isSolid() {
        return blockCount == boundingVolume();
    }

    /**
     * Creates metrics for a single-block structure.
     */
    public static StructureMetrics singleBlock() {
        return new StructureMetrics(1, 1, 1, 1, 0);
    }

    /**
     * Creates metrics for a single-block structure with a tier.
     */
    public static StructureMetrics singleBlock(int tier) {
        return new StructureMetrics(1, 1, 1, 1, tier);
    }
}

