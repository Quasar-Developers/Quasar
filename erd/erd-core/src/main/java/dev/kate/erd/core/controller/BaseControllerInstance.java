package dev.kate.erd.core.controller;

import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ControllerId;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.machine.StructureSnapshot;

import java.util.*;

/**
 * Base implementation of ControllerInstance with common functionality.
 *
 * <p>Concrete controller types can extend this class to add type-specific
 * behavior while inheriting standard lifecycle management.
 *
 * <p>Thread-safety: NOT thread-safe. Use on processing thread only.
 */
public abstract class BaseControllerInstance implements ControllerInstance {

    private final ControllerId id;
    private final ControllerDefinition<?> definition;
    private final BlockPos anchorPosition;
    private final Set<BlockPos> occupiedPositions;
    private final List<Endpoint> endpoints;
    private final long createdAt;

    private ControllerStatus status = ControllerStatus.NO_SIGNAL;
    private final Set<MachineId> boundMachines = new HashSet<>();

    /**
     * Creates a base controller instance.
     *
     * @param id the unique instance ID
     * @param definition the controller definition
     * @param anchorPosition the anchor block position
     * @param occupiedPositions all occupied positions
     * @param endpoints the controller's endpoints
     * @param createdAt creation timestamp for leader election
     */
    protected BaseControllerInstance(
            ControllerId id,
            ControllerDefinition<?> definition,
            BlockPos anchorPosition,
            Set<BlockPos> occupiedPositions,
            List<Endpoint> endpoints,
            long createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.definition = Objects.requireNonNull(definition, "definition must not be null");
        this.anchorPosition = Objects.requireNonNull(anchorPosition, "anchorPosition must not be null");
        this.occupiedPositions = Set.copyOf(Objects.requireNonNull(occupiedPositions));
        this.endpoints = List.copyOf(Objects.requireNonNull(endpoints));
        this.createdAt = createdAt;
    }

    @Override
    public ControllerId id() {
        return id;
    }

    @Override
    public ControllerDefinition<?> definition() {
        return definition;
    }

    @Override
    public BlockPos anchorPosition() {
        return anchorPosition;
    }

    @Override
    public Set<BlockPos> occupiedPositions() {
        return occupiedPositions;
    }

    @Override
    public List<Endpoint> endpoints() {
        return endpoints;
    }

    @Override
    public long createdAt() {
        return createdAt;
    }

    @Override
    public ControllerStatus status() {
        return status;
    }

    /**
     * Sets the controller status.
     *
     * @param status the new status
     */
    protected void setStatus(ControllerStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    @Override
    public boolean isAvailable() {
        return status == ControllerStatus.CONNECTED;
    }

    /**
     * @return the set of currently bound machine IDs
     */
    protected Set<MachineId> boundMachines() {
        return Collections.unmodifiableSet(boundMachines);
    }

    /**
     * @return the number of currently bound machines
     */
    protected int boundMachineCount() {
        return boundMachines.size();
    }

    @Override
    public void onDataConnectionEstablished() {
        if (status != ControllerStatus.INVALID) {
            setStatus(ControllerStatus.CONNECTED);
        }
    }

    @Override
    public void onDataConnectionLost() {
        if (status != ControllerStatus.INVALID) {
            setStatus(ControllerStatus.NO_SIGNAL);
        }
    }

    @Override
    public void onMachineBound(MachineId machineId) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        boundMachines.add(machineId);
    }

    @Override
    public void onMachineUnbound(MachineId machineId) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        boundMachines.remove(machineId);
    }

    @Override
    public void tick() {
        if (status == ControllerStatus.INVALID) {
            return;
        }
        doTick();
    }

    /**
     * Subclass hook for tick processing.
     */
    protected abstract void doTick();

    @Override
    public boolean revalidate(StructureSnapshot snapshot) {
        var result = definition.validate(snapshot);
        if (result instanceof ControllerDefinition.ValidationResult.Failure) {
            setStatus(ControllerStatus.INVALID);
            return false;
        }
        return true;
    }

    @Override
    public void onRemove() {
        setStatus(ControllerStatus.INVALID);
        boundMachines.clear();

        for (Endpoint endpoint : endpoints) {
            endpoint.onDetach();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseControllerInstance that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s[id=%s, type=%s, pos=%s, status=%s, createdAt=%d]",
            getClass().getSimpleName(), id, definition.typeId(), anchorPosition, status, createdAt);
    }
}
