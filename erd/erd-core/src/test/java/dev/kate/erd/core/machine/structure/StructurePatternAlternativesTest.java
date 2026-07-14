package dev.kate.erd.core.machine.structure;

import dev.kate.erd.core.machine.StructureSnapshot;
import dev.kate.erd.core.machine.ValidationResult;
import dev.kate.erd.core.model.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for block alternatives in StructurePattern.
 */
class StructurePatternAlternativesTest {

    private static final UUID TEST_WORLD = UUID.randomUUID();

    @Test
    void alternatives_acceptsPrimaryBlock() {
        StructurePattern pattern = StructurePattern.builder()
            .layer("@X ")
            .key('@', "minecraft:redstone_block")
            .key('X', "minecraft:iron_block")
            .alternatives('X', "minecraft:gold_block", "minecraft:diamond_block")
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
    void alternatives_acceptsFirstAlternative() {
        StructurePattern pattern = StructurePattern.builder()
            .layer("@X ")
            .key('@', "minecraft:redstone_block")
            .key('X', "minecraft:iron_block")
            .alternatives('X', "minecraft:gold_block", "minecraft:diamond_block")
            .build();

        BlockPos origin = new BlockPos(TEST_WORLD, 0, 0, 0);
        StructureSnapshot snapshot = StructureSnapshot.builder()
            .origin(origin)
            .addBlock(origin, "minecraft:redstone_block")
            .addBlock(origin.offset(1, 0, 0), "minecraft:gold_block")
            .build();

        ValidationResult result = pattern.validate(snapshot);
        assertTrue(result instanceof ValidationResult.Valid,
            "Should accept first alternative block");
    }

    @Test
    void alternatives_acceptsSecondAlternative() {
        StructurePattern pattern = StructurePattern.builder()
            .layer("@X ")
            .key('@', "minecraft:redstone_block")
            .key('X', "minecraft:iron_block")
            .alternatives('X', "minecraft:gold_block", "minecraft:diamond_block")
            .build();

        BlockPos origin = new BlockPos(TEST_WORLD, 0, 0, 0);
        StructureSnapshot snapshot = StructureSnapshot.builder()
            .origin(origin)
            .addBlock(origin, "minecraft:redstone_block")
            .addBlock(origin.offset(1, 0, 0), "minecraft:diamond_block")
            .build();

        ValidationResult result = pattern.validate(snapshot);
        assertTrue(result instanceof ValidationResult.Valid,
            "Should accept second alternative block");
    }

    @Test
    void alternatives_rejectsInvalidBlock() {
        StructurePattern pattern = StructurePattern.builder()
            .layer("@X ")
            .key('@', "minecraft:redstone_block")
            .key('X', "minecraft:iron_block")
            .alternatives('X', "minecraft:gold_block", "minecraft:diamond_block")
            .build();

        BlockPos origin = new BlockPos(TEST_WORLD, 0, 0, 0);
        StructureSnapshot snapshot = StructureSnapshot.builder()
            .origin(origin)
            .addBlock(origin, "minecraft:redstone_block")
            .addBlock(origin.offset(1, 0, 0), "minecraft:stone")
            .build();

        ValidationResult result = pattern.validate(snapshot);
        assertTrue(result instanceof ValidationResult.Invalid,
            "Should reject block not in alternatives");
    }

    @Test
    void alternatives_mixedPattern_worksCorrectly() {
        StructurePattern pattern = StructurePattern.builder()
            .layer("XXX")
            .layer("X@X")
            .layer("XXX")
            .key('X', "minecraft:iron_block")
            .key('@', "minecraft:redstone_block")
            .alternatives('X', "minecraft:gold_block", "minecraft:copper_block")
            .build();

        BlockPos origin = new BlockPos(TEST_WORLD, 0, 0, 0);
        StructureSnapshot.Builder builder = StructureSnapshot.builder().origin(origin);

        // Mix of primary and alternative blocks
        builder.addBlock(origin.offset(-1, 0, -1), "minecraft:iron_block");
        builder.addBlock(origin.offset(0, 0, -1), "minecraft:gold_block");
        builder.addBlock(origin.offset(1, 0, -1), "minecraft:copper_block");
        builder.addBlock(origin.offset(-1, 0, 0), "minecraft:iron_block");
        builder.addBlock(origin, "minecraft:redstone_block");
        builder.addBlock(origin.offset(1, 0, 0), "minecraft:gold_block");
        builder.addBlock(origin.offset(-1, 0, 1), "minecraft:copper_block");
        builder.addBlock(origin.offset(0, 0, 1), "minecraft:iron_block");
        builder.addBlock(origin.offset(1, 0, 1), "minecraft:gold_block");

        StructureSnapshot snapshot = builder.build();
        ValidationResult result = pattern.validate(snapshot);
        assertTrue(result instanceof ValidationResult.Valid,
            "Should accept mixed primary and alternative blocks");
    }

    @Test
    void alternatives_withRotation_worksCorrectly() {
        StructurePattern pattern = StructurePattern.builder()
            .layer("@X ")
            .key('@', "minecraft:redstone_block")
            .key('X', "minecraft:iron_block")
            .alternatives('X', "minecraft:gold_block")
            .allowRotation(true)
            .build();

        // Test with alternative block in east rotation
        BlockPos origin = new BlockPos(TEST_WORLD, 0, 0, 0);
        StructureSnapshot snapshot = StructureSnapshot.builder()
            .origin(origin)
            .addBlock(origin, "minecraft:redstone_block")
            .addBlock(origin.offset(0, 0, 1), "minecraft:gold_block")
            .build();

        ValidationResult result = pattern.validate(snapshot);
        assertTrue(result instanceof ValidationResult.Valid,
            "Alternatives should work with rotation");
    }
}
