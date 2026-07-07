package dev.kate.erd.bukkit.adapter;

import dev.kate.erd.core.model.ConnectionType;
import org.bukkit.Material;

import java.util.Optional;

/**
 * Maps Bukkit block materials to network type kinds.
 *
 * <p>Segment materials:
 * <ul>
 *   <li>RED_MUSHROOM_BLOCK → POWER segments</li>
 *   <li>BROWN_MUSHROOM_BLOCK → PIPE segments (fluids + gases)</li>
 *   <li>MUSHROOM_STEM → DATA segments</li>
 * </ul>
 *
 * <p>Thread-safety: This class is stateless and thread-safe.
 */
public final class SegmentMaterialResolver {

    /** POWER segment material */
    public static final Material POWER_SEGMENT = Material.RED_MUSHROOM_BLOCK;

    /** PIPE segment material */
    public static final Material PIPE_SEGMENT = Material.BROWN_MUSHROOM_BLOCK;

    /** DATA segment material */
    public static final Material DATA_SEGMENT = Material.MUSHROOM_STEM;

    private SegmentMaterialResolver() {
        // Utility class
    }

    /**
     * Gets the type kind for a segment material.
     *
     * @param material the block material
     * @return the type kind, or empty if not a segment material
     */
    public static Optional<ConnectionType> getConnectionType(Material material) {
        if (material == null) {
            return Optional.empty();
        }

        return switch (material) {
            case RED_MUSHROOM_BLOCK -> Optional.of(ConnectionType.POWER);
            case BROWN_MUSHROOM_BLOCK -> Optional.of(ConnectionType.PIPE);
            case MUSHROOM_STEM -> Optional.of(ConnectionType.DATA);
            default -> Optional.empty();
        };
    }

    /**
     * Gets the segment material for a type kind.
     *
     * @param layer the type kind
     * @return the segment material
     */
    public static Material getMaterial(ConnectionType layer) {
        return switch (layer) {
            case POWER -> POWER_SEGMENT;
            case PIPE -> PIPE_SEGMENT;
            case DATA -> DATA_SEGMENT;
        };
    }

    /**
     * Checks if a material is a segment.
     *
     * @param material the material to check
     * @return true if it's a segment material
     */
    public static boolean isSegment(Material material) {
        return material == POWER_SEGMENT || material == PIPE_SEGMENT || material == DATA_SEGMENT;
    }

    /**
     * Checks if a material is a segment of the specified type.
     *
     * @param material the material to check
     * @param layer the expected type
     * @return true if the material matches the type's segment type
     */
    public static boolean isSegmentOfLayer(Material material, ConnectionType layer) {
        return material == getMaterial(layer);
    }
}
