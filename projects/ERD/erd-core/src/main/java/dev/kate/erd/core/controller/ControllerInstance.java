package dev.kate.erd.core.controller;

import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ControllerId;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.machine.StructureSnapshot;

import java.util.List;
import java.util.Set;

/**
 * Represents an active controller instance in the world.
 *
 * <p>Controller instances monitor and control machines. They track
 * runtime state like DATA connection status and bound machines.
 *
 * <p>Thread-safety: Instances are NOT thread-safe. Mutations should occur
 * on the main/processing thread.
 */
public interface ControllerInstance {

    /**
     * @return the unique identifier for this instance
     */
    ControllerId id();

    /**
     * @return the definition/type of this controller
     */
    ControllerDefinition<?> definition();

    /**
     * @return the anchor/controller block position
     */
    BlockPos anchorPosition();

    /**
     * @return all positions occupied by this controller (immutable)
     */
    Set<BlockPos> occupiedPositions();

    /**
     * @return all endpoints/ports on this controller (immutable)
     */
    List<Endpoint> endpoints();

    /**
     * @return the timestamp when this controller was created (for leader election)
     */
    long createdAt();

    /**
     * @return the current connection status
     */
    ControllerStatus status();

    /**
     * @return true if this controller is currently available for operation
     */
    boolean isAvailable();

    /**
     * Called each server tick to update controller state.
     */
    void tick();

    /**
     * Called when DATA network connection is established.
     */
    void onDataConnectionEstablished();

    /**
     * Called when DATA network connection is lost.
     * Controller should display "NO SIGNAL".
     */
    void onDataConnectionLost();

    /**
     * Called when this controller is bound to a machine.
     *
     * @param machineId the machine being bound to
     */
    void onMachineBound(MachineId machineId);

    /**
     * Called when a machine binding is removed.
     *
     * @param machineId the machine being unbound
     */
    void onMachineUnbound(MachineId machineId);

    /**
     * Revalidates the controller structure.
     *
     * @param snapshot current structure snapshot
     * @return true if still valid
     */
    boolean revalidate(StructureSnapshot snapshot);

    /**
     * Called when the controller is being removed/destroyed.
     */
    void onRemove();
}

