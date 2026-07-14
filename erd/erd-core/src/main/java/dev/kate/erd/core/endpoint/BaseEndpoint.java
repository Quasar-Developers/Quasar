package dev.kate.erd.core.endpoint;

import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.NetworkId;

import java.util.Objects;
import java.util.Optional;

/**
 * Base implementation of an endpoint with common functionality.
 *
 * <p>This class provides the standard endpoint behavior for attachment/detachment
 * and can be extended for type-specific or role-specific endpoints.
 *
 * <p>Thread-safety: This class is NOT thread-safe. Mutations should occur
 * on a single thread.
 */
public class BaseEndpoint implements Endpoint {

    private final EndpointId id;
    private final BlockPos position;
    private final ConnectionType layer;
    private final EndpointRole role;

    private NetworkId attachedNetworkId;

    /**
     * Creates a new base endpoint.
     *
     * @param id the endpoint ID
     * @param position the position
     * @param layer the network type
     * @param role the endpoint role
     */
    public BaseEndpoint(EndpointId id, BlockPos position, ConnectionType layer, EndpointRole role) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.position = Objects.requireNonNull(position, "position must not be null");
        this.layer = Objects.requireNonNull(layer, "type must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
    }

    /**
     * Creates a new base endpoint with a generated ID.
     *
     * @param position the position
     * @param layer the network type
     * @param role the endpoint role
     */
    public BaseEndpoint(BlockPos position, ConnectionType layer, EndpointRole role) {
        this(EndpointId.create(), position, layer, role);
    }

    @Override
    public EndpointId id() {
        return id;
    }

    @Override
    public BlockPos position() {
        return position;
    }

    @Override
    public ConnectionType layer() {
        return layer;
    }

    @Override
    public EndpointRole role() {
        return role;
    }

    @Override
    public Optional<NetworkId> attachedNetwork() {
        return Optional.ofNullable(attachedNetworkId);
    }

    @Override
    public void onAttach(NetworkId networkId) {
        Objects.requireNonNull(networkId, "networkId must not be null");
        this.attachedNetworkId = networkId;
    }

    @Override
    public void onDetach() {
        this.attachedNetworkId = null;
    }

    @Override
    public String toString() {
        return String.format("Endpoint[id=%s, pos=%s, type=%s, role=%s, attached=%s]",
            id, position, layer, role, attachedNetworkId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEndpoint that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
