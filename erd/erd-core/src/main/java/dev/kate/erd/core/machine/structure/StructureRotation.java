package dev.kate.erd.core.machine.structure;

import dev.kate.erd.core.model.BlockPos;

/**
 * Represents the four cardinal rotations for structure patterns.
 *
 * <p>Rotations are applied around the Y-axis (vertical), transforming
 * the X and Z coordinates while leaving Y unchanged.
 *
 * <p>Coordinate system:
 * <ul>
 *   <li>NORTH: No rotation (identity) - X+ is east, Z+ is south</li>
 *   <li>EAST: 90° clockwise - X maps to Z, Z maps to -X</li>
 *   <li>SOUTH: 180° rotation - X maps to -X, Z maps to -Z</li>
 *   <li>WEST: 270° clockwise (90° counter-clockwise) - X maps to -Z, Z maps to X</li>
 * </ul>
 *
 * <p>Thread-safety: Enum values are inherently thread-safe.
 */
public enum StructureRotation {

    /**
     * North orientation (0°, no rotation).
     */
    NORTH {
        @Override
        public BlockPos rotate(BlockPos origin, int relX, int relY, int relZ) {
            return origin.offset(relX, relY, relZ);
        }
    },

    /**
     * East orientation (90° clockwise).
     */
    EAST {
        @Override
        public BlockPos rotate(BlockPos origin, int relX, int relY, int relZ) {
            // 90° CW: (x, z) -> (z, -x)
            return origin.offset(-relZ, relY, relX);
        }
    },

    /**
     * South orientation (180°).
     */
    SOUTH {
        @Override
        public BlockPos rotate(BlockPos origin, int relX, int relY, int relZ) {
            // 180°: (x, z) -> (-x, -z)
            return origin.offset(-relX, relY, -relZ);
        }
    },

    /**
     * West orientation (270° clockwise, 90° counter-clockwise).
     */
    WEST {
        @Override
        public BlockPos rotate(BlockPos origin, int relX, int relY, int relZ) {
            // 270° CW / 90° CCW: (x, z) -> (-z, x)
            return origin.offset(relZ, relY, -relX);
        }
    };

    /**
     * Rotates relative coordinates and applies them to the origin position.
     *
     * @param origin the origin/anchor position
     * @param relX the relative X coordinate
     * @param relY the relative Y coordinate
     * @param relZ the relative Z coordinate
     * @return the rotated absolute position
     */
    public abstract BlockPos rotate(BlockPos origin, int relX, int relY, int relZ);
}

