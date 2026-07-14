package dev.kate.erd.core.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for core model classes.
 */
class ModelTest {

    private static final UUID WORLD_ID = UUID.randomUUID();

    // ========== BlockPos Tests ==========

    @Test
    void blockPos_immutable() {
        BlockPos pos = new BlockPos(WORLD_ID, 1, 2, 3);

        assertThat(pos.worldId()).isEqualTo(WORLD_ID);
        assertThat(pos.x()).isEqualTo(1);
        assertThat(pos.y()).isEqualTo(2);
        assertThat(pos.z()).isEqualTo(3);
    }

    @Test
    void blockPos_nullWorldId_throws() {
        assertThatThrownBy(() -> new BlockPos(null, 0, 0, 0))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blockPos_offset_createsNewPosition() {
        BlockPos pos = new BlockPos(WORLD_ID, 0, 0, 0);

        BlockPos offset = pos.offset(1, 2, 3);

        assertThat(offset.x()).isEqualTo(1);
        assertThat(offset.y()).isEqualTo(2);
        assertThat(offset.z()).isEqualTo(3);
        assertThat(offset.worldId()).isEqualTo(WORLD_ID);

        // Original unchanged
        assertThat(pos.x()).isEqualTo(0);
    }

    @Test
    void blockPos_adjacent_allDirections() {
        BlockPos center = new BlockPos(WORLD_ID, 0, 0, 0);

        assertThat(center.adjacent(Direction.EAST)).isEqualTo(new BlockPos(WORLD_ID, 1, 0, 0));
        assertThat(center.adjacent(Direction.WEST)).isEqualTo(new BlockPos(WORLD_ID, -1, 0, 0));
        assertThat(center.adjacent(Direction.UP)).isEqualTo(new BlockPos(WORLD_ID, 0, 1, 0));
        assertThat(center.adjacent(Direction.DOWN)).isEqualTo(new BlockPos(WORLD_ID, 0, -1, 0));
        assertThat(center.adjacent(Direction.SOUTH)).isEqualTo(new BlockPos(WORLD_ID, 0, 0, 1));
        assertThat(center.adjacent(Direction.NORTH)).isEqualTo(new BlockPos(WORLD_ID, 0, 0, -1));
    }

    @Test
    void blockPos_toChunkKey_calculatesCorrectly() {
        // Block at (16, 64, 32) should be in chunk (1, 2)
        BlockPos pos = new BlockPos(WORLD_ID, 16, 64, 32);
        ChunkKey chunk = pos.toChunkKey();

        assertThat(chunk.chunkX()).isEqualTo(1);
        assertThat(chunk.chunkZ()).isEqualTo(2);
        assertThat(chunk.worldId()).isEqualTo(WORLD_ID);
    }

    @Test
    void blockPos_toChunkKey_negativeCoordinates() {
        // Block at (-1, 0, -1) should be in chunk (-1, -1)
        BlockPos pos = new BlockPos(WORLD_ID, -1, 0, -1);
        ChunkKey chunk = pos.toChunkKey();

        assertThat(chunk.chunkX()).isEqualTo(-1);
        assertThat(chunk.chunkZ()).isEqualTo(-1);
    }

    @Test
    void blockPos_chunkLocal_calculatesCorrectly() {
        BlockPos pos = new BlockPos(WORLD_ID, 17, 64, 35);

        assertThat(pos.chunkLocalX()).isEqualTo(1); // 17 % 16 = 1
        assertThat(pos.chunkLocalZ()).isEqualTo(3); // 35 % 16 = 3
    }

    @Test
    void blockPos_equality() {
        BlockPos a = new BlockPos(WORLD_ID, 1, 2, 3);
        BlockPos b = new BlockPos(WORLD_ID, 1, 2, 3);
        BlockPos c = new BlockPos(WORLD_ID, 1, 2, 4);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    // ========== ChunkKey Tests ==========

    @Test
    void chunkKey_bounds() {
        ChunkKey chunk = new ChunkKey(WORLD_ID, 1, 2);

        assertThat(chunk.minBlockX()).isEqualTo(16);
        assertThat(chunk.maxBlockX()).isEqualTo(31);
        assertThat(chunk.minBlockZ()).isEqualTo(32);
        assertThat(chunk.maxBlockZ()).isEqualTo(47);
    }

    @Test
    void chunkKey_contains() {
        ChunkKey chunk = new ChunkKey(WORLD_ID, 1, 2);

        assertThat(chunk.contains(new BlockPos(WORLD_ID, 16, 64, 32))).isTrue();
        assertThat(chunk.contains(new BlockPos(WORLD_ID, 31, 0, 47))).isTrue();
        assertThat(chunk.contains(new BlockPos(WORLD_ID, 15, 64, 32))).isFalse();
        assertThat(chunk.contains(new BlockPos(WORLD_ID, 32, 64, 32))).isFalse();
    }

    @Test
    void chunkKey_contains_differentWorld() {
        ChunkKey chunk = new ChunkKey(WORLD_ID, 1, 2);
        UUID otherWorld = UUID.randomUUID();

        assertThat(chunk.contains(new BlockPos(otherWorld, 16, 64, 32))).isFalse();
    }

    // ========== Direction Tests ==========

    @Test
    void direction_opposite() {
        assertThat(Direction.EAST.opposite()).isEqualTo(Direction.WEST);
        assertThat(Direction.WEST.opposite()).isEqualTo(Direction.EAST);
        assertThat(Direction.UP.opposite()).isEqualTo(Direction.DOWN);
        assertThat(Direction.DOWN.opposite()).isEqualTo(Direction.UP);
        assertThat(Direction.NORTH.opposite()).isEqualTo(Direction.SOUTH);
        assertThat(Direction.SOUTH.opposite()).isEqualTo(Direction.NORTH);
    }

    @Test
    void direction_allValues() {
        assertThat(Direction.ALL).hasSize(6);
        assertThat(Direction.ALL).containsExactlyInAnyOrder(
            Direction.EAST, Direction.WEST,
            Direction.UP, Direction.DOWN,
            Direction.NORTH, Direction.SOUTH
        );
    }

    // ========== Identifier Tests ==========

    @Test
    void networkId_create_unique() {
        NetworkId a = NetworkId.create();
        NetworkId b = NetworkId.create();

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void networkId_parse() {
        String uuidStr = "550e8400-e29b-41d4-a716-446655440000";
        NetworkId id = NetworkId.parse(uuidStr);

        assertThat(id.toString()).isEqualTo(uuidStr);
    }

    @Test
    void machineId_nullId_throws() {
        assertThatThrownBy(() -> new MachineId(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void controllerId_nullId_throws() {
        assertThatThrownBy(() -> new ControllerId(null))
            .isInstanceOf(NullPointerException.class);
    }

    // ========== ConnectionType Tests ==========

    @Test
    void ConnectionType_allValues() {
        assertThat(ConnectionType.values()).containsExactly(
            ConnectionType.POWER, ConnectionType.PIPE, ConnectionType.DATA
        );
    }

    // ========== PipeFamily Tests ==========

    @Test
    void pipeFamily_allValues() {
        assertThat(PipeFamily.values()).containsExactly(
            PipeFamily.FLUID, PipeFamily.GAS, PipeFamily.UNASSIGNED
        );
    }
}
