package dev.kate.erd.core.dataplane;

import dev.kate.erd.core.model.ControllerId;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.model.NetworkId;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a binding between a controller and a machine on a DATA network.
 *
 * <p>Bindings are stored against the DATA network, not the mainframe instance.
 * A binding becomes inactive if its participants land in different networks
 * after a split, or if there is no available mainframe leader.
 *
 * <p>Thread-safety: This class is immutable and therefore thread-safe.
 *
 * @param id unique identifier for this binding
 * @param networkId the DATA network this binding belongs to
 * @param controllerId the bound controller
 * @param machineId the bound machine
 * @param createdAt timestamp when binding was created
 */
public record Binding(
        BindingId id,
        NetworkId networkId,
        ControllerId controllerId,
        MachineId machineId,
        long createdAt
) {

    /**
     * Constructs a binding with validation.
     */
    public Binding {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(networkId, "networkId must not be null");
        Objects.requireNonNull(controllerId, "controllerId must not be null");
        Objects.requireNonNull(machineId, "machineId must not be null");
    }

    /**
     * Creates a new binding.
     *
     * @param networkId the DATA network
     * @param controllerId the controller
     * @param machineId the machine
     * @param createdAt creation timestamp
     * @return the new binding
     */
    public static Binding create(
            NetworkId networkId,
            ControllerId controllerId,
            MachineId machineId,
            long createdAt) {
        return new Binding(BindingId.create(), networkId, controllerId, machineId, createdAt);
    }

    /**
     * Creates a copy of this binding with a new network ID.
     * Used during network merge operations.
     *
     * @param newNetworkId the new network ID
     * @return a binding with the new network ID
     */
    public Binding withNetworkId(NetworkId newNetworkId) {
        return new Binding(id, newNetworkId, controllerId, machineId, createdAt);
    }
}
