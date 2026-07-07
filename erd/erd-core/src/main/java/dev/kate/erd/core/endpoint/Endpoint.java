package dev.kate.erd.core.endpoint;

import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.NetworkId;

import java.util.Optional;

/**
 * Represents a connection point where machines/controllers attach to networks.
 *
 * <p>Endpoints are the ports through which machines and controllers interact
 * with the network infrastructure. Each endpoint is associated with a specific
 * type (POWER, PIPE, DATA) and has a role that defines its behavior.
 *
 * <p>Thread-safety: Endpoint implementations should be designed for single-thread
 * mutation with multi-thread reads (after proper synchronization).
 */
public interface Endpoint {

    /**
     * @return the unique identifier for this endpoint
     */
    EndpointId id();

    /**
     * @return the world position of this endpoint
     */
    BlockPos position();

    /**
     * @return the network type this endpoint connects to
     */
    ConnectionType layer();

    /**
     * @return the role/behavior of this endpoint
     */
    EndpointRole role();

    /**
     * @return the network this endpoint is currently attached to, or empty
     */
    Optional<NetworkId> attachedNetwork();

    /**
     * @return true if this endpoint is currently attached to a network
     */
    default boolean isAttached() {
        return attachedNetwork().isPresent();
    }

    /**
     * Called when this endpoint is attached to a network.
     *
     * @param networkId the network being attached to
     */
    void onAttach(NetworkId networkId);

    /**
     * Called when this endpoint is detached from its network.
     */
    void onDetach();
}
