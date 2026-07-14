package dev.kate.erd.core.engine;

import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.NetworkId;
import dev.kate.erd.core.model.PipeFamily;

import java.util.*;

/**
 * Immutable snapshot of network state for a single connection type.
 *
 * <p>Snapshots are used for async topology computations. They capture
 * the network state at a specific version, allowing computations to
 * proceed without holding locks on the live state.
 *
 * <p>Thread-safety: This class is immutable and therefore thread-safe.
 */
public final class LayerSnapshot {

    private final ConnectionType type;
    private final long version;
    private final Map<BlockPos, NetworkId> positionToNetwork;
    private final Map<NetworkId, Set<BlockPos>> networkToPositions;
    private final Map<NetworkId, PipeFamily> pipeFamilies; // Only used for PIPE type

    /**
     * Creates a new connection type snapshot.
     *
     * @param type the connection type
     * @param version the version number at snapshot time
     * @param positionToNetwork mapping of positions to networks
     * @param networkToPositions mapping of networks to their positions
     * @param pipeFamilies pipe family assignments (only for PIPE type)
     */
    public LayerSnapshot(
            ConnectionType type,
            long version,
            Map<BlockPos, NetworkId> positionToNetwork,
            Map<NetworkId, Set<BlockPos>> networkToPositions,
            Map<NetworkId, PipeFamily> pipeFamilies) {
        this.type = Objects.requireNonNull(type);
        this.version = version;
        this.positionToNetwork = Map.copyOf(positionToNetwork);

        // Deep copy networkToPositions
        Map<NetworkId, Set<BlockPos>> copy = new HashMap<>();
        for (var entry : networkToPositions.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        this.networkToPositions = Collections.unmodifiableMap(copy);

        this.pipeFamilies = pipeFamilies != null ? Map.copyOf(pipeFamilies) : Map.of();
    }

    /**
     * @return the connection type
     */
    public ConnectionType type() {
        return type;
    }

    /**
     * @return the version number at snapshot time
     */
    public long version() {
        return version;
    }

    /**
     * Gets the network containing a position.
     *
     * @param pos the position
     * @return the network ID, or empty if not part of any network
     */
    public Optional<NetworkId> getNetworkAt(BlockPos pos) {
        return Optional.ofNullable(positionToNetwork.get(pos));
    }

    /**
     * Gets all positions in a network.
     *
     * @param networkId the network ID
     * @return the positions, or empty set if network doesn't exist
     */
    public Set<BlockPos> getPositions(NetworkId networkId) {
        return networkToPositions.getOrDefault(networkId, Set.of());
    }

    /**
     * @return all network IDs in this type
     */
    public Set<NetworkId> getAllNetworkIds() {
        return networkToPositions.keySet();
    }

    /**
     * @return unmodifiable view of position to network mapping
     */
    public Map<BlockPos, NetworkId> positionToNetworkMap() {
        return positionToNetwork;
    }

    /**
     * @return unmodifiable view of network to positions mapping
     */
    public Map<NetworkId, Set<BlockPos>> networkToPositionsMap() {
        return networkToPositions;
    }

    /**
     * Gets the pipe family for a PIPE network.
     *
     * @param networkId the network ID
     * @return the pipe family, or UNASSIGNED if not set
     */
    public PipeFamily getPipeFamily(NetworkId networkId) {
        return pipeFamilies.getOrDefault(networkId, PipeFamily.UNASSIGNED);
    }

    /**
     * Checks if a position is part of any network.
     *
     * @param pos the position
     * @return true if the position is a segment in some network
     */
    public boolean containsPosition(BlockPos pos) {
        return positionToNetwork.containsKey(pos);
    }

    /**
     * @return total number of segments across all networks
     */
    public int totalSegmentCount() {
        return positionToNetwork.size();
    }

    /**
     * @return number of networks in this type
     */
    public int networkCount() {
        return networkToPositions.size();
    }
}
