package dev.kate.erd.core.dataplane;

import dev.kate.erd.core.controller.BaseControllerInstance;
import dev.kate.erd.core.controller.ControllerDefinition;
import dev.kate.erd.core.controller.ControllerInstance;
import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.machine.MachineDefinition;
import dev.kate.erd.core.machine.StructureSnapshot;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ControllerId;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.model.NetworkId;
import dev.kate.erd.core.util.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for NetworkRegistry (DATA control plane per-network state).
 */
class NetworkRegistryTest {

    private static final UUID WORLD_ID = UUID.randomUUID();

    private NetworkId networkId;
    private NetworkRegistry registry;
    private TestClock clock;

    @BeforeEach
    void setUp() {
        networkId = NetworkId.create();
        registry = new NetworkRegistry(networkId);
        clock = new TestClock(1000);
    }

    private BlockPos pos(int x, int y, int z) {
        return new BlockPos(WORLD_ID, x, y, z);
    }

    // ========== Leader Election Tests ==========

    @Test
    void noMainframes_noLeader() {
        assertThat(registry.hasLeader()).isFalse();
        assertThat(registry.currentLeader()).isEmpty();
    }

    @Test
    void singleMainframe_becomesLeader() {
        ControllerInstance mainframe = createMainframe(clock.nowMillis());
        registry.registerController(mainframe, true);

        assertThat(registry.hasLeader()).isTrue();
        assertThat(registry.currentLeader()).contains(mainframe.id());
    }

    @Test
    void multipleMainframes_earliestCreatedAtWins() {
        // Create mainframe1 first (earlier timestamp)
        ControllerInstance mainframe1 = createMainframe(1000);
        ControllerInstance mainframe2 = createMainframe(2000);

        // Register in reverse order
        registry.registerController(mainframe2, true);
        registry.registerController(mainframe1, true);

        // Earlier one should be leader
        assertThat(registry.currentLeader()).contains(mainframe1.id());
    }

    @Test
    void leaderUnavailable_failoverToNext() {
        ControllerInstance mainframe1 = createMainframe(1000);
        ControllerInstance mainframe2 = createMainframe(2000);

        registry.registerController(mainframe1, true);
        registry.registerController(mainframe2, true);

        // mainframe1 is leader
        assertThat(registry.currentLeader()).contains(mainframe1.id());

        // Mark leader unavailable
        registry.setMainframeAvailable(mainframe1.id(), false);

        // mainframe2 should become leader
        assertThat(registry.currentLeader()).contains(mainframe2.id());
    }

    @Test
    void leaderBecomesAvailableAgain_regainsLeadership() {
        ControllerInstance mainframe1 = createMainframe(1000);
        ControllerInstance mainframe2 = createMainframe(2000);

        registry.registerController(mainframe1, true);
        registry.registerController(mainframe2, true);

        // Make mainframe1 unavailable then available again
        registry.setMainframeAvailable(mainframe1.id(), false);
        assertThat(registry.currentLeader()).contains(mainframe2.id());

        registry.setMainframeAvailable(mainframe1.id(), true);
        assertThat(registry.currentLeader()).contains(mainframe1.id());
    }

    @Test
    void unregisterLeader_failover() {
        ControllerInstance mainframe1 = createMainframe(1000);
        ControllerInstance mainframe2 = createMainframe(2000);

        registry.registerController(mainframe1, true);
        registry.registerController(mainframe2, true);

        registry.unregisterController(mainframe1.id());

        assertThat(registry.currentLeader()).contains(mainframe2.id());
    }

    // ========== Binding Tests ==========

    @Test
    void createBinding_success() {
        ControllerInstance mainframe = createMainframe(clock.nowMillis());
        registry.registerController(mainframe, true);

        ControllerInstance controller = createController(clock.nextTime());
        registry.registerController(controller, false);

        FakeMachineInstance machine = createMachine(3); // maxControllers = 3
        registry.registerMachine(machine);

        var result = registry.createBinding(controller.id(), machine.id(), clock.nowMillis());

        assertThat(result).isInstanceOf(NetworkRegistry.BindingResult.Success.class);

        List<Binding> machineBindings = registry.getBindingsForMachine(machine.id());
        assertThat(machineBindings).hasSize(1);
        assertThat(machineBindings.get(0).controllerId()).isEqualTo(controller.id());
    }

