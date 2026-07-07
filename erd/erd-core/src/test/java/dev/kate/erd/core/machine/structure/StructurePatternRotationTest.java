package dev.kate.erd.core.machine.structure;

import dev.kate.erd.core.endpoint.EndpointRole;
import dev.kate.erd.core.machine.StructureSnapshot;
import dev.kate.erd.core.machine.ValidationResult;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StructurePattern rotation support.
 */
class StructurePatternRotationTest {

    private static final UUID TEST_WORLD = UUID.randomUUID();

    @Test
    void rotation_north_matchesOriginalPattern() {
        StructurePattern pattern = StructurePattern.builder()
            .layer("@X ")
            .key('@', "minecraft:redstone_block")
            .key('X', "minecraft:iron_block")
            .allowRotation(false)
            .build();

        BlockPos origin = new BlockPos(TEST_WORLD, 0, 0, 0);
        StructureSnapshot snapshot = StructureSnapshot.builder()
            .origin(origin)
            .addBlock(origin, "minecraft:redstone_block")
            .addBlock(origin.offset(1, 0, 0), "minecraft:iron_block")
            .build();

        ValidationResult result = pattern.validate(snapshot);
        assertTrue(result instanceof ValidationResult.Valid);
    }

    @Test
    void rotation_east_rotates90Degrees() {
        StructurePattern pattern = StructurePattern.builder()
            .layer("@X ")
            .key('@', "minecraft:redstone_block")
            .key('X', "minecraft:iron_block")
            .allowRotation(true)
            .build();

        // East rotation: X moves to +Z from anchor
        BlockPos origin = new BlockPos(TEST_WORLD, 0, 0, 0);
        StructureSnapshot snapshot = StructureSnapshot.builder()
            .origin(origin)
            .addBlock(origin, "minecraft:redstone_block")
            .addBlock(origin.offset(0, 0, 1), "minecraft:iron_block")
            .build();

        ValidationResult result = pattern.validate(snapshot);
        assertTrue(result instanceof ValidationResult.Valid,
            "East rotation should match");
    }

    @Test
    void rotation_south_rotates180Degrees() {
        StructurePattern pattern = StructurePattern.builder()
            .layer("@X ")
            .key('@', "minecraft:redstone_block")
            .key('X', "minecraft:iron_block")
            .allowRotation(true)
            .build();

        // South rotation: X moves to -X from anchor
        BlockPos origin = new BlockPos(TEST_WORLD, 0, 0, 0);
        StructureSnapshot snapshot = StructureSnapshot.builder()
            .origin(origin)
            .addBlock(origin, "minecraft:redstone_block")
            .addBlock(origin.offset(-1, 0, 0), "minecraft:iron_block")
            .build();

        ValidationResult result = pattern.validate(snapshot);
        assertTrue(result instanceof ValidationResult.Valid,
            "South rotation should match");
    }

    @Test
    void rotation_west_rotates270Degrees() {
        StructurePattern pattern = StructurePattern.builder()
            .layer("@X ")
            .key('@', "minecraft:redstone_block")
            .key('X', "minecraft:iron_block")
            .allowRotation(true)
            .build();

        // West rotation: X moves to -Z from anchor
        BlockPos origin = new BlockPos(TEST_WORLD, 0, 0, 0);
        StructureSnapshot snapshot = StructureSnapshot.builder()
            .origin(origin)
            .addBlock(origin, "minecraft:redstone_block")
            .addBlock(origin.offset(0, 0, -1), "minecraft:iron_block")
            .build();

        ValidationResult result = pattern.validate(snapshot);
        assertTrue(result instanceof ValidationResult.Valid,
            "West rotation should match");
    }

    @Test
    void rotation_disabled_onlyMatchesNorthOrientation() {
        StructurePattern pattern = StructurePattern.builder()
            .layer("@X ")
            .key('@', "minecraft:redstone_block")
            .key('X', "minecraft:iron_block")
            .allowRotation(false)
            .build();

        // Try east rotation
        BlockPos origin = new BlockPos(TEST_WORLD, 0, 0, 0);
        StructureSnapshot snapshot = StructureSnapshot.builder()
            .origin(origin)
            .addBlock(origin, "minecraft:redstone_block")
            .addBlock(origin.offset(0, 0, 1), "minecraft:iron_block")
            .build();

        ValidationResult result = pattern.validate(snapshot);
        assertTrue(result instanceof ValidationResult.Invalid,
            "Should fail when rotation is disabled");
    }

    @Test
    void rotation_complexPattern_allRotationsWork() {
        StructurePattern pattern = StructurePattern.builder()
            .layer("XXX")
            .layer("X@X")
            .layer("XXX")
            .nextLayer()
            .layer("PPP")
            .layer("P P")
            .layer("PPP")
            .key('X', "minecraft:iron_block")
            .key('@', "minecraft:redstone_block")
            .key('P', "minecraft:copper_block")
            .allowRotation(true)
            .endpoint('P', ConnectionType.PIPE, EndpointRole.CONSUMER)
            .build();

        // Build north-facing structure
        BlockPos origin = new BlockPos(TEST_WORLD, 0, 0, 0);
        StructureSnapshot.Builder builder = StructureSnapshot.builder().origin(origin);

        // Bottom type (y=0)
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                String block = (x == 0 && z == 0) ? "minecraft:redstone_block" : "minecraft:iron_block";
                builder.addBlock(origin.offset(x, 0, z), block);
            }
        }

        // Top type (y=1)
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                if (x == 0 && z == 0) continue; // Empty center
                builder.addBlock(origin.offset(x, 1, z), "minecraft:copper_block");
            }
        }

        StructureSnapshot snapshot = builder.build();
        ValidationResult result = pattern.validate(snapshot);
        assertTrue(result instanceof ValidationResult.Valid,
            "Complex pattern should match in north orientation");
    }
}
