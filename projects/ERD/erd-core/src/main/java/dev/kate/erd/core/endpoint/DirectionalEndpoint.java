package dev.kate.erd.core.endpoint;

import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.Direction;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.NetworkId;

import java.util.Objects;
import java.util.Optional;

/**
 * An endpoint that connects to networks via an adjacent segment position.
 *
 * <p>This is useful for machines where the port block itself is not a segment,
 * but connects to segments in adjacent positions.
 *
 * <p>Thread-safety: This class is NOT thread-safe.
 */
public class DirectionalEndpoint implements Endpoint {

    private final EndpointId id;
    private final BlockPos position;
    private final Direction connectionDirection;
    private final ConnectionType layer;
    private final EndpointRole role;

    private NetworkId attachedNetworkId;

    /**
     * Creates a directional endpoint.
     *
     * @param id the endpoint ID
     * @param position the endpoint's own position
     * @param connectionDirection the direction to look for segment connection
     * @param layer the network type
     * @param role the endpoint role
     */
    public DirectionalEndpoint(
            EndpointId id,
            BlockPos position,
            Direction connectionDirection,
            ConnectionType layer,
            EndpointRole role) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.position = Objects.requireNonNull(position, "position must not be null");
        this.connectionDirection = Objects.requireNonNull(connectionDirection,
            "connectionDirection must not be null");
        this.layer = Objects.requireNonNull(layer, "type must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
    }

    /**
     * Creates a directional endpoint with a generated ID.
     */
    public DirectionalEndpoint(
            BlockPos position,
            Direction connectionDirection,
            ConnectionType layer,
            EndpointRole role) {
        this(EndpointId.create(), position, connectionDirection, layer, role);
    }

    @Override
    public EndpointId id() {
        return id;
    }

    @Override
    public BlockPos position() {
        return position;
    }

    /**
     * @return the position where this endpoint looks for segment connections
     */
    public BlockPos connectionPosition() {
        return position.adjacent(connectionDirection);
    }

    /**
     * @return the direction this endpoint connects in
     */
    public Direction connectionDirection() {
        return connectionDirection;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DirectionalEndpoint that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
