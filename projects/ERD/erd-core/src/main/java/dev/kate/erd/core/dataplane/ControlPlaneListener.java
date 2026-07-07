package dev.kate.erd.core.dataplane;

import dev.kate.erd.core.controller.ControllerInstance;
import dev.kate.erd.core.machine.MachineInstance;
import dev.kate.erd.core.model.NetworkId;

/**
 * Listener for DATA control plane events.
 *
 * <p>Implementations can react to controller/machine registration,
 * binding changes, and leader status updates.
 *
 * <p>Thread-safety: Listeners are called on the processing thread.
 */
public interface ControlPlaneListener {

    /**
     * Called when a controller is registered on a DATA network.
     *
     * @param controller the controller
     * @param networkId the DATA network
     */
    default void onControllerRegistered(ControllerInstance controller, NetworkId networkId) {}

    /**
     * Called when a machine is registered on a DATA network.
     *
     * @param machine the machine
     * @param networkId the DATA network
     */
    default void onMachineRegistered(MachineInstance machine, NetworkId networkId) {}

    /**
     * Called when a binding is created.
     *
     * @param binding the new binding
     */
    default void onBindingCreated(Binding binding) {}

    /**
     * Called when a binding is removed.
     *
     * @param bindingId the removed binding ID
     */
    default void onBindingRemoved(BindingId bindingId) {}

    /**
     * Called when a DATA network's leader status changes.
     *
     * @param networkId the network
     * @param hasLeader whether a leader is now available
     */
    default void onLeaderStatusChanged(NetworkId networkId, boolean hasLeader) {}
}