    @Test
    void createBinding_exceedsMaxControllers_fails() {
        ControllerInstance mainframe = createMainframe(clock.nowMillis());
        registry.registerController(mainframe, true);

        ControllerInstance controller1 = createController(clock.nextTime());
        ControllerInstance controller2 = createController(clock.nextTime());
        registry.registerController(controller1, false);
        registry.registerController(controller2, false);

        FakeMachineInstance machine = createMachine(1); // maxControllers = 1 (like reactor)
        registry.registerMachine(machine);

        // First binding succeeds
        var result1 = registry.createBinding(controller1.id(), machine.id(), clock.nowMillis());
        assertThat(result1).isInstanceOf(NetworkRegistry.BindingResult.Success.class);

        // Second binding fails
        var result2 = registry.createBinding(controller2.id(), machine.id(), clock.nowMillis());
        assertThat(result2).isInstanceOf(NetworkRegistry.BindingResult.Failure.class);
    }

    @Test
    void createBinding_duplicateBinding_fails() {
        ControllerInstance mainframe = createMainframe(clock.nowMillis());
        registry.registerController(mainframe, true);

        ControllerInstance controller = createController(clock.nextTime());
        registry.registerController(controller, false);

        FakeMachineInstance machine = createMachine(3);
        registry.registerMachine(machine);

        // First succeeds
        registry.createBinding(controller.id(), machine.id(), clock.nowMillis());

        // Duplicate fails
        var result = registry.createBinding(controller.id(), machine.id(), clock.nowMillis());
        assertThat(result).isInstanceOf(NetworkRegistry.BindingResult.Failure.class);
    }

    @Test
    void createBinding_unregisteredController_fails() {
        ControllerInstance mainframe = createMainframe(clock.nowMillis());
        registry.registerController(mainframe, true);

        FakeMachineInstance machine = createMachine(3);
        registry.registerMachine(machine);

        ControllerId unknownController = ControllerId.create();
        var result = registry.createBinding(unknownController, machine.id(), clock.nowMillis());

        assertThat(result).isInstanceOf(NetworkRegistry.BindingResult.Failure.class);
    }

    @Test
    void removeBinding_success() {
        ControllerInstance mainframe = createMainframe(clock.nowMillis());
        registry.registerController(mainframe, true);

        ControllerInstance controller = createController(clock.nextTime());
        registry.registerController(controller, false);

        FakeMachineInstance machine = createMachine(3);
        registry.registerMachine(machine);

        var result = (NetworkRegistry.BindingResult.Success)
            registry.createBinding(controller.id(), machine.id(), clock.nowMillis());

        boolean removed = registry.removeBinding(result.binding().id());

        assertThat(removed).isTrue();
        assertThat(registry.getBindingsForMachine(machine.id())).isEmpty();
    }

    @Test
    void unregisterController_removesItsBindings() {
        ControllerInstance mainframe = createMainframe(clock.nowMillis());
        registry.registerController(mainframe, true);

        ControllerInstance controller = createController(clock.nextTime());
        registry.registerController(controller, false);

        FakeMachineInstance machine = createMachine(3);
        registry.registerMachine(machine);

        registry.createBinding(controller.id(), machine.id(), clock.nowMillis());
        assertThat(registry.getBindingsForMachine(machine.id())).hasSize(1);

        registry.unregisterController(controller.id());

        assertThat(registry.getBindingsForMachine(machine.id())).isEmpty();
    }

    @Test
    void unregisterMachine_removesItsBindings() {
        ControllerInstance mainframe = createMainframe(clock.nowMillis());
        registry.registerController(mainframe, true);

        ControllerInstance controller = createController(clock.nextTime());
        registry.registerController(controller, false);

        FakeMachineInstance machine = createMachine(3);
        registry.registerMachine(machine);

        registry.createBinding(controller.id(), machine.id(), clock.nowMillis());
        assertThat(registry.getBindingsForController(controller.id())).hasSize(1);

        registry.unregisterMachine(machine.id());

        assertThat(registry.getBindingsForController(controller.id())).isEmpty();
    }

    // ========== Merge/Split Tests ==========

