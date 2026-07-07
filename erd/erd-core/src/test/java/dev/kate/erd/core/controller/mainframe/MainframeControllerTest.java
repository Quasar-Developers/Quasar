package dev.kate.erd.core.controller.mainframe;

import dev.kate.erd.core.controller.ControllerDefinition;
import dev.kate.erd.core.controller.ControllerStatus;
import dev.kate.erd.core.endpoint.EndpointRole;
import dev.kate.erd.core.machine.StructureSnapshot;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.ControllerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MainframeControllerTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final String CONTROLLER_BLOCK = "minecraft:diamond_block";
    private static final String CASING_BLOCK = "minecraft:iron_block";
    private static final String SCREEN_BLOCK = "minecraft:glass";
    private static final String AIR_BLOCK = "minecraft:air";

    private MainframeController definition;

    @BeforeEach
    void setUp() {
        definition = new MainframeController();
    }

    private BlockPos pos(int x, int y, int z) {
        return new BlockPos(WORLD_ID, x, y, z);
    }

    private StructureSnapshot createSnapshot(boolean complete) {
        Map<BlockPos, StructureSnapshot.BlockData> blocks = new HashMap<>();
        BlockPos anchor = pos(0, 0, 0);

        // Layer 0: Base
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos p = anchor.offset(x, 0, z);
                if (x == 0 && z == 0) {
                    blocks.put(p, new StructureSnapshot.BlockData(CONTROLLER_BLOCK, Map.of()));
                } else {
                    blocks.put(p, new StructureSnapshot.BlockData(CASING_BLOCK, Map.of()));
                }
            }
        }

        // Layer 1: Body
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos p = anchor.offset(x, 1, z);
                if (x == 0 && z == 0) {
                    // blocks.put(p, new StructureSnapshot.BlockData(AIR_BLOCK, Map.of())); // Air is implicit
                } else {
                    blocks.put(p, new StructureSnapshot.BlockData(SCREEN_BLOCK, Map.of()));
                }
            }
        }

        // Layer 2: Top
        if (complete) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos p = anchor.offset(x, 2, z);
                    blocks.put(p, new StructureSnapshot.BlockData(CASING_BLOCK, Map.of()));
                }
            }
        }

        return new StructureSnapshot(blocks, anchor);
    }

    @Test
    void validate_validStructure_success() {
        StructureSnapshot snapshot = createSnapshot(true);
        var result = definition.validate(snapshot);

        assertThat(result).isInstanceOf(ControllerDefinition.ValidationResult.Success.class);
        var success = (ControllerDefinition.ValidationResult.Success) result;
        
        // 9 (base) + 8 (body) + 9 (top) = 26 blocks
        assertThat(success.occupiedPositions()).hasSize(26);
        
        assertThat(success.endpoints()).hasSize(1);
        var endpoint = success.endpoints().get(0);
        assertThat(endpoint.position()).isEqualTo(pos(0, 0, 0));
        assertThat(endpoint.layer()).isEqualTo(ConnectionType.DATA);
        assertThat(endpoint.role()).isEqualTo(EndpointRole.PROVIDER);
    }

    @Test
    void validate_missingTop_fails() {
        StructureSnapshot snapshot = createSnapshot(false);
        var result = definition.validate(snapshot);

        assertThat(result).isInstanceOf(ControllerDefinition.ValidationResult.Failure.class);
    }

    @Test
    void validate_brokenBase_fails() {
        Map<BlockPos, StructureSnapshot.BlockData> brokenBlocks = new HashMap<>();
        BlockPos anchor = pos(0, 0, 0);
        
        // Base with missing corner
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == -1 && z == -1) continue;
                BlockPos p = anchor.offset(x, 0, z);
                if (x == 0 && z == 0) {
                    brokenBlocks.put(p, new StructureSnapshot.BlockData(CONTROLLER_BLOCK, Map.of()));
                } else {
                    brokenBlocks.put(p, new StructureSnapshot.BlockData(CASING_BLOCK, Map.of()));
                }
            }
        }
        
        StructureSnapshot brokenSnapshot = new StructureSnapshot(brokenBlocks, anchor);
        var result = definition.validate(brokenSnapshot);
        
        assertThat(result).isInstanceOf(ControllerDefinition.ValidationResult.Failure.class);
    }

    @Test
    void createInstance_validStructure_createsInstance() {
        StructureSnapshot snapshot = createSnapshot(true);
        ControllerId id = new ControllerId(UUID.randomUUID());
        long now = System.currentTimeMillis();

        MainframeController.Instance instance = definition.createInstance(id, snapshot, now);

        assertThat(instance).isNotNull();
        assertThat(instance.id()).isEqualTo(id);
        assertThat(instance.definition()).isEqualTo(definition);
        assertThat(instance.createdAt()).isEqualTo(now);
        assertThat(instance.status()).isEqualTo(ControllerStatus.NO_SIGNAL);
    }

    @Test
    void instance_lifecycle_transitions() {
        StructureSnapshot snapshot = createSnapshot(true);
        MainframeController.Instance instance = definition.createInstance(new ControllerId(UUID.randomUUID()), snapshot, 1000);

        // Initial state
        assertThat(instance.status()).isEqualTo(ControllerStatus.NO_SIGNAL);
        assertThat(instance.isAvailable()).isFalse();

        // Connect
        instance.onDataConnectionEstablished();
        assertThat(instance.status()).isEqualTo(ControllerStatus.CONNECTED);
        assertThat(instance.isAvailable()).isTrue();

        // Disconnect
        instance.onDataConnectionLost();
        assertThat(instance.status()).isEqualTo(ControllerStatus.NO_SIGNAL);
        assertThat(instance.isAvailable()).isFalse();

        // Remove
        instance.onRemove();
        assertThat(instance.status()).isEqualTo(ControllerStatus.INVALID);
    }
}
