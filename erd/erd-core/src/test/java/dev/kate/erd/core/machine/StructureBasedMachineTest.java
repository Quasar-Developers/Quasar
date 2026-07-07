package dev.kate.erd.core.machine;

import dev.kate.erd.core.machine.test.FusionReactorDefinition;
import dev.kate.erd.core.machine.test.FusionReactorInstance;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.MachineId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the new Structure-based machine framework.
 */
class StructureBasedMachineTest {

    private static final UUID TEST_WORLD = UUID.randomUUID();

    @Test
    void structure_computesMetricsCorrectly() {
        BlockPos p1 = new BlockPos(TEST_WORLD, 0, 0, 0);
        BlockPos p2 = new BlockPos(TEST_WORLD, 1, 0, 0);
        BlockPos p3 = new BlockPos(TEST_WORLD, 2, 0, 0);

        Structure structure = Structure.of(Set.of(p1, p2, p3), List.of());

        StructureMetrics metrics = structure.metrics();
        assertThat(metrics.width()).isEqualTo(3);
        assertThat(metrics.height()).isEqualTo(1);
        assertThat(metrics.depth()).isEqualTo(1);
        assertThat(metrics.blockCount()).isEqualTo(3);
    }

    @Test
    void structure_tracksSpannedChunks() {
        // Create positions in different chunks
        BlockPos chunk0 = new BlockPos(TEST_WORLD, 0, 64, 0);     // Chunk (0, 0)
        BlockPos chunk1 = new BlockPos(TEST_WORLD, 16, 64, 0);   // Chunk (1, 0)
        BlockPos chunk2 = new BlockPos(TEST_WORLD, 0, 64, 16);   // Chunk (0, 1)

        Structure structure = Structure.of(Set.of(chunk0, chunk1, chunk2), List.of());

        assertThat(structure.spannedChunks()).hasSize(3);
        assertThat(structure.isMultiChunk()).isTrue();
    }

    @Test
    void validationResult_validCreatesStructure() {
        BlockPos origin = new BlockPos(TEST_WORLD, 0, 0, 0);
        Set<BlockPos> positions = Set.of(origin, origin.offset(1, 0, 0));

        ValidationResult result = ValidationResult.valid(positions, List.of());

        assertThat(result).isInstanceOf(ValidationResult.Valid.class);
        ValidationResult.Valid valid = (ValidationResult.Valid) result;
        assertThat(valid.structure().size()).isEqualTo(2);
    }

    @Test
    void validationResult_invalidContainsReason() {
        BlockPos problem = new BlockPos(TEST_WORLD, 5, 5, 5);

        ValidationResult result = ValidationResult.invalid("Missing block", Set.of(problem));

        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        ValidationResult.Invalid invalid = (ValidationResult.Invalid) result;
        assertThat(invalid.reason()).isEqualTo("Missing block");
        assertThat(invalid.problemPositions()).contains(problem);
    }

    @Test
    void machineInstance_canUpdateStructure() {
        BlockPos anchor = new BlockPos(TEST_WORLD, 0, 0, 0);
        Structure initial = Structure.of(Set.of(anchor), List.of());

        FusionReactorInstance reactor = new FusionReactorInstance(
                MachineId.create(),
                FusionReactorDefinition.INSTANCE,
                initial
        );

        assertThat(reactor.structure().size()).isEqualTo(1);

        // Upgrade the structure
        Structure larger = Structure.of(
                Set.of(anchor, anchor.offset(1, 0, 0), anchor.offset(0, 1, 0)),
                List.of()
        );
        reactor.updateStructure(larger);

        assertThat(reactor.structure().size()).isEqualTo(3);
    }

    @Test
    void machineInstance_rescanDetectsChanges() {
        BlockPos anchor = new BlockPos(TEST_WORLD, 0, 0, 0);
        Set<BlockPos> positions = Set.of(anchor);

        Structure initial = Structure.of(positions, List.of());

        FusionReactorInstance reactor = new FusionReactorInstance(
                MachineId.create(),
                FusionReactorDefinition.INSTANCE,
                initial
        );

        // Build a snapshot with the same structure
        StructureSnapshot snapshot = StructureSnapshot.builder()
                .origin(anchor)
                .addBlock(anchor, FusionReactorDefinition.CORE_BLOCK)
                .build();

        // This will fail validation because it's not a complete 3x3x3
        RescanResult result = reactor.rescan(snapshot);
        assertThat(result).isEqualTo(RescanResult.INVALID);
    }

    @Test
    void machineSnapshot_builderWorks() {
        MachineId id = MachineId.create();
        BlockPos anchor = new BlockPos(TEST_WORLD, 0, 0, 0);

        MachineSnapshot snapshot = MachineSnapshot.builder()
                .id(id)
                .typeId("test:machine")
                .anchorPosition(anchor)
                .occupiedPositions(Set.of(anchor))
                .stateVersion(1)
                .state(java.util.Map.of("key", "value"))
                .build();

        assertThat(snapshot.id()).isEqualTo(id);
        assertThat(snapshot.typeId()).isEqualTo("test:machine");
        assertThat(snapshot.anchorPosition()).isEqualTo(anchor);
        assertThat(snapshot.occupiedPositions()).contains(anchor);
        assertThat(snapshot.stateVersion()).isEqualTo(1);
        assertThat(snapshot.state()).containsEntry("key", "value");
    }

    @Test
    void machineSnapshot_computesSpannedChunks() {
        BlockPos anchor = new BlockPos(TEST_WORLD, 0, 64, 0);
        BlockPos farBlock = new BlockPos(TEST_WORLD, 20, 64, 0); // Different chunk

        MachineSnapshot snapshot = MachineSnapshot.builder()
                .id(MachineId.create())
                .typeId("test:machine")
                .anchorPosition(anchor)
                .occupiedPositions(Set.of(anchor, farBlock))
                .build();

        assertThat(snapshot.isMultiChunk()).isTrue();
        assertThat(snapshot.spannedChunks()).hasSize(2);
    }
}

