package dev.kate.erd.core.engine;

import dev.kate.erd.core.model.*;
import dev.kate.erd.core.persistence.NetworkStateStore;
import dev.kate.erd.core.topology.TopologyBfs;
import dev.kate.erd.core.topology.TopologyResult;
import dev.kate.erd.core.util.ErdLogger;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages the topology state for a single network type.
 *
 * <p>Each type (POWER, PIPE, DATA) has its own ConnectionState instance that
 * tracks segment positions, network membership, and type-specific properties.
 *
 * <p>Thread-safety: This class is NOT thread-safe. All mutations must be
 * performed through the EngineOperationQueue on a single thread.
 */
public final class ConnectionState {

    private final ConnectionType layer;
    private final ErdLogger logger;
    private final AtomicLong version = new AtomicLong(0);

    // Core mappings
    private final Map<BlockPos, NetworkId> positionToNetwork = new HashMap<>();
    private final Map<NetworkId, Set<BlockPos>> networkToPositions = new HashMap<>();

    // PIPE-specific: network family assignments
    private final Map<NetworkId, PipeFamily> pipeFamilies = new HashMap<>();

    // Chunk tracking for load/unload handling
    private final Map<ChunkKey, Set<BlockPos>> chunkToPositions = new HashMap<>();
    private final Set<ChunkKey> activeChunks = new HashSet<>();

    // Network metadata (creation time for merge decisions)
    private final Map<NetworkId, Long> networkCreationTime = new HashMap<>();

