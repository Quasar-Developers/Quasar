package dev.kate.erd.core.endpoint;

import dev.kate.erd.core.machine.resource.ResourceType;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.NetworkId;

import java.util.Objects;
import java.util.Optional;

/**
 * An endpoint that is bound to a specific resource type.
 *
 * <p>This endpoint only allows transfer of its designated resource type,
 * enabling resource-specific routing through different ports on a machine.
 *
 * <p>For example, a reactor's "water input" endpoint would only accept water,
 * while its "hydrogen input" endpoint would only accept hydrogen.
 *
 * <p>Thread-safety: This class is NOT thread-safe. Mutations should occur
 * on a single thread.
 */
public class ResourceEndpoint implements Endpoint {

    private final EndpointId id;
    private final BlockPos position;
    private final ConnectionType layer;
    private final EndpointRole role;
    private final ResourceType resourceType;

    private NetworkId attachedNetworkId;

    /**
     * Creates a new resource endpoint.
     *
     * @param id the endpoint ID
     * @param position the position
     * @param layer the network type (should be PIPE)
     * @param role the endpoint role (CONSUMER or PROVIDER)
     * @param resourceType the specific resource type this endpoint handles
     */
    public ResourceEndpoint(EndpointId id, BlockPos position, ConnectionType layer,
                           EndpointRole role, ResourceType resourceType) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.position = Objects.requireNonNull(position, "position must not be null");
        this.layer = Objects.requireNonNull(layer, "type must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType must not be null");
    }

    /**
     * Creates a new resource endpoint with a generated ID.
     *
     * @param position the position
     * @param layer the network type
     * @param role the endpoint role
     * @param resourceType the specific resource type
     */
    public ResourceEndpoint(BlockPos position, ConnectionType layer, EndpointRole role,
                           ResourceType resourceType) {
        this(EndpointId.create(), position, layer, role, resourceType);
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

    /**
     * @return the specific resource type this endpoint handles
     */
    public ResourceType resourceType() {
        return resourceType;
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
        return String.format("ResourceEndpoint[id=%s, pos=%s, type=%s, role=%s, resource=%s, attached=%s]",
            id, position, layer, role, resourceType, attachedNetworkId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceEndpoint that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

