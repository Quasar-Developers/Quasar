package dev.kate.erd.core.machine;

import dev.kate.erd.core.controller.ControllerDefinition;
import dev.kate.erd.core.controller.ControllerInstance;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ControllerId;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.util.ErdLogger;
import dev.kate.erd.core.util.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AddonReloadTest {

    private InstanceManager manager;
    private TestClock clock;

    @BeforeEach
    void setUp() {
        clock = new TestClock(1000);
        manager = new InstanceManager(ErdLogger.silent(), clock);
    }

    @Test
    void reload_preservesMachineState() {
        // 1. Create and register a stateful machine
        MachineId id = MachineId.create();
        BlockPos pos = new BlockPos(UUID.randomUUID(), 0, 0, 0);
        MachineStateful machine = mock(MachineStateful.class);
        MachineDefinition def = mock(MachineDefinition.class);
        Structure structure = Structure.of(Set.of(pos), List.of());

        when(machine.id()).thenReturn(id);
        when(machine.definition()).thenReturn(def);
        when(def.typeId()).thenReturn("test_machine");
        when(machine.anchorPosition()).thenReturn(pos);
        when(machine.structure()).thenReturn(structure);
        when(machine.components()).thenReturn(List.of());
        when(machine.saveState()).thenReturn(Map.of("energy", 5000));

        manager.registerMachine(machine);

        // 2. Simulate Addon Reload (Snapshot & Clear)
        List<MachineSnapshot> snapshots = manager.snapshotAllMachines();
        
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).state()).containsEntry("energy", 5000);
        assertThat(manager.machineCount()).isEqualTo(0); // Registry cleared

        // 3. Restore Phase (Queue state)
        manager.restoreFromSnapshots(snapshots);

        // 4. Re-registration (Simulate detection)
        // Create a NEW instance (simulating re-creation from factory)
        MachineStateful newInstance = mock(MachineStateful.class);
        when(newInstance.id()).thenReturn(id); // Must have same ID
        when(newInstance.definition()).thenReturn(def);
        when(newInstance.structure()).thenReturn(structure);
        when(newInstance.components()).thenReturn(List.of());

        manager.registerMachine(newInstance);

        // 5. Verify state was restored to new instance
        verify(newInstance).restoreState(argThat(map -> map.get("energy").equals(5000)));
    }
}
