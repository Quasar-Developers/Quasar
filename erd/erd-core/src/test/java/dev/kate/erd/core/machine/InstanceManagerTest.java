package dev.kate.erd.core.machine;

import dev.kate.erd.core.controller.ControllerDefinition;
import dev.kate.erd.core.controller.ControllerInstance;
import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ControllerId;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.util.ErdLogger;
import dev.kate.erd.core.util.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InstanceManagerTest {

    private InstanceManager manager;
    private TestClock clock;

    @BeforeEach
    void setUp() {
        clock = new TestClock(1000);
        manager = new InstanceManager(ErdLogger.silent(), clock);
    }

    @Test
    void registerMachine_storesInstanceAndPositions() {
        MachineInstance machine = mock(MachineInstance.class);
        MachineId id = MachineId.create();
        BlockPos pos = new BlockPos(UUID.randomUUID(), 0, 0, 0);

        when(machine.id()).thenReturn(id);
        when(machine.occupiedPositions()).thenReturn(Set.of(pos));
        when(machine.endpoints()).thenReturn(List.of());

        manager.registerMachine(machine);

        assertThat(manager.getMachine(id)).isPresent();
        assertThat(manager.getMachineAt(pos)).isPresent();
        assertThat(manager.isOccupied(pos)).isTrue();
    }

    @Test
    void removeMachine_clearsInstanceAndPositions() {
        MachineInstance machine = mock(MachineInstance.class);
        MachineId id = MachineId.create();
        BlockPos pos = new BlockPos(UUID.randomUUID(), 0, 0, 0);

        when(machine.id()).thenReturn(id);
        when(machine.occupiedPositions()).thenReturn(Set.of(pos));
        when(machine.endpoints()).thenReturn(List.of());

        manager.registerMachine(machine);
        manager.removeMachine(machine);

        assertThat(manager.getMachine(id)).isEmpty();
        assertThat(manager.getMachineAt(pos)).isEmpty();
        assertThat(manager.isOccupied(pos)).isFalse();
    }

    @Test
    void snapshotAllMachines_returnsSnapshotsAndClearsRegistry() {
        MachineInstance machine = mock(MachineInstance.class);
        MachineId id = MachineId.create();
        BlockPos pos = new BlockPos(UUID.randomUUID(), 0, 0, 0);
        MachineDefinition def = mock(MachineDefinition.class);
        Structure structure = Structure.of(Set.of(pos), List.of());

        when(machine.id()).thenReturn(id);
        when(machine.definition()).thenReturn(def);
        when(def.typeId()).thenReturn("test_machine");
        when(machine.anchorPosition()).thenReturn(pos);
        when(machine.structure()).thenReturn(structure);
        when(machine.components()).thenReturn(List.of());

        manager.registerMachine(machine);

        List<MachineSnapshot> snapshots = manager.snapshotAllMachines();

        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).id()).isEqualTo(id);
        
        // Registry should be empty
        assertThat(manager.machineCount()).isEqualTo(0);
        assertThat(manager.isOccupied(pos)).isFalse();
    }

    @Test
    void restoreFromSnapshots_queuesStateForRestore() {
        MachineId id = MachineId.create();
        BlockPos pos = new BlockPos(UUID.randomUUID(), 0, 0, 0);
        MachineSnapshot snapshot = MachineSnapshot.builder()
            .id(id)
            .typeId("test")
            .anchorPosition(pos)
            .state(java.util.Map.of("key", "value"))
            .build();

        manager.restoreFromSnapshots(List.of(snapshot));

        // Now register a machine with that ID
        MachineStateful machine = mock(MachineStateful.class);
        MachineDefinition def = mock(MachineDefinition.class);
        Structure structure = Structure.of(Set.of(pos), List.of());

        when(machine.id()).thenReturn(id);
        when(machine.definition()).thenReturn(def);
        when(machine.structure()).thenReturn(structure);
        when(machine.anchorPosition()).thenReturn(pos);
        when(machine.components()).thenReturn(List.of());

        manager.registerMachine(machine);

        // Verify restoreState was called
        verify(machine).restoreState(snapshot.state());
    }

    @Test
    void getMachineForEndpoint_returnsOwningMachine() {
        MachineInstance machine = mock(MachineInstance.class);
        MachineId id = MachineId.create();
        BlockPos machinePos = new BlockPos(UUID.randomUUID(), 0, 0, 0);
        BlockPos endpointPos = new BlockPos(machinePos.worldId(), 1, 0, 0);
        Endpoint endpoint = mock(Endpoint.class);

        when(machine.id()).thenReturn(id);
        when(machine.occupiedPositions()).thenReturn(Set.of(machinePos));
        when(machine.endpoints()).thenReturn(List.of(endpoint));
        when(endpoint.position()).thenReturn(endpointPos);

        manager.registerMachine(machine);

        // Verify we can look up the machine by endpoint
        Optional<MachineInstance> result = manager.getMachineForEndpoint(endpoint);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(machine);
    }

    @Test
    void getMachineForEndpoint_returnsEmptyForUnknownEndpoint() {
        Endpoint endpoint = mock(Endpoint.class);
        
        Optional<MachineInstance> result = manager.getMachineForEndpoint(endpoint);
        assertThat(result).isEmpty();
    }

    @Test
    void removeMachine_clearsEndpointToMachineIndex() {
        MachineInstance machine = mock(MachineInstance.class);
        MachineId id = MachineId.create();
        BlockPos machinePos = new BlockPos(UUID.randomUUID(), 0, 0, 0);
        BlockPos endpointPos = new BlockPos(machinePos.worldId(), 1, 0, 0);
        Endpoint endpoint = mock(Endpoint.class);

        when(machine.id()).thenReturn(id);
        when(machine.occupiedPositions()).thenReturn(Set.of(machinePos));
        when(machine.endpoints()).thenReturn(List.of(endpoint));
        when(endpoint.position()).thenReturn(endpointPos);

        manager.registerMachine(machine);
        assertThat(manager.getMachineForEndpoint(endpoint)).isPresent();

        manager.removeMachine(machine);
        assertThat(manager.getMachineForEndpoint(endpoint)).isEmpty();
    }
}