    /**
     * Creates a new type state.
     *
     * @param layer the type type
     * @param logger the logger to use
     */
    public ConnectionState(ConnectionType layer, ErdLogger logger) {
        this.layer = Objects.requireNonNull(layer, "type must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

    /**
     * @return the type type
     */
    public ConnectionType getLayer() {
        return layer;
    }

    /**
     * @return the current version number
     */
    public long getVersion() {
        return version.get();
    }

    /**
     * Creates an immutable snapshot of the current state.
     *
     * @return a snapshot for async computation
     */
    public LayerSnapshot createSnapshot() {
        return new LayerSnapshot(
            layer,
            version.get(),
            positionToNetwork,
            networkToPositions,
            layer == ConnectionType.PIPE ? pipeFamilies : null
        );
    }

    /**
     * Adds a segment at the specified position.
     *
     * @param pos the position to add
     * @param creationTime timestamp for network creation ordering
     * @return the result of the topology operation
     */
    public TopologyResult addSegment(BlockPos pos, long creationTime) {
        Objects.requireNonNull(pos, "pos must not be null");

        if (positionToNetwork.containsKey(pos)) {
            logger.debug("Segment already exists at %s", pos);
            return new TopologyResult.NoChange(version.get());
        }

        long currentVersion = version.get();
        Set<NetworkId> adjacent = TopologyBfs.findAdjacentNetworks(pos, positionToNetwork);

        TopologyResult result;

        if (adjacent.isEmpty()) {
            // Create new isolated network
            NetworkId newId = NetworkId.create();
            result = new TopologyResult.NetworkCreated(currentVersion, newId, pos);
            applyNetworkCreated((TopologyResult.NetworkCreated) result, creationTime);

        } else if (adjacent.size() == 1) {
            // Join existing network
            NetworkId networkId = adjacent.iterator().next();
            result = new TopologyResult.SegmentAdded(currentVersion, networkId, pos);
            applySegmentAdded((TopologyResult.SegmentAdded) result);

        } else {
            // Merge multiple networks
            NetworkId primary = selectPrimaryNetwork(adjacent);
            Set<NetworkId> toMerge = new HashSet<>(adjacent);
            toMerge.remove(primary);
            result = new TopologyResult.NetworksMerged(currentVersion, primary, toMerge, pos);
            applyNetworksMerged((TopologyResult.NetworksMerged) result);
        }

        // Update chunk tracking
        ChunkKey chunk = pos.toChunkKey();
        chunkToPositions.computeIfAbsent(chunk, k -> new HashSet<>()).add(pos);

        version.incrementAndGet();
        return result;
    }

    /**
     * Removes a segment at the specified position.
     *
     * @param pos the position to remove
     * @return the result of the topology operation
     */
    public TopologyResult removeSegment(BlockPos pos) {
        Objects.requireNonNull(pos, "pos must not be null");

        NetworkId networkId = positionToNetwork.get(pos);
        if (networkId == null) {
            logger.debug("No segment at %s to remove", pos);
            return new TopologyResult.NoChange(version.get());
        }

        long currentVersion = version.get();
        Set<BlockPos> networkCables = networkToPositions.get(networkId);

        TopologyResult result;

        if (networkCables.size() == 1) {
            // Last segment - dissolve network
            result = new TopologyResult.NetworkDissolved(currentVersion, networkId, pos);
            applyNetworkDissolved((TopologyResult.NetworkDissolved) result);

        } else {
            // Check for split
            List<Set<BlockPos>> components = TopologyBfs.detectSplitOnRemoval(pos, networkCables);

            if (components.size() == 1) {
                // No split - just remove
                result = new TopologyResult.SegmentRemoved(currentVersion, networkId, pos);
                applySegmentRemoved((TopologyResult.SegmentRemoved) result);
            } else {
                // Split into multiple networks
                List<TopologyResult.SplitComponent> splitComponents =
                    createSplitComponents(networkId, components);
                result = new TopologyResult.NetworkSplit(
                    currentVersion, networkId, pos, splitComponents);
                applyNetworkSplit((TopologyResult.NetworkSplit) result);
            }
        }

        // Update chunk tracking
        ChunkKey chunk = pos.toChunkKey();
        Set<BlockPos> chunkPositions = chunkToPositions.get(chunk);
        if (chunkPositions != null) {
            chunkPositions.remove(pos);
            if (chunkPositions.isEmpty()) {
                chunkToPositions.remove(chunk);
            }
        }

        version.incrementAndGet();
        return result;
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
     * @return unmodifiable view of positions, or empty if network doesn't exist
     */
    public Set<BlockPos> getPositions(NetworkId networkId) {
        Set<BlockPos> positions = networkToPositions.get(networkId);
        return positions != null ? Collections.unmodifiableSet(positions) : Set.of();
    }

    /**
     * @return all network IDs in this type
     */
    public Set<NetworkId> getAllNetworkIds() {
        return Collections.unmodifiableSet(networkToPositions.keySet());
    }

    /**
     * Sets the pipe family for a PIPE network.
     * Only valid for PIPE type.
     *
     * @param networkId the network ID
     * @param family the family to set
     * @throws IllegalStateException if not a PIPE type
     * @throws IllegalArgumentException if network doesn't exist or family already set
     */
    public void setPipeFamily(NetworkId networkId, PipeFamily family) {
        if (layer != ConnectionType.PIPE) {
            throw new IllegalStateException("Pipe family only valid for PIPE type");
        }
        Objects.requireNonNull(family, "family must not be null");
        if (family == PipeFamily.UNASSIGNED) {
            throw new IllegalArgumentException("Cannot explicitly set UNASSIGNED family");
        }
        if (!networkToPositions.containsKey(networkId)) {
            throw new IllegalArgumentException("Network does not exist: " + networkId);
        }

        PipeFamily existing = pipeFamilies.get(networkId);
        if (existing != null && existing != PipeFamily.UNASSIGNED) {
            throw new IllegalArgumentException(
                "Network already has family " + existing + ", cannot change to " + family);
        }

        pipeFamilies.put(networkId, family);
        version.incrementAndGet();
    }

    /**
     * Gets the pipe family for a network.
     *
     * @param networkId the network ID
     * @return the family, or UNASSIGNED
     */
    public PipeFamily getPipeFamily(NetworkId networkId) {
        return pipeFamilies.getOrDefault(networkId, PipeFamily.UNASSIGNED);
    }

    /**
     * Marks a chunk as active (loaded).
     *
     * @param chunk the chunk to activate
     */
    public void activateChunk(ChunkKey chunk) {
        activeChunks.add(chunk);
    }

    /**
     * Marks a chunk as dormant (unloaded).
     *
     * @param chunk the chunk to deactivate
     */
    public void deactivateChunk(ChunkKey chunk) {
        activeChunks.remove(chunk);
    }

    /**
     * Checks if a chunk is currently active.
     *
     * @param chunk the chunk to check
     * @return true if active
     */
    public boolean isChunkActive(ChunkKey chunk) {
        return activeChunks.contains(chunk);
    }

    /**
     * Gets all segment positions in a chunk.
     *
     * @param chunk the chunk
     * @return unmodifiable view of positions in the chunk
     */
    public Set<BlockPos> getPositionsInChunk(ChunkKey chunk) {
        Set<BlockPos> positions = chunkToPositions.get(chunk);
        return positions != null ? Collections.unmodifiableSet(positions) : Set.of();
    }

    /**
     * @return total number of segments in this type
     */
    public int getTotalSegmentCount() {
        return positionToNetwork.size();
    }

    /**
     * @return number of networks in this type
     */
    public int getNetworkCount() {
        return networkToPositions.size();
    }

    // ========== Internal apply methods ==========

    private void applyNetworkCreated(TopologyResult.NetworkCreated result, long creationTime) {
        NetworkId id = result.newNetworkId();
        BlockPos pos = result.position();

        positionToNetwork.put(pos, id);
        Set<BlockPos> positions = new HashSet<>();
        positions.add(pos);
        networkToPositions.put(id, positions);
        networkCreationTime.put(id, creationTime);

        if (layer == ConnectionType.PIPE) {
            pipeFamilies.put(id, PipeFamily.UNASSIGNED);
        }

        logger.debug("Created network %s at %s", id, pos);
    }

    private void applySegmentAdded(TopologyResult.SegmentAdded result) {
        NetworkId id = result.networkId();
        BlockPos pos = result.position();

        positionToNetwork.put(pos, id);
        networkToPositions.get(id).add(pos);

        logger.debug("Added segment at %s to network %s", pos, id);
    }

    private void applyNetworksMerged(TopologyResult.NetworksMerged result) {
        NetworkId primary = result.primaryNetworkId();
        BlockPos bridgePos = result.bridgePosition();

        // Add bridge segment to primary
        positionToNetwork.put(bridgePos, primary);
        networkToPositions.get(primary).add(bridgePos);

        // Merge all other networks into primary
        for (NetworkId mergedId : result.mergedNetworkIds()) {
            Set<BlockPos> mergedPositions = networkToPositions.remove(mergedId);
            if (mergedPositions != null) {
                for (BlockPos pos : mergedPositions) {
                    positionToNetwork.put(pos, primary);
                }
                networkToPositions.get(primary).addAll(mergedPositions);
            }
            networkCreationTime.remove(mergedId);

            // For PIPE: merged network inherits family if primary is unassigned
            if (layer == ConnectionType.PIPE) {
                PipeFamily mergedFamily = pipeFamilies.remove(mergedId);
                PipeFamily primaryFamily = pipeFamilies.get(primary);
                if (primaryFamily == PipeFamily.UNASSIGNED && mergedFamily != null
                        && mergedFamily != PipeFamily.UNASSIGNED) {
                    pipeFamilies.put(primary, mergedFamily);
                }
            }
        }

        logger.debug("Merged networks %s into %s via bridge at %s",
            result.mergedNetworkIds(), primary, bridgePos);
    }

    private void applySegmentRemoved(TopologyResult.SegmentRemoved result) {
        BlockPos pos = result.removedPosition();
        NetworkId id = result.networkId();

        positionToNetwork.remove(pos);
        networkToPositions.get(id).remove(pos);

        logger.debug("Removed segment at %s from network %s", pos, id);
    }

    private void applyNetworkDissolved(TopologyResult.NetworkDissolved result) {
        BlockPos pos = result.removedPosition();
        NetworkId id = result.networkId();

        positionToNetwork.remove(pos);
        networkToPositions.remove(id);
        networkCreationTime.remove(id);

        if (layer == ConnectionType.PIPE) {
            pipeFamilies.remove(id);
        }

        logger.debug("Dissolved network %s", id);
    }

    private void applyNetworkSplit(TopologyResult.NetworkSplit result) {
        NetworkId originalId = result.originalNetworkId();

        // Remove the segment that caused the split
        positionToNetwork.remove(result.removedPosition());

        // Clear original network
        networkToPositions.remove(originalId);
        Long originalCreation = networkCreationTime.remove(originalId);
        PipeFamily originalFamily = layer == ConnectionType.PIPE ? pipeFamilies.remove(originalId) : null;

        // Create new network states for each component
        for (TopologyResult.SplitComponent component : result.resultingComponents()) {
            NetworkId compId = component.networkId();
            Set<BlockPos> compPositions = new HashSet<>(component.positions());

            networkToPositions.put(compId, compPositions);
            for (BlockPos pos : compPositions) {
                positionToNetwork.put(pos, compId);
            }

            if (component.retainsOriginalId()) {
                networkCreationTime.put(compId, originalCreation);
            } else {
                networkCreationTime.put(compId, System.currentTimeMillis());
            }

            if (layer == ConnectionType.PIPE && originalFamily != null) {
                pipeFamilies.put(compId, originalFamily);
            }
        }

        logger.debug("Split network %s into %d components",
            originalId, result.resultingComponents().size());
    }

    private NetworkId selectPrimaryNetwork(Set<NetworkId> networks) {
        // Select network with earliest creation time (most "senior")
        // This ensures deterministic merge behavior
        return networks.stream()
            .min(Comparator.comparingLong(id ->
                networkCreationTime.getOrDefault(id, Long.MAX_VALUE)))
            .orElseThrow();
    }

    private List<TopologyResult.SplitComponent> createSplitComponents(
            NetworkId originalId, List<Set<BlockPos>> components) {

        List<TopologyResult.SplitComponent> result = new ArrayList<>();
        boolean originalIdAssigned = false;

        // Largest component keeps original ID for stability
        int maxSize = components.stream().mapToInt(Set::size).max().orElse(0);

        for (Set<BlockPos> compPositions : components) {
            NetworkId compId;
            boolean retainsOriginal;

            if (!originalIdAssigned && compPositions.size() == maxSize) {
                compId = originalId;
                retainsOriginal = true;
                originalIdAssigned = true;
            } else {
                compId = NetworkId.create();
                retainsOriginal = false;
            }

            result.add(new TopologyResult.SplitComponent(compId, compPositions, retainsOriginal));
        }

        return result;
    }

    /**
     * Exports current state for persistence/inspection.
     */
    public NetworkStateStore.ConnectionStateData exportState() {
        List<NetworkStateStore.NetworkData> networks = new ArrayList<>();
        for (var entry : networkToPositions.entrySet()) {
            NetworkId netId = entry.getKey();
            Set<BlockPos> positions = entry.getValue();
            long createdAt = networkCreationTime.getOrDefault(netId, 0L);
            PipeFamily family = pipeFamilies.getOrDefault(netId, PipeFamily.UNASSIGNED);
            networks.add(new NetworkStateStore.NetworkData(netId, positions, createdAt, family));
        }
        return new NetworkStateStore.ConnectionStateData(layer, networks, version.get());
    }

    /**
     * Imports a complete network from persisted state, preserving the original network ID.
     * This is used during world load/restore operations.
     *
     * @param networkData the network data to import
     */
    public void importNetwork(NetworkStateStore.NetworkData networkData) {
        NetworkId networkId = networkData.networkId();
        Set<BlockPos> positions = networkData.segmentPositions();
        long createdAt = networkData.createdAt();
        PipeFamily pipeFamily = networkData.pipeFamily();

        // Add all positions to this network
        for (BlockPos pos : positions) {
            positionToNetwork.put(pos, networkId);

            // Track chunk membership
            ChunkKey chunk = pos.toChunkKey();
            chunkToPositions.computeIfAbsent(chunk, k -> new HashSet<>()).add(pos);
        }

        // Set up network -> positions mapping
        networkToPositions.put(networkId, new HashSet<>(positions));
        networkCreationTime.put(networkId, createdAt);

        // Restore pipe family if applicable
        if (layer == ConnectionType.PIPE) {
            pipeFamilies.put(networkId, pipeFamily);
        }

        version.incrementAndGet();
        logger.debug("Imported network %s with %d positions", networkId, positions.size());
    }
}