    @Test
    void mergeFrom_combinesRegistries() {
        // Set up registry 1
        ControllerInstance mainframe1 = createMainframe(1000);
        registry.registerController(mainframe1, true);
        FakeMachineInstance machine1 = createMachine(3);
        registry.registerMachine(machine1);

        // Set up registry 2
        NetworkRegistry other = new NetworkRegistry(NetworkId.create());
        ControllerInstance mainframe2 = createMainframe(2000);
        other.registerController(mainframe2, true);
        FakeMachineInstance machine2 = createMachine(3);
        other.registerMachine(machine2);

        // Merge
        registry.mergeFrom(other);

        // Should have both mainframes, earlier one as leader
        assertThat(registry.allMainframeIds()).containsExactlyInAnyOrder(
            mainframe1.id(), mainframe2.id());
        assertThat(registry.currentLeader()).contains(mainframe1.id());

        // Should have both machines
        assertThat(registry.allMachineIds()).containsExactlyInAnyOrder(
            machine1.id(), machine2.id());
    }

    @Test
    void splitTo_partitionsCorrectly() {
        ControllerInstance mainframe = createMainframe(clock.nowMillis());
        ControllerInstance controller = createController(clock.nextTime());
        registry.registerController(mainframe, true);
        registry.registerController(controller, false);

        FakeMachineInstance machine1 = createMachine(3);
        FakeMachineInstance machine2 = createMachine(3);
        registry.registerMachine(machine1);
        registry.registerMachine(machine2);

        // Create binding between controller and machine1
        registry.createBinding(controller.id(), machine1.id(), clock.nowMillis());

        // Split: controller and machine1 go to new network
        NetworkId newNetworkId = NetworkId.create();
        NetworkRegistry newRegistry = registry.splitTo(
            newNetworkId,
            Set.of(controller.id()),
            Set.of(machine1.id())
        );

        // Original should have mainframe and machine2
        assertThat(registry.hasController(mainframe.id())).isTrue();
        assertThat(registry.hasController(controller.id())).isFalse();
        assertThat(registry.hasMachine(machine2.id())).isTrue();
        assertThat(registry.hasMachine(machine1.id())).isFalse();

        // New registry should have controller and machine1
        assertThat(newRegistry.hasController(controller.id())).isTrue();
        assertThat(newRegistry.hasMachine(machine1.id())).isTrue();

        // Binding should be in new registry
        assertThat(newRegistry.getBindingsForMachine(machine1.id())).hasSize(1);
    }

    // ========== Helper Methods ==========

    private ControllerInstance createMainframe(long createdAt) {
        return new FakeControllerInstance(ControllerId.create(), true, createdAt);
    }

    private ControllerInstance createController(long createdAt) {
        return new FakeControllerInstance(ControllerId.create(), false, createdAt);
    }

    private FakeMachineInstance createMachine(int maxControllers) {
        return new FakeMachineInstance(MachineId.create(), maxControllers);
    }

    /**
     * Fake controller instance for testing.
     */
    private class FakeControllerInstance implements ControllerInstance {
        private final ControllerId id;
        private final boolean isMainframe;
        private final long createdAt;

        FakeControllerInstance(ControllerId id, boolean isMainframe, long createdAt) {
            this.id = id;
            this.isMainframe = isMainframe;
            this.createdAt = createdAt;
        }

        @Override public ControllerId id() { return id; }
        @Override public ControllerDefinition<?> definition() {
            return new FakeControllerDefinition(isMainframe);
        }
        @Override public BlockPos anchorPosition() { return pos(0, 0, 0); }
        @Override public Set<BlockPos> occupiedPositions() { return Set.of(pos(0, 0, 0)); }
        @Override public List<Endpoint> endpoints() { return List.of(); }
        @Override public long createdAt() { return createdAt; }
        @Override public dev.kate.erd.core.controller.ControllerStatus status() {
            return dev.kate.erd.core.controller.ControllerStatus.CONNECTED;
        }
        @Override public boolean isAvailable() { return true; }
        @Override public void tick() {}
        @Override public void onDataConnectionEstablished() {}
        @Override public void onDataConnectionLost() {}
        @Override public void onMachineBound(MachineId machineId) {}
        @Override public void onMachineUnbound(MachineId machineId) {}
        @Override public boolean revalidate(StructureSnapshot snapshot) { return true; }
        @Override public void onRemove() {}
    }

    private static class FakeControllerDefinition implements ControllerDefinition<ControllerInstance> {
        private final boolean isMainframe;

        FakeControllerDefinition(boolean isMainframe) {
            this.isMainframe = isMainframe;
        }

