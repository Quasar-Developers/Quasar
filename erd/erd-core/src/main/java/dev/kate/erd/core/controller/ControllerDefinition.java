package dev.kate.erd.core.controller;

import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.machine.MachineDefinition;
import dev.kate.erd.core.machine.StructureSnapshot;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ControllerId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Defines the type and structure of a controller.
 *
 * <p>Controller definitions describe how to validate, detect, and instantiate
 * controllers. Controllers monitor and control machines (panels, control rooms,
 * terminals). A Mainframe is a special type of Controller with authority over
 * the DATA network.
 *
 * <p>All controllers are multiblocks. A "single block controller" is a valid
 * multiblock with a 1-block structure.
 *
 * <p>Thread-safety: Definitions should be immutable after creation.
 *
 * @param <T> the type of controller instance this definition creates
 */
public interface ControllerDefinition<T extends ControllerInstance> {

    /**
     * @return the unique type identifier for this controller type
     */
    String typeId();

    /**
     * @return the human-readable display name
     */
    String displayName();

    /**
     * Returns the maximum number of machines this controller can be bound to.
     *
     * @return the maximum machine count (must be >= 1)
     */
    int maxMachines();

    /**
     * Returns whether this controller type is a Mainframe.
     * Mainframes have special authority over the DATA network.
     *
     * @return true if this is a mainframe type
     */
    boolean isMainframe();

    /**
     * Returns the block type key used to detect this controller's anchor block.
     *
     * @return the block type key (e.g., "minecraft:diamond_block")
     */
    String controllerBlockKey();

    /**
     * Returns the relative bounds for structure detection.
     *
     * @return the detection bounds
     */
    MachineDefinition.StructureBounds detectionBounds();

    /**
     * Validates that a structure snapshot matches this controller definition.
     *
     * @param snapshot the structure to validate
     * @return validation result
     */
    ValidationResult validate(StructureSnapshot snapshot);

    /**
     * Creates a controller instance from a validated structure.
     *
     * @param id the unique ID for the new instance
     * @param snapshot the validated structure snapshot
     * @param createdAt timestamp for leader election ordering
     * @return the new controller instance
     */
    T createInstance(ControllerId id, StructureSnapshot snapshot, long createdAt);

    /**
     * Returns the endpoint definitions for this controller type.
     *
     * @return list of port definitions
     */
    List<MachineDefinition.PortDefinition> portDefinitions();

    /**
     * Result of structure validation.
     */
    sealed interface ValidationResult {

        /**
         * Validation succeeded.
         */
        record Success(
                Set<BlockPos> occupiedPositions,
                List<Endpoint> endpoints
        ) implements ValidationResult {
            public Success {
                occupiedPositions = Set.copyOf(occupiedPositions);
                endpoints = List.copyOf(endpoints);
            }
        }

        /**
         * Validation failed.
         */
        record Failure(
                String reason,
                Set<BlockPos> problemPositions
        ) implements ValidationResult {
            public Failure(String reason) {
                this(reason, Set.of());
            }

            public Failure {
                problemPositions = Set.copyOf(problemPositions);
            }
        }
    }
}
