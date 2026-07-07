package dev.kate.erd.core.dataplane;

import dev.kate.erd.core.controller.ControllerDefinition;
import dev.kate.erd.core.controller.ControllerInstance;
import dev.kate.erd.core.engine.NetworkEngine;
import dev.kate.erd.core.machine.MachineDefinition;
import dev.kate.erd.core.machine.MachineInstance;
import dev.kate.erd.core.model.*;
import dev.kate.erd.core.util.Clock;
import dev.kate.erd.core.util.ErdLogger;
import dev.kate.erd.core.util.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DataControlPlaneTest {

    private DataControlPlane controlPlane;
    private NetworkEngine engine;
    private TestClock clock;

    @BeforeEach
    void setUp() {
        clock = new TestClock(1000);
        engine = mock(NetworkEngine.class);
        controlPlane = new DataControlPlane(ErdLogger.silent(), clock, engine);
    }

    @Test
    void registerController_addsToRegistry() {
        ControllerInstance controller = mock(ControllerInstance.class);
        ControllerDefinition def = mock(ControllerDefinition.class);
        ControllerId id = ControllerId.create();
        NetworkId netId = NetworkId.create();

        when(controller.id()).thenReturn(id);
        when(controller.definition()).thenReturn(def);
        when(def.isMainframe()).thenReturn(false);

        controlPlane.registerController(controller, netId);

        assertThat(controlPlane.getControllerNetwork(id)).isPresent();
        assertThat(controlPlane.getControllerNetwork(id).get()).isEqualTo(netId);
        assertThat(controlPlane.getControllersOnNetwork(netId)).contains(id);
    }

    @Test
    void registerMainframe_electsLeader() {
        ControllerInstance mainframe = mock(ControllerInstance.class);
        ControllerDefinition def = mock(ControllerDefinition.class);
        ControllerId id = ControllerId.create();
        NetworkId netId = NetworkId.create();

        when(mainframe.id()).thenReturn(id);
        when(mainframe.definition()).thenReturn(def);
        when(def.isMainframe()).thenReturn(true);
        when(mainframe.createdAt()).thenReturn(1000L);

        controlPlane.registerController(mainframe, netId);
        controlPlane.setMainframeAvailable(id, true);

        assertThat(controlPlane.hasLeader(netId)).isTrue();
        assertThat(controlPlane.getLeader(netId)).isPresent();
        assertThat(controlPlane.getLeader(netId).get()).isEqualTo(id);
    }

    @Test
    void leaderElection_oldestWins() {
        NetworkId netId = NetworkId.create();

        // Mainframe 1 (Newer)
        ControllerInstance m1 = createMainframe(2000L);
        controlPlane.registerController(m1, netId);
        controlPlane.setMainframeAvailable(m1.id(), true);

        // Mainframe 2 (Older)
        ControllerInstance m2 = createMainframe(1000L);
        controlPlane.registerController(m2, netId);
        controlPlane.setMainframeAvailable(m2.id(), true);

        // M2 should be leader because it's older (smaller timestamp)
        assertThat(controlPlane.getLeader(netId).get()).isEqualTo(m2.id());
    }

    private ControllerInstance createMainframe(long creationTime) {
        ControllerInstance c = mock(ControllerInstance.class);
        ControllerDefinition def = mock(ControllerDefinition.class);
        ControllerId id = ControllerId.create();
        
        when(c.id()).thenReturn(id);
        when(c.definition()).thenReturn(def);
        when(def.isMainframe()).thenReturn(true);
        when(c.createdAt()).thenReturn(creationTime);
        
        return c;
    }

    @Test
    void createBinding_success() {
        NetworkId netId = NetworkId.create();
        
        // Setup leader
        ControllerInstance mainframe = createMainframe(1000L);
        controlPlane.registerController(mainframe, netId);
        controlPlane.setMainframeAvailable(mainframe.id(), true);

        // Setup controller and machine
        ControllerInstance controller = mock(ControllerInstance.class);
        ControllerDefinition controllerDef = mock(ControllerDefinition.class);
        when(controller.id()).thenReturn(ControllerId.create());
        when(controller.definition()).thenReturn(controllerDef);
        when(controllerDef.maxMachines()).thenReturn(1); // Ensure max bindings > 0
        controlPlane.registerController(controller, netId);

        MachineInstance machine = mock(MachineInstance.class);
        MachineDefinition machineDef = mock(MachineDefinition.class);
        when(machine.id()).thenReturn(MachineId.create());
        when(machine.definition()).thenReturn(machineDef);
        when(machineDef.maxControllers()).thenReturn(1); // Ensure max controllers > 0
        controlPlane.registerMachine(machine, netId);

        // Create binding
        var result = controlPlane.createBinding(controller.id(), machine.id());

        assertThat(result).isInstanceOf(DataControlPlane.BindingOperationResult.Success.class);
        assertThat(controlPlane.getBindingsForController(controller.id())).hasSize(1);
    }
}
