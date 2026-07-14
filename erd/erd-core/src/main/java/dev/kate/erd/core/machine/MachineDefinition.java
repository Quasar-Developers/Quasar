package dev.kate.erd.core.machine;

import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.MachineId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Defines the type and structure of a machine.
 *
 * <p>Machine definitions are templates that describe how to validate, detect,
 * and instantiate machines. Each machine type (reactor, electrolyzer, etc.)
 * has a corresponding definition.
 *
 * <p>All machines are multiblocks. A "single block machine" is a valid
 * multiblock with a 1-block structure.
 *
 * <p>Definitions support upgradeable machines by returning {@link Structure}
 * with metrics that can vary based on structure size. Machines created from
 * the same definition can have different capacities/throughputs based on
 * how large they are built.
 *
 * <p>Thread-safety: Definitions should be immutable after creation.
 *
 * @param <T> the type of machine instance this definition creates
 * @see ValidationResult
 * @see Structure
 */
public interface MachineDefinition<T extends MachineInstance> {

    /**
     * @return the unique type identifier for this machine type
     */
    String typeId();

    /**
     * @return the human-readable display name
     */
    String displayName();

    /**
     * Returns the maximum number of controllers that can be bound to this machine.
     *
     * <p>For critical machines like reactors, this should be 1 to prevent
     * conflicting control signals.
     *
     * @return the maximum controller count (must be >= 1)
     */
    int maxControllers();

    /**
     * Returns the block type key used to detect this machine's controller block.
     * This is the anchor block used to identify the machine's presence.
     *
     * @return the block type key (e.g., "minecraft:iron_block")
     */
    String controllerBlockKey();

    /**
     * Returns the relative bounds for structure detection.
     * The bounds define the region to scan when validating the structure,
     * relative to the anchor/controller block.
     *
     * @return the detection bounds
     */
    StructureBounds detectionBounds();

    /**
     * Validates that a structure snapshot matches this machine definition.
     * This is a pure function with no side effects.
     *
     * @param snapshot the structure to validate
     * @return validation result with success/failure and details
     */
    ValidationResult validate(StructureSnapshot snapshot);

    /**
     * Creates a machine instance from a validated structure.
     * Should only be called after validate() returns success.
     *
     * @param id the unique ID for the new instance
     * @param structure the validated structure
     * @return the new machine instance
     */
    T createInstance(MachineId id, Structure structure);

    /**
     * Returns the endpoint definitions for this machine type.
     * These define where POWER, PIPE, DATA ports can be attached.
     *
     * @return list of port definitions
     */
    List<PortDefinition> portDefinitions();

    /**
     * Represents bounds for structure detection relative to an anchor.
     *
     * @param minX minimum X offset from anchor
     * @param minY minimum Y offset from anchor
     * @param minZ minimum Z offset from anchor
     * @param maxX maximum X offset from anchor
     * @param maxY maximum Y offset from anchor
     * @param maxZ maximum Z offset from anchor
     */
    record StructureBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        /**
         * Creates bounds for a single block at the anchor.
         */
        public static StructureBounds singleBlock() {
            return new StructureBounds(0, 0, 0, 0, 0, 0);
        }

        /**
         * Creates symmetric bounds around the anchor.
         *
         * @param radius the radius in all directions
         */
        public static StructureBounds symmetric(int radius) {
            return new StructureBounds(-radius, -radius, -radius, radius, radius, radius);
        }
    }

    /**
     * Defines a port/endpoint for a machine.
     *
     * @param relativePosition position relative to machine anchor
     * @param layer the network type (POWER/PIPE/DATA)
     * @param role the endpoint role
     * @param name optional name for the port
     */
    record PortDefinition(
            BlockPos relativePosition,
            dev.kate.erd.core.model.ConnectionType layer,
            dev.kate.erd.core.endpoint.EndpointRole role,
            Optional<String> name
    ) {
        public PortDefinition {
            java.util.Objects.requireNonNull(relativePosition);
            java.util.Objects.requireNonNull(layer);
            java.util.Objects.requireNonNull(role);
            name = java.util.Objects.requireNonNullElse(name, Optional.empty());
        }
    }
}
