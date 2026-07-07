package dev.kate.erd.core.machine.component;

import dev.kate.erd.core.machine.MachineInstance;
import dev.kate.erd.core.machine.RescanResult;
import dev.kate.erd.core.machine.Structure;
import dev.kate.erd.core.machine.StructureSnapshot;
import dev.kate.erd.core.machine.ValidationResult;
import dev.kate.erd.core.model.BlockPos;

import java.util.Objects;

/**
 * Base implementation of {@link MachineComponent} with common functionality.
 *
 * <p>Concrete component types should extend this class to add type-specific
 * behavior while inheriting standard lifecycle management.
 *
 * <p>Example:
 * <pre>{@code
 * public class PowerLaserComponent extends BaseMachineComponent {
 *     private int chargeLevel = 0;
 *
 *     public PowerLaserComponent(ComponentId id, ComponentDefinition<?> definition,
 *                                 Structure structure, BlockPos attachmentPoint,
 *                                 MachineInstance parent) {
 *         super(id, definition, structure, attachmentPoint, parent);
 *     }
 *
 *     @Override
 *     protected void doTick() {
 *         // Charge the laser each tick
 *         chargeLevel = Math.min(100, chargeLevel + 1);
 *     }
 *
 *     @Override
 *     public void onStructureChanged(Structure oldStructure, Structure newStructure) {
 *         // Larger structure = more power
 *         maxPower = BASE_POWER * newStructure.metrics().blockCount();
 *     }
 * }
 * }</pre>
 *
 * <p>Thread-safety: NOT thread-safe. Use on processing thread only.
 */
public abstract class BaseMachineComponent implements MachineComponent {

    private final ComponentId id;
    private final ComponentDefinition<?> definition;
    private final BlockPos attachmentPoint;

    private Structure structure;
    private MachineInstance parent;
    private boolean attached = false;

    /**
     * Creates a base component instance.
     *
     * @param id the unique component ID
     * @param definition the component definition
     * @param structure the initial structure
     * @param attachmentPoint the attachment point
     * @param parent the parent machine (may be null if not yet attached)
     */
    protected BaseMachineComponent(
            ComponentId id,
            ComponentDefinition<?> definition,
            Structure structure,
            BlockPos attachmentPoint,
            MachineInstance parent) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.definition = Objects.requireNonNull(definition, "definition must not be null");
        this.structure = Objects.requireNonNull(structure, "structure must not be null");
        this.attachmentPoint = Objects.requireNonNull(attachmentPoint, "attachmentPoint must not be null");
        this.parent = parent;
        this.attached = parent != null;
    }

    @Override
    public ComponentId id() {
        return id;
    }

    @Override
    public ComponentDefinition<?> definition() {
        return definition;
    }

    @Override
    public Structure structure() {
        return structure;
    }

    @Override
    public BlockPos attachmentPoint() {
        return attachmentPoint;
    }

    @Override
    public MachineInstance parent() {
        return parent;
    }

    /**
     * @return true if this component is currently attached to a parent
     */
    public boolean isAttached() {
        return attached;
    }

    @Override
    public void updateStructure(Structure newStructure) {
        Objects.requireNonNull(newStructure, "newStructure must not be null");

        Structure oldStructure = this.structure;
        this.structure = newStructure;

        onStructureChanged(oldStructure, newStructure);

        // Notify parent of structure change
        if (parent != null) {
            parent.onComponentStructureChanged(this, oldStructure, newStructure);
        }
    }

    @Override
    public RescanResult rescan(StructureSnapshot snapshot) {
        ValidationResult result = definition.validate(snapshot, attachmentPoint);

        if (result instanceof ValidationResult.Invalid) {
            return RescanResult.INVALID;
        }

        ValidationResult.Valid valid = (ValidationResult.Valid) result;
        Structure newStructure = valid.structure();

        if (newStructure.positions().equals(structure.positions())) {
            return RescanResult.UNCHANGED;
        }

        updateStructure(newStructure);
        return RescanResult.RESIZED;
    }

    @Override
    public void tick() {
        if (!attached) return;
        doTick();
    }

    /**
     * Subclass hook for tick processing.
     * Called when component is attached.
     */
    protected abstract void doTick();

    @Override
    public void onAttach(MachineInstance parent) {
        this.parent = Objects.requireNonNull(parent, "parent must not be null");
        this.attached = true;
    }

    @Override
    public void onDetach() {
        this.attached = false;
        // Don't null parent - may be needed for cleanup
    }

    @Override
    public void onStructureChanged(Structure oldStructure, Structure newStructure) {
        // Default: no action. Subclasses override to recalculate stats.
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseMachineComponent that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s[id=%s, type=%s, attached=%s, blocks=%d]",
                getClass().getSimpleName(), id, definition.componentTypeId(),
                attached, structure.size());
    }
}

