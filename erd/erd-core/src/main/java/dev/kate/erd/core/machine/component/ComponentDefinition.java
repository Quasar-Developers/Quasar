package dev.kate.erd.core.machine.component;

import dev.kate.erd.core.machine.MachineInstance;
import dev.kate.erd.core.machine.Structure;
import dev.kate.erd.core.machine.StructureSnapshot;
import dev.kate.erd.core.machine.ValidationResult;
import dev.kate.erd.core.model.BlockPos;

import java.util.Set;

/**
 * Defines the type and structure of a machine component.
 *
 * <p>Component definitions are templates that describe how to validate, detect,
 * and instantiate components. Each component type (power laser, cooling system, etc.)
 * has a corresponding definition.
 *
 * <p>Components differ from machines in that:
 * <ul>
 *   <li>They must attach to a compatible parent machine</li>
 *   <li>They are validated relative to an attachment point</li>
 *   <li>They share the parent's lifecycle (tick, save, remove)</li>
 * </ul>
 *
 * <p>Example implementation:
 * <pre>{@code
 * public class PowerLaserDefinition implements ComponentDefinition<PowerLaserComponent> {
 *
 *     @Override
 *     public String componentTypeId() {
 *         return "erd:power_laser";
 *     }
 *
 *     @Override
 *     public Set<String> compatibleMachineTypes() {
 *         return Set.of("erd:fusion_reactor", "erd:plasma_cutter");
 *     }
 *
 *     @Override
 *     public ValidationResult validate(StructureSnapshot snapshot, BlockPos attachmentPoint) {
 *         // Validate structure relative to attachment point
 *     }
 *
 *     @Override
 *     public PowerLaserComponent create(ComponentId id, Structure structure,
 *                                        BlockPos attachmentPoint, MachineInstance parent) {
 *         return new PowerLaserComponent(id, this, structure, attachmentPoint, parent);
 *     }
 * }
 * }</pre>
 *
 * <p>Thread-safety: Definitions should be immutable after creation.
 *
 * @param <T> the type of component this definition creates
 * @see MachineComponent
 */
public interface ComponentDefinition<T extends MachineComponent> {

    /**
     * @return the unique type identifier for this component type (e.g., "erd:power_laser")
     */
    String componentTypeId();

    /**
     * @return the human-readable display name
     */
    String displayName();

    /**
     * Returns the machine types this component can attach to.
     *
     * @return set of compatible machine type IDs
     */
    Set<String> compatibleMachineTypes();

    /**
     * Returns the block type key used to detect this component's anchor.
     *
     * @return the block type key (e.g., "minecraft:diamond_block")
     */
    String anchorBlockKey();

    /**
     * Returns the relative bounds for structure detection.
     * The bounds define the region to scan when validating the structure,
     * relative to the attachment point.
     *
     * @return the detection bounds
     */
    dev.kate.erd.core.machine.MachineDefinition.StructureBounds detectionBounds();

    /**
     * Validates that a structure snapshot matches this component definition.
     *
     * <p>Unlike machine validation, component validation is relative to an
     * attachment point which is the connection to the parent machine.
     *
     * @param snapshot the structure to validate
     * @param attachmentPoint the attachment point on the parent machine
     * @return validation result with success/failure and details
     */
    ValidationResult validate(StructureSnapshot snapshot, BlockPos attachmentPoint);

    /**
     * Creates a component instance from a validated structure.
     * Should only be called after validate() returns success.
     *
     * @param id the unique ID for the new instance
     * @param structure the validated structure
     * @param attachmentPoint the attachment point
     * @param parent the parent machine instance
     * @return the new component instance
     */
    T create(ComponentId id, Structure structure, BlockPos attachmentPoint, MachineInstance parent);

    /**
     * Checks if this component type is compatible with the given machine type.
     *
     * @param machineTypeId the machine type ID to check
     * @return true if this component can attach to that machine type
     */
    default boolean isCompatibleWith(String machineTypeId) {
        return compatibleMachineTypes().contains(machineTypeId);
    }
}

