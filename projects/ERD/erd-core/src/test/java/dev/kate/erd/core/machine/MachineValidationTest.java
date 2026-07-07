package dev.kate.erd.core.machine;

import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.machine.test.FusionReactorDefinition;
import dev.kate.erd.core.machine.test.FusionReactorInstance;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.util.ErdLogger;
import dev.kate.erd.core.util.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for machine validation system - verifies that machines can be validated
 * against world state and removed when they become invalid.
 *
 * <p>This tests the periodic validation mechanism that protects against machines
 * destroyed externally (WorldEdit, creeper explosions, etc.) without firing events.
 */
class MachineValidationTest {

    private static final UUID TEST_WORLD = UUID.randomUUID();
    private InstanceManager instanceManager;

    @BeforeEach
    void setUp() {
        instanceManager = new InstanceManager(ErdLogger.silent(), new TestClock(1000));
    }

    @Test
    void rescan_unchanged_whenStructureValid() {
        // Create a machine with proper 3x3x3 structure matching FusionReactor requirements
        BlockPos anchor = new BlockPos(TEST_WORLD, 0, 0, 0);
        
        // Build the expected structure with all required blocks
        Set<BlockPos> positions = new HashSet<>();
        List<Endpoint> endpoints = new ArrayList<>();
        
        // Core at center
        positions.add(anchor);
        
        // Add all 3x3x3 positions (26 blocks around center)
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue; // Skip core
                    positions.add(anchor.offset(x, y, z));
                }
            }
        }
        
        Structure structure = Structure.of(positions, endpoints);
        FusionReactorInstance reactor = new FusionReactorInstance(
            MachineId.create(),
            FusionReactorDefinition.INSTANCE,
            structure
        );

        // Build snapshot that matches the FusionReactor structure requirements
        StructureSnapshot.Builder snapshotBuilder = StructureSnapshot.builder().origin(anchor);
        
        // Core beacon at center
        snapshotBuilder.addBlock(anchor, FusionReactorDefinition.CORE_BLOCK);
        
        // Add all 3x3x3 positions with correct block types
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue; // Skip core
                    
                    BlockPos pos = anchor.offset(x, y, z);
                    
                    // Middle layer sides are ports (glass)
                    if (y == 0 && ((x == 0 && z != 0) || (z == 0 && x != 0))) {
                        snapshotBuilder.addBlock(pos, FusionReactorDefinition.PORT_BLOCK);
                    } else {
                        // Everything else is casing (iron_block)
                        snapshotBuilder.addBlock(pos, FusionReactorDefinition.CASING_BLOCK);
                    }
                }
            }
        }
        StructureSnapshot snapshot = snapshotBuilder.build();

        // Rescan should return UNCHANGED
        RescanResult result = reactor.rescan(snapshot);
        assertThat(result).isEqualTo(RescanResult.UNCHANGED);
    }

    @Test
    void rescan_invalid_whenBlocksMissing() {
        // Create a machine with a 3x3x3 structure
        BlockPos anchor = new BlockPos(TEST_WORLD, 0, 0, 0);
        Set<BlockPos> positions = build3x3x3Positions(anchor);
        
        Structure structure = Structure.of(positions, List.of());
        FusionReactorInstance reactor = new FusionReactorInstance(
            MachineId.create(),
            FusionReactorDefinition.INSTANCE,
            structure
        );

        // Build snapshot with some blocks missing (simulate WorldEdit destruction)
        StructureSnapshot.Builder snapshotBuilder = StructureSnapshot.builder().origin(anchor);
        int count = 0;
        for (BlockPos pos : positions) {
            if (count < 20) { // Only add 20 out of 27 blocks
                snapshotBuilder.addBlock(pos, FusionReactorDefinition.CORE_BLOCK);
            } else {
                // Missing blocks - simulate AIR
                snapshotBuilder.addBlock(pos, "minecraft:air");
            }
            count++;
        }
        StructureSnapshot snapshot = snapshotBuilder.build();

        // Rescan should return INVALID
        RescanResult result = reactor.rescan(snapshot);
        assertThat(result).isEqualTo(RescanResult.INVALID);
    }

    @Test
    void rescan_invalid_whenBlocksReplacedWithAir() {
        // Create a machine
        BlockPos anchor = new BlockPos(TEST_WORLD, 0, 0, 0);
        Set<BlockPos> positions = build3x3x3Positions(anchor);
        
        Structure structure = Structure.of(positions, List.of());
        FusionReactorInstance reactor = new FusionReactorInstance(
            MachineId.create(),
            FusionReactorDefinition.INSTANCE,
            structure
        );

        // Build snapshot where all blocks are AIR (simulate complete destruction)
        StructureSnapshot.Builder snapshotBuilder = StructureSnapshot.builder().origin(anchor);
        for (BlockPos pos : positions) {
            snapshotBuilder.addBlock(pos, "minecraft:air");
        }
        StructureSnapshot snapshot = snapshotBuilder.build();

        // Rescan should return INVALID
        RescanResult result = reactor.rescan(snapshot);
        assertThat(result).isEqualTo(RescanResult.INVALID);
    }

    @Test
    void instanceManager_removesInvalidMachine() {
        // Create and register a machine
        BlockPos anchor = new BlockPos(TEST_WORLD, 0, 0, 0);
        Set<BlockPos> positions = build3x3x3Positions(anchor);
        
        Structure structure = Structure.of(positions, List.of());
        FusionReactorInstance reactor = new FusionReactorInstance(
            MachineId.create(),
            FusionReactorDefinition.INSTANCE,
            structure
        );

        instanceManager.registerMachine(reactor);
        assertThat(instanceManager.machineCount()).isEqualTo(1);
        assertThat(instanceManager.getMachine(reactor.id())).isPresent();

        // Remove the machine
        instanceManager.removeMachine(reactor);
        assertThat(instanceManager.machineCount()).isEqualTo(0);
        assertThat(instanceManager.getMachine(reactor.id())).isEmpty();
    }

    @Test
    void validation_workflow_detectsAndRemovesInvalidMachines() {
        // Simulate the full validation workflow:
        // 1. Register machines
        // 2. Detect invalid ones via rescan
        // 3. Remove them from instance manager

        BlockPos anchor1 = new BlockPos(TEST_WORLD, 0, 0, 0);
        BlockPos anchor2 = new BlockPos(TEST_WORLD, 10, 0, 0);
        
        Set<BlockPos> positions1 = build3x3x3Positions(anchor1);
        Set<BlockPos> positions2 = build3x3x3Positions(anchor2);
        
        Structure structure1 = Structure.of(positions1, List.of());
        Structure structure2 = Structure.of(positions2, List.of());
        
        FusionReactorInstance reactor1 = new FusionReactorInstance(
            MachineId.create(),
            FusionReactorDefinition.INSTANCE,
            structure1
        );
        FusionReactorInstance reactor2 = new FusionReactorInstance(
            MachineId.create(),
            FusionReactorDefinition.INSTANCE,
            structure2
        );

        // Register both machines
        instanceManager.registerMachine(reactor1);
        instanceManager.registerMachine(reactor2);
        assertThat(instanceManager.machineCount()).isEqualTo(2);

        // Build snapshots: reactor1 is valid, reactor2 is invalid (destroyed)
        
        // Valid snapshot for reactor1
        StructureSnapshot.Builder snapshot1Builder = StructureSnapshot.builder().origin(anchor1);
        snapshot1Builder.addBlock(anchor1, FusionReactorDefinition.CORE_BLOCK);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockPos pos = anchor1.offset(x, y, z);
                    if (y == 0 && ((x == 0 && z != 0) || (z == 0 && x != 0))) {
                        snapshot1Builder.addBlock(pos, FusionReactorDefinition.PORT_BLOCK);
                    } else {
                        snapshot1Builder.addBlock(pos, FusionReactorDefinition.CASING_BLOCK);
                    }
                }
            }
        }
        StructureSnapshot validSnapshot = snapshot1Builder.build();

        // Invalid snapshot for reactor2 - all AIR
        StructureSnapshot.Builder snapshot2Builder = StructureSnapshot.builder().origin(anchor2);
        for (BlockPos pos : positions2) {
            snapshot2Builder.addBlock(pos, "minecraft:air"); // All AIR - invalid
        }
        StructureSnapshot invalidSnapshot = snapshot2Builder.build();

        // Simulate validation process
        List<MachineInstance> toRemove = new ArrayList<>();
        
        for (MachineInstance machine : instanceManager.allMachines()) {
            StructureSnapshot snapshot = machine.id().equals(reactor1.id()) 
                ? validSnapshot 
                : invalidSnapshot;
            
            RescanResult result = machine.rescan(snapshot);
            if (result == RescanResult.INVALID) {
                toRemove.add(machine);
            }
        }

        // Remove invalid machines
        for (MachineInstance machine : toRemove) {
            instanceManager.removeMachine(machine);
        }

        // Verify only reactor1 remains
        assertThat(instanceManager.machineCount()).isEqualTo(1);
        assertThat(instanceManager.getMachine(reactor1.id())).isPresent();
        assertThat(instanceManager.getMachine(reactor2.id())).isEmpty();
    }

    /**
     * Helper to build a 3x3x3 cube of positions centered on anchor.
     * Offsets range from -1 to 1 in each dimension (27 blocks total).
     */
    private Set<BlockPos> build3x3x3Positions(BlockPos anchor) {
        Set<BlockPos> positions = new HashSet<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    positions.add(anchor.offset(x, y, z));
                }
            }
        }
        return positions;
    }

    /**
     * Builds a valid FusionReactor snapshot centered on the given origin.
     */
    private StructureSnapshot buildValidSnapshot(BlockPos origin) {
        StructureSnapshot.Builder snapshotBuilder = StructureSnapshot.builder().origin(origin);
        snapshotBuilder.addBlock(origin, FusionReactorDefinition.CORE_BLOCK);

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockPos pos = origin.offset(x, y, z);
                    if (y == 0 && ((x == 0 && z != 0) || (z == 0 && x != 0))) {
                        snapshotBuilder.addBlock(pos, FusionReactorDefinition.PORT_BLOCK);
                    } else {
                        snapshotBuilder.addBlock(pos, FusionReactorDefinition.CASING_BLOCK);
                    }
                }
            }
        }
        return snapshotBuilder.build();
    }

    @Test
    void createInstance_anchorIsCenter_notRandomSetElement() {
        // Simulate the detection flow: validate → createInstance → verify anchor
        BlockPos expectedAnchor = new BlockPos(TEST_WORLD, 5, 10, 15);

        StructureSnapshot snapshot = buildValidSnapshot(expectedAnchor);
        ValidationResult result = FusionReactorDefinition.INSTANCE.validate(snapshot);

        assertThat(result).isInstanceOf(ValidationResult.Valid.class);

        ValidationResult.Valid valid = (ValidationResult.Valid) result;
        FusionReactorInstance reactor = FusionReactorDefinition.INSTANCE.createInstance(
            MachineId.create(), valid.structure());

        // The anchor must be the center (core) block, not a random set element
        assertThat(reactor.anchorPosition()).isEqualTo(expectedAnchor);
    }

    @Test
    void rescan_unchanged_afterCreateInstance_simulatesAsyncValidation() {
        // Simulate the exact flow that causes the bug:
        // 1. Detection validates and creates the machine instance
        // 2. Async validation builds a snapshot using machine.anchorPosition()
        // 3. Rescan must return UNCHANGED (not INVALID)
        BlockPos expectedAnchor = new BlockPos(TEST_WORLD, 3, -59, 9);

        // Step 1: Detection creates the machine
        StructureSnapshot detectionSnapshot = buildValidSnapshot(expectedAnchor);
        ValidationResult detectionResult = FusionReactorDefinition.INSTANCE.validate(detectionSnapshot);
        assertThat(detectionResult).isInstanceOf(ValidationResult.Valid.class);

        ValidationResult.Valid valid = (ValidationResult.Valid) detectionResult;
        FusionReactorInstance reactor = FusionReactorDefinition.INSTANCE.createInstance(
            MachineId.create(), valid.structure());

        // Step 2: Async validation uses machine.anchorPosition() as origin
        // This must build a valid snapshot since the anchor should be the center
        StructureSnapshot asyncSnapshot = buildValidSnapshot(reactor.anchorPosition());

        // Step 3: Rescan must succeed
        RescanResult rescanResult = reactor.rescan(asyncSnapshot);
        assertThat(rescanResult).isEqualTo(RescanResult.UNCHANGED);
    }
}