        @Override public String typeId() { return isMainframe ? "mainframe" : "panel"; }
        @Override public String displayName() { return typeId(); }
        @Override public int maxMachines() { return 10; }
        @Override public boolean isMainframe() { return isMainframe; }
        @Override public String controllerBlockKey() { return "test:controller"; }
        @Override public MachineDefinition.StructureBounds detectionBounds() {
            return MachineDefinition.StructureBounds.singleBlock();
        }
        @Override public ControllerDefinition.ValidationResult validate(StructureSnapshot snapshot) {
            return new ControllerDefinition.ValidationResult.Success(Set.of(), List.of());
        }
        @Override public ControllerInstance createInstance(ControllerId id, StructureSnapshot snapshot, long createdAt) {
            return null;
        }
        @Override public List<MachineDefinition.PortDefinition> portDefinitions() { return List.of(); }
    }

    /**
     * Fake machine instance for testing.
     */
    private class FakeMachineInstance implements dev.kate.erd.core.machine.MachineInstance {
        private final MachineId id;
        private final int maxControllers;
        private final dev.kate.erd.core.machine.Structure structure;

        FakeMachineInstance(MachineId id, int maxControllers) {
            this.id = id;
            this.maxControllers = maxControllers;
            this.structure = dev.kate.erd.core.machine.Structure.of(Set.of(pos(0, 0, 0)), List.of());
        }

        @Override public MachineId id() { return id; }
        @Override public MachineDefinition<?> definition() {
            return new FakeMachineDefinition(maxControllers);
        }
        @Override public BlockPos anchorPosition() { return pos(0, 0, 0); }
        @Override public dev.kate.erd.core.machine.Structure structure() { return structure; }
        @Override public void updateStructure(dev.kate.erd.core.machine.Structure newStructure) {}
        @Override public dev.kate.erd.core.machine.RescanResult rescan(StructureSnapshot snapshot) {
            return dev.kate.erd.core.machine.RescanResult.UNCHANGED;
        }
        @Override public void onStructureChanged(dev.kate.erd.core.machine.Structure oldStructure, dev.kate.erd.core.machine.Structure newStructure) {}
        @Override public List<dev.kate.erd.core.machine.component.MachineComponent> components() { return List.of(); }
        @Override public java.util.Optional<dev.kate.erd.core.machine.component.MachineComponent> getComponent(dev.kate.erd.core.machine.component.ComponentId id) { return java.util.Optional.empty(); }
        @Override public void attachComponent(dev.kate.erd.core.machine.component.MachineComponent component) {}
        @Override public java.util.Optional<dev.kate.erd.core.machine.component.MachineComponent> detachComponent(dev.kate.erd.core.machine.component.ComponentId id) { return java.util.Optional.empty(); }
        @Override public void onComponentStructureChanged(dev.kate.erd.core.machine.component.MachineComponent component, dev.kate.erd.core.machine.Structure oldStructure, dev.kate.erd.core.machine.Structure newStructure) {}
        @Override public dev.kate.erd.core.machine.MachineStatus status() {
            return dev.kate.erd.core.machine.MachineStatus.IDLE;
        }
        @Override public void tick() {}
        @Override public void onControlLinkEstablished(ControllerId controllerId) {}
        @Override public void onControlLinkLost(ControllerId controllerId) {}
        @Override public void onRemove() {}
    }

    private static class FakeMachineDefinition implements MachineDefinition<dev.kate.erd.core.machine.MachineInstance> {
        private final int maxControllers;

        FakeMachineDefinition(int maxControllers) {
            this.maxControllers = maxControllers;
        }

        @Override public String typeId() { return "test_machine"; }
        @Override public String displayName() { return "Test Machine"; }
        @Override public int maxControllers() { return maxControllers; }
        @Override public String controllerBlockKey() { return "test:machine"; }
        @Override public StructureBounds detectionBounds() { return StructureBounds.singleBlock(); }
        @Override public dev.kate.erd.core.machine.ValidationResult validate(StructureSnapshot snapshot) {
            return dev.kate.erd.core.machine.ValidationResult.valid(Set.of(), List.of());
        }
        @Override public dev.kate.erd.core.machine.MachineInstance createInstance(MachineId id, dev.kate.erd.core.machine.Structure structure) {
            return null;
        }
        @Override public List<PortDefinition> portDefinitions() { return List.of(); }
    }
}
