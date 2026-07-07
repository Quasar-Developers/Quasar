package dev.kate.erd.core.machine;

import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.machine.component.ComponentId;
import dev.kate.erd.core.machine.component.MachineComponent;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ControllerId;
import dev.kate.erd.core.model.MachineId;

import java.util.*;

/**
 * Base implementation of MachineInstance with common functionality.
 *
 * <p>Concrete machine types can extend this class to add type-specific
 * behavior while inheriting standard lifecycle management.
 *
 * <p>This implementation supports:
 * <ul>
 *   <li>Mutable structure — machines can grow/shrink via {@link #updateStructure}</li>
 *   <li>Components — sub-machines can be attached/detached</li>
 *   <li>Control links — tracks bound controllers</li>
 * </ul>
 *
 * <p>Thread-safety: NOT thread-safe. Use on processing thread only.
 */
public abstract class BaseMachineInstance implements MachineInstance {

    private final MachineId id;
    private final MachineDefinition<?> definition;
    private final BlockPos anchorPosition;

    // Mutable structure - can change when machine is upgraded
    private Structure structure;

    // Attached components
    private final List<MachineComponent> components = new ArrayList<>();
    private final Map<ComponentId, MachineComponent> componentMap = new HashMap<>();

    private MachineStatus status = MachineStatus.IDLE;
    private final Set<ControllerId> linkedControllers = new HashSet<>();

    /**
     * Creates a base machine instance.
     *
     * @param id the unique instance ID
     * @param definition the machine definition
     * @param anchorPosition the anchor block position
     * @param structure the initial structure
     */
    protected BaseMachineInstance(
            MachineId id,
            MachineDefinition<?> definition,
            BlockPos anchorPosition,
            Structure structure) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.definition = Objects.requireNonNull(definition, "definition must not be null");
        this.anchorPosition = Objects.requireNonNull(anchorPosition, "anchorPosition must not be null");
        this.structure = Objects.requireNonNull(structure, "structure must not be null");
    }

    /**
     * Creates a base machine instance from positions and endpoints (legacy compatibility).
     *
     * @param id the unique instance ID
     * @param definition the machine definition
     * @param anchorPosition the anchor block position
     * @param occupiedPositions all occupied positions
     * @param endpoints the machine's endpoints
     */
    protected BaseMachineInstance(
            MachineId id,
            MachineDefinition<?> definition,
            BlockPos anchorPosition,
            Set<BlockPos> occupiedPositions,
            List<Endpoint> endpoints) {
        this(id, definition, anchorPosition, Structure.of(occupiedPositions, endpoints));
    }

    @Override
    public MachineId id() {
        return id;
    }

    @Override
    public MachineDefinition<?> definition() {
        return definition;
    }

    @Override
    public BlockPos anchorPosition() {
        return anchorPosition;
    }

    // ========== Structure ==========

    @Override
    public Structure structure() {
        return structure;
    }

    @Override
    public void updateStructure(Structure newStructure) {
        Objects.requireNonNull(newStructure, "newStructure must not be null");

        Structure oldStructure = this.structure;
        this.structure = newStructure;

        // Detach old endpoints
        for (Endpoint endpoint : oldStructure.endpoints()) {
            endpoint.onDetach();
        }

        onStructureChanged(oldStructure, newStructure);
    }

    @Override
    public RescanResult rescan(StructureSnapshot snapshot) {
        ValidationResult result = definition.validate(snapshot);

        if (result instanceof ValidationResult.Invalid) {
            setStatus(MachineStatus.INVALID);
            return RescanResult.INVALID;
        }

        if (result instanceof ValidationResult.Valid(Structure newStructure)) {
            if (newStructure.positions().equals(structure.positions())) {
                return RescanResult.UNCHANGED;
            }

            updateStructure(newStructure);
            return RescanResult.RESIZED;
        }

        throw new IllegalStateException("Unknown validation result type: " + result);
    }

    @Override
    public void onStructureChanged(Structure oldStructure, Structure newStructure) {
        // Default: no action. Subclasses override to recalculate stats.
    }

    // ========== Components ==========

    @Override
    public List<MachineComponent> components() {
        return Collections.unmodifiableList(components);
    }

    @Override
    public Optional<MachineComponent> getComponent(ComponentId id) {
        return Optional.ofNullable(componentMap.get(id));
    }

    @Override
    public void attachComponent(MachineComponent component) {
        Objects.requireNonNull(component, "component must not be null");

        if (componentMap.containsKey(component.id())) {
            throw new IllegalArgumentException("Component already attached: " + component.id());
        }

        if (!component.definition().isCompatibleWith(definition.typeId())) {
            throw new IllegalArgumentException("Component " + component.definition().componentTypeId() +
                    " is not compatible with machine " + definition.typeId());
        }

        components.add(component);
        componentMap.put(component.id(), component);
        component.onAttach(this);
    }

    @Override
    public Optional<MachineComponent> detachComponent(ComponentId id) {
        MachineComponent component = componentMap.remove(id);
        if (component != null) {
            components.remove(component);
            component.onDetach();
        }
        return Optional.ofNullable(component);
    }

    @Override
    public void onComponentStructureChanged(MachineComponent component, Structure oldStructure, Structure newStructure) {
        // Default: no action. Subclasses can override to recalculate aggregate stats.
    }

    // ========== Status ==========

    @Override
    public MachineStatus status() {
        return status;
    }

    /**
     * Sets the machine status.
     *
     * @param status the new status
     */
    protected void setStatus(MachineStatus status) {
        this.status = Objects.requireNonNull(status);
    }


    @Override
    public void onControlLinkEstablished(ControllerId controllerId) {
        Objects.requireNonNull(controllerId, "controllerId must not be null");
        linkedControllers.add(controllerId);

        if (status == MachineStatus.BLIND) {
            setStatus(MachineStatus.IDLE);
        }
    }

    @Override
    public void onControlLinkLost(ControllerId controllerId) {
        Objects.requireNonNull(controllerId, "controllerId must not be null");
        linkedControllers.remove(controllerId);

        if (linkedControllers.isEmpty()) {
            onAllControlLinksLost();
        }
    }

    /**
     * Called when all control links are lost.
     * Subclasses can override to define type-specific behavior.
     * Default behavior sets status to BLIND.
     */
    protected void onAllControlLinksLost() {
        setStatus(MachineStatus.BLIND);
    }

    // ========== Lifecycle ==========

    @Override
    public void tick() {
        if (status == MachineStatus.INVALID) {
            return;
        }

        // Tick self
        doTick();

        // Tick all components
        for (MachineComponent component : components) {
            component.tick();
        }
    }

    /**
     * Subclass hook for tick processing.
     * Called when status is not INVALID.
     */
    protected abstract void doTick();

    @Override
    public void onRemove() {
        setStatus(MachineStatus.INVALID);
        linkedControllers.clear();

        // Detach all components
        for (MachineComponent component : new ArrayList<>(components)) {
            component.onDetach();
        }
        components.clear();
        componentMap.clear();

        // Detach all endpoints
        for (Endpoint endpoint : structure.endpoints()) {
            endpoint.onDetach();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseMachineInstance that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s[id=%s, type=%s, pos=%s, status=%s, blocks=%d, components=%d]",
            getClass().getSimpleName(), id, definition.typeId(), anchorPosition, status,
            structure.size(), components.size());
    }
}
