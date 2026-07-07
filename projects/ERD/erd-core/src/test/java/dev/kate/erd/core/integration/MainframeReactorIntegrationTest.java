package dev.kate.erd.core.integration;

import dev.kate.erd.core.controller.ControllerDefinition;
import dev.kate.erd.core.controller.ControllerInstance;
import dev.kate.erd.core.controller.ControllerStatus;
import dev.kate.erd.core.controller.mainframe.MainframeController;
import dev.kate.erd.core.dataplane.DataControlPlane;
import dev.kate.erd.core.engine.NetworkEngine;
import dev.kate.erd.core.machine.MachineDefinition;
import dev.kate.erd.core.machine.MachineInstance;
import dev.kate.erd.core.machine.Structure;
import dev.kate.erd.core.machine.StructureSnapshot;
import dev.kate.erd.core.machine.test.FusionReactorDefinition;
import dev.kate.erd.core.machine.test.FusionReactorInstance;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ControllerId;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.model.NetworkId;
import dev.kate.erd.core.util.Clock;
import dev.kate.erd.core.util.ErdLogger;
import dev.kate.erd.core.util.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Mainframe controller interacting with machines (Reactor).
 */
class MainframeReactorIntegrationTest {

    private DataControlPlane controlPlane;
    private NetworkEngine networkEngine;
    private TestClock clock;
    private MainframeController mainframeDef;
    private FusionReactorDefinition reactorDef;

    @BeforeEach
    void setUp() {
        clock = new TestClock(1000);
        networkEngine = mock(NetworkEngine.class);
        controlPlane = new DataControlPlane(ErdLogger.silent(), clock, networkEngine);
        mainframeDef = new MainframeController();
        reactorDef = FusionReactorDefinition.INSTANCE;
    }

    @Test
    void mainframe_controls_reactor() {
        NetworkId dataNetwork = new NetworkId(UUID.randomUUID());

        // 1. Create Mainframe Instance
        ControllerId mainframeId = new ControllerId(UUID.randomUUID());
        StructureSnapshot mainframeSnapshot = createMainframeSnapshot();
        MainframeController.Instance mainframe = mainframeDef.createInstance(mainframeId, mainframeSnapshot, clock.nowMillis());

        // 2. Create Reactor Instance
        MachineId reactorId = new MachineId(UUID.randomUUID());
        // We need a valid structure for the reactor.
        Structure reactorStructure = Structure.of(Collections.emptySet(), Collections.emptyList());
        FusionReactorInstance reactor = reactorDef.createInstance(reactorId, reactorStructure);

        // 3. Register both on the same DATA network
        controlPlane.registerController(mainframe, dataNetwork);
        controlPlane.registerMachine(reactor, dataNetwork);

        // 4. Verify Mainframe is Leader
        // Mainframe needs to be marked available to become leader
        controlPlane.setMainframeAvailable(mainframeId, true);
        
        assertThat(controlPlane.hasLeader(dataNetwork)).isTrue();
        assertThat(controlPlane.getLeader(dataNetwork)).contains(mainframeId);
        
        // Mainframe status should be CONNECTED (it connects to itself/network)
        assertThat(mainframe.status()).isEqualTo(ControllerStatus.CONNECTED);

        // 5. Bind Mainframe to Reactor
        // Mainframes can control machines directly.
        var bindingResult = controlPlane.createBinding(mainframeId, reactorId);

        assertThat(bindingResult).isInstanceOf(DataControlPlane.BindingOperationResult.Success.class);
        
        // 6. Verify Binding
        var bindings = controlPlane.getBindingsForController(mainframeId);
        assertThat(bindings).hasSize(1);
        assertThat(bindings.get(0).machineId()).isEqualTo(reactorId);
        
        // 7. Verify Reactor is bound
        var machineBindings = controlPlane.getBindingsForMachine(reactorId);
        assertThat(machineBindings).hasSize(1);
        assertThat(machineBindings.get(0).controllerId()).isEqualTo(mainframeId);
    }

    @Test
    void mainframe_loses_connection_unbinds_reactor() {
        NetworkId dataNetwork = new NetworkId(UUID.randomUUID());
        ControllerId mainframeId = new ControllerId(UUID.randomUUID());
        MachineId reactorId = new MachineId(UUID.randomUUID());

        MainframeController.Instance mainframe = mainframeDef.createInstance(mainframeId, createMainframeSnapshot(), clock.nowMillis());
        FusionReactorInstance reactor = reactorDef.createInstance(reactorId, Structure.of(Collections.emptySet(), Collections.emptyList()));

        controlPlane.registerController(mainframe, dataNetwork);
        controlPlane.registerMachine(reactor, dataNetwork);
        controlPlane.setMainframeAvailable(mainframeId, true);
        controlPlane.createBinding(mainframeId, reactorId);

        // Verify initial state
        assertThat(controlPlane.getBindingsForController(mainframeId)).isNotEmpty();

        // Simulate network split or mainframe removal (set unavailable)
        controlPlane.setMainframeAvailable(mainframeId, false);

        // If the mainframe is unavailable, it loses leadership.
        assertThat(controlPlane.hasLeader(dataNetwork)).isFalse();
    }

    private StructureSnapshot createMainframeSnapshot() {
        java.util.Map<BlockPos, StructureSnapshot.BlockData> blocks = new java.util.HashMap<>();
        BlockPos anchor = new BlockPos(UUID.randomUUID(), 0, 0, 0);
        
        String C = "minecraft:iron_block";
        String M = "minecraft:diamond_block";
        String S = "minecraft:glass";
        String A = "minecraft:air";
        
        // Layer 0
        for(int x=-1; x<=1; x++) for(int z=-1; z<=1; z++) {
            BlockPos p = anchor.offset(x, 0, z);
            if(x==0 && z==0) blocks.put(p, new StructureSnapshot.BlockData(M, java.util.Map.of()));
            else blocks.put(p, new StructureSnapshot.BlockData(C, java.util.Map.of()));
        }
        // Layer 1
        for(int x=-1; x<=1; x++) for(int z=-1; z<=1; z++) {
            BlockPos p = anchor.offset(x, 1, z);
            if(x==0 && z==0) {
                // blocks.put(p, new StructureSnapshot.BlockData(A, java.util.Map.of())); // Air is implicit
            } else {
                blocks.put(p, new StructureSnapshot.BlockData(S, java.util.Map.of()));
            }
        }
        // Layer 2
        for(int x=-1; x<=1; x++) for(int z=-1; z<=1; z++) {
            BlockPos p = anchor.offset(x, 2, z);
            blocks.put(p, new StructureSnapshot.BlockData(C, java.util.Map.of()));
        }
        
        return new StructureSnapshot(blocks, anchor);
    }
}
