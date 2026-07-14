package dev.kate.erd.core.controller.mainframe;

import dev.kate.erd.core.controller.BaseControllerInstance;
import dev.kate.erd.core.controller.ControllerDefinition;
import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.endpoint.EndpointRole;
import dev.kate.erd.core.machine.MachineDefinition;
import dev.kate.erd.core.machine.StructureSnapshot;
import dev.kate.erd.core.machine.structure.StructurePattern;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.ControllerId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The Mainframe controller definition and instance.
 *
 * <p>The Mainframe is a special controller that acts as the authority for the DATA network.
 * It is a static 3x3x3 multiblock structure.
 *
 * <p>Structure Pattern:
 * <pre>
 * Layer 0 (Bottom):
 *   C C C
 *   C @ C  (@ = Controller/Anchor)
 *   C C C
 *
 * Layer 1 (Middle):
 *   S S S
 *   S A S  (A = Air)
 *   S S S
 *
 * Layer 2 (Top):
 *   C C C
 *   C C C
 *   C C C
 * </pre>
 *
 * <p>Key:
 * <ul>
 *   <li>C: Casing (minecraft:iron_block)</li>
 *   <li>@: Controller (minecraft:diamond_block)</li>
 *   <li>S: Screen/Glass (minecraft:glass)</li>
 * </ul>
 */
public class MainframeController implements ControllerDefinition<MainframeController.Instance> {

    public static final String TYPE_ID = "erd:mainframe";
    private static final String CONTROLLER_BLOCK = "minecraft:diamond_block";
    private static final String CASING_BLOCK = "minecraft:iron_block";
    private static final String SCREEN_BLOCK = "minecraft:glass";

    private final StructurePattern pattern;

    public MainframeController() {
        this.pattern = StructurePattern.builder()
                // Layer 0
                .layer("CCC")
                .layer("C@C")
                .layer("CCC")
                .nextLayer()
                // Layer 1
                .layer("SSS")
                .layer("S S")
                .layer("SSS")
                .nextLayer()
                // Layer 2
                .layer("CCC")
                .layer("CCC")
                .layer("CCC")
                .key('C', CASING_BLOCK)
                .key('@', CONTROLLER_BLOCK)
                .key('S', SCREEN_BLOCK)
                // .key(' ', "minecraft:air") // Removed explicit air key mapping
                .endpoint('@', ConnectionType.DATA, EndpointRole.PROVIDER)
                .build();
    }

    @Override
    public String typeId() {
        return TYPE_ID;
    }

    @Override
    public String displayName() {
        return "Mainframe";
    }

    @Override
    public int maxMachines() {
        return 100;
    }

    @Override
    public boolean isMainframe() {
        return true;
    }

    @Override
    public String controllerBlockKey() {
        return CONTROLLER_BLOCK;
    }

    @Override
    public MachineDefinition.StructureBounds detectionBounds() {
        return pattern.getDetectionBounds();
    }

    @Override
    public ValidationResult validate(StructureSnapshot snapshot) {
        dev.kate.erd.core.machine.ValidationResult result = pattern.validate(snapshot);
        
        if (result instanceof dev.kate.erd.core.machine.ValidationResult.Valid valid) {
            return new ValidationResult.Success(valid.positions(), valid.structure().endpoints());
        } else if (result instanceof dev.kate.erd.core.machine.ValidationResult.Invalid invalid) {
            return new ValidationResult.Failure(invalid.reason(), invalid.problemPositions());
        }
        throw new IllegalStateException("Unknown validation result type");
    }

    @Override
    public Instance createInstance(ControllerId id, StructureSnapshot snapshot, long createdAt) {
        ValidationResult result = validate(snapshot);
        if (result instanceof ValidationResult.Failure failure) {
            throw new IllegalArgumentException("Invalid structure: " + failure.reason());
        }
        ValidationResult.Success success = (ValidationResult.Success) result;
        return new Instance(id, this, snapshot.origin(), success.occupiedPositions(), success.endpoints(), createdAt);
    }

    @Override
    public List<MachineDefinition.PortDefinition> portDefinitions() {
        return List.of(
            new MachineDefinition.PortDefinition(
                new BlockPos(new java.util.UUID(0, 0), 0, 0, 0), 
                ConnectionType.DATA, 
                EndpointRole.PROVIDER, 
                Optional.of("data_port"))
        );
    }

    public static class Instance extends BaseControllerInstance {

        protected Instance(ControllerId id, ControllerDefinition<?> definition, BlockPos anchorPosition, Set<BlockPos> occupiedPositions, List<Endpoint> endpoints, long createdAt) {
            super(id, definition, anchorPosition, occupiedPositions, endpoints, createdAt);
        }

        @Override
        protected void doTick() {
            // Mainframe logic
        }
    }
}
