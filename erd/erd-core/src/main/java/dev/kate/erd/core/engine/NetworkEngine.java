package dev.kate.erd.core.engine;

import dev.kate.erd.core.event.EventBus;
import dev.kate.erd.core.event.topology.TopologyChangedEvent;
import dev.kate.erd.core.model.*;
import dev.kate.erd.core.persistence.NetworkStateStore;
import dev.kate.erd.core.topology.TopologyResult;
import dev.kate.erd.core.util.Clock;
import dev.kate.erd.core.util.ErdLogger;

import java.util.*;

/**
 * Unified multi-type network engine for POWER, PIPE, and DATA networks.
 *
 * <p>The NetworkEngine manages topology state for all network types and provides
 * a clean API for segment operations, network queries, and chunk management.
 * All mutations are serialized through the operation queue for thread safety.
 *
 * <p>Thread-safety: This class is NOT thread-safe for mutations. Use the
 * {@link #getOperationQueue()} to enqueue operations from other threads.
 * Query methods may be called from any thread but return snapshots/copies.
 *
 * <h2>Usage Pattern</h2>
 * <pre>
 * // Create engine
 * NetworkEngine engine = new NetworkEngine(logger, clock);
 *
 * // Queue operations (thread-safe)
 * engine.getOperationQueue().enqueue(new EngineOperation.AddSegment(ConnectionType.POWER, pos));
 *
 * // Process on main thread
 * engine.processQueue();
 * </pre>
 */
public final class NetworkEngine {

    private final ErdLogger logger;
    private final Clock clock;
    private final EngineOperationQueue operationQueue;
    private final EventBus eventBus;

    // Network states by connection type
    private final EnumMap<ConnectionType, ConnectionState> layers = new EnumMap<>(ConnectionType.class);

    // Event listeners for topology changes
    private final List<NetworkEventListener> eventListeners = new ArrayList<>();

    /**
     * Creates a new NetworkEngine.
     *
     * @param logger the logger to use
     * @param clock the clock for timestamps
     */
    public NetworkEngine(ErdLogger logger, Clock clock) {
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.operationQueue = new EngineOperationQueue(logger);
        this.eventBus = new EventBus(logger);

        // Initialize all connection types
        for (ConnectionType type : ConnectionType.values()) {
            layers.put(type, new ConnectionState(type, logger));
        }

        logger.info("NetworkEngine initialized with %d connection types", layers.size());
    }

    /**
     * @return the operation queue for enqueuing mutations
     */
    public EngineOperationQueue getOperationQueue() {
        return operationQueue;
    }

    /**
     * @return the event bus for topology events
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Processes all pending operations in the queue.
     * MUST be called from the main/designated thread.
     *
     * @return number of operations processed
     */
    public int processQueue() {
        int count = 0;
        EngineOperation op;
        while ((op = operationQueue.poll()) != null) {
            processOperation(op);
            count++;
        }
        return count;
    }

    /**
     * Processes up to maxOperations from the queue.
     * Use for rate-limiting in tick processing.
     *
     * @param maxOperations maximum operations to process
     * @return number of operations processed
     */
    public int processQueue(int maxOperations) {
        int count = 0;
        EngineOperation op;
        while (count < maxOperations && (op = operationQueue.poll()) != null) {
            processOperation(op);
            count++;
        }
        return count;
    }

    // ========== Direct API (for same-thread usage) ==========

    /**
     * Adds a connection segment at the specified position.
     * Call from main thread only.
     *
     * @param type the connection type (POWER, PIPE, DATA)
     * @param position the segment position
     * @return the topology result
     */
    public TopologyResult addSegment(ConnectionType type, BlockPos position) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(position, "position must not be null");

        ConnectionState state = layers.get(type);
        TopologyResult result = state.addSegment(position, clock.nowMillis());

        notifyListeners(type, result);
        return result;
    }

    /**
     * Removes a connection segment at the specified position.
     * Call from main thread only.
     *
     * @param type the connection type (POWER, PIPE, DATA)
     * @param position the segment position
     * @return the topology result
     */
    public TopologyResult removeSegment(ConnectionType type, BlockPos position) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(position, "position must not be null");

        ConnectionState state = layers.get(type);
        TopologyResult result = state.removeSegment(position);

        notifyListeners(type, result);
        return result;
    }

    /**
     * Marks a chunk as loaded.
     *
     * @param chunk the chunk key
     */
    public void onChunkLoaded(ChunkKey chunk) {
        Objects.requireNonNull(chunk, "chunk must not be null");
        for (ConnectionState state : layers.values()) {
            state.activateChunk(chunk);
        }
    }

    /**
     * Marks a chunk as unloaded.
     *
     * @param chunk the chunk key
     */
    public void onChunkUnloaded(ChunkKey chunk) {
        Objects.requireNonNull(chunk, "chunk must not be null");
        for (ConnectionState state : layers.values()) {
            state.deactivateChunk(chunk);
        }
    }

    // ========== Query API ==========

    /**
     * Gets the network containing a position.
     *
     * @param type the connection type
     * @param position the position
     * @return the network ID, or empty if not in any network
     */
    public Optional<NetworkId> getNetworkAt(ConnectionType type, BlockPos position) {
        return layers.get(type).getNetworkAt(position);
    }

    /**
     * Gets all segment positions in a network.
     *
     * @param type the connection type
     * @param networkId the network ID
     * @return unmodifiable set of positions
     */
    public Set<BlockPos> getNetworkSegments(ConnectionType type, NetworkId networkId) {
        return layers.get(type).getPositions(networkId);
    }

    /**
     * Gets all network IDs for a connection type.
     *
     * @param type the connection type
     * @return unmodifiable set of network IDs
     */
    public Set<NetworkId> getAllNetworks(ConnectionType type) {
        return layers.get(type).getAllNetworkIds();
    }

    /**
     * Gets the current version of a connection type (for async operations).
     *
     * @param type the connection type
     * @return the version number
     */
    public long getVersion(ConnectionType type) {
        return layers.get(type).getVersion();
    }

    /**
     * Creates a snapshot of a connection type for async computation.
     *
     * @param type the connection type
     * @return an immutable snapshot
     */
    public LayerSnapshot createSnapshot(ConnectionType type) {
        return layers.get(type).createSnapshot();
    }

    /**
     * Gets the pipe family for a PIPE network.
     *
     * @param networkId the network ID
     * @return the family, or UNASSIGNED
     */
    public PipeFamily getPipeFamily(NetworkId networkId) {
        return layers.get(ConnectionType.PIPE).getPipeFamily(networkId);
    }

    /**
     * Sets the pipe family for a PIPE network.
     *
     * @param networkId the network ID
     * @param family the family to set
     */
    public void setPipeFamily(NetworkId networkId, PipeFamily family) {
        layers.get(ConnectionType.PIPE).setPipeFamily(networkId, family);
    }

    /**
     * Gets statistics for a connection type.
     *
     * @param type the connection type
     * @return statistics object
     */
    public NetworkStatistics getStatistics(ConnectionType type) {
        ConnectionState state = layers.get(type);
        return new NetworkStatistics(
            type,
            state.getNetworkCount(),
            state.getTotalSegmentCount(),
            state.getVersion()
        );
    }

    // ========== Event System ==========

    /**
     * Registers a listener for network topology events.
     *
     * @param listener the listener to register
     */
    public void addListener(NetworkEventListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        eventListeners.add(listener);
    }

    /**
     * Removes a registered listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(NetworkEventListener listener) {
        eventListeners.remove(listener);
    }

    // ========== Internal ==========

    private void processOperation(EngineOperation op) {
        try {
            switch (op) {
                case EngineOperation.AddSegment add -> {
                    TopologyResult result = addSegment(add.type(), add.position());
                    if (add.callback() != null) {
                        add.callback().accept(new OperationResult.Success(result));
                    }
                }
                case EngineOperation.RemoveSegment remove -> {
                    TopologyResult result = removeSegment(remove.type(), remove.position());
                    if (remove.callback() != null) {
                        remove.callback().accept(new OperationResult.Success(result));
                    }
                }
                case EngineOperation.ChunkLoaded loaded -> onChunkLoaded(loaded.chunk());
                case EngineOperation.ChunkUnloaded unloaded -> onChunkUnloaded(unloaded.chunk());
                case EngineOperation.SetPipeFamily spf -> {
                    try {
                        setPipeFamily(spf.networkId(), spf.family());
                        if (spf.callback() != null) {
                            spf.callback().accept(new OperationResult.Success(null));
                        }
                    } catch (Exception e) {
                        if (spf.callback() != null) {
                            spf.callback().accept(new OperationResult.Error(e.getMessage(), e));
                        }
                    }
                }
                case EngineOperation.ApplyAsyncResult async -> applyAsyncResult(async);
                case EngineOperation.Batch batch -> {
                    for (EngineOperation subOp : batch.operations()) {
                        processOperation(subOp);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error processing operation: " + op, e);
        }
    }

    private void applyAsyncResult(EngineOperation.ApplyAsyncResult async) {
        ConnectionState state = layers.get(async.type());
        long currentVersion = state.getVersion();

        if (currentVersion != async.expectedVersion()) {
            logger.debug("Async result version mismatch: expected %d, got %d",
                async.expectedVersion(), currentVersion);
            if (async.callback() != null) {
                async.callback().accept(
                    new OperationResult.VersionMismatch(async.expectedVersion(), currentVersion));
            }
            return;
        }

        try {
            async.computation().apply(state);
            if (async.callback() != null) {
                async.callback().accept(new OperationResult.Success(null));
            }
        } catch (Exception e) {
            logger.error("Error applying async result", e);
            if (async.callback() != null) {
                async.callback().accept(new OperationResult.Error(e.getMessage(), e));
            }
        }
    }

    private void notifyListeners(ConnectionType type, TopologyResult result) {
        if (result instanceof TopologyResult.NoChange) {
            return;
        }

        // Dispatch to event bus
        eventBus.dispatch(new TopologyChangedEvent(type, result));

        // Legacy listener support
        for (NetworkEventListener listener : eventListeners) {
            try {
                listener.onTopologyChanged(type, result);
            } catch (Exception e) {
                logger.error("Error in event listener", e);
            }
        }
    }

    /**
     * Exports a connection type's current topology for persistence/inspection.
     */
    public NetworkStateStore.ConnectionStateData exportConnectionState(ConnectionType type) {
        Objects.requireNonNull(type, "type must not be null");
        return layers.get(type).exportState();
    }

    /**
     * Imports a connection type's topology from persisted state.
     * This reconstructs networks from saved data, preserving original network IDs.
     */
    public void importConnectionState(NetworkStateStore.ConnectionStateData data) {
        Objects.requireNonNull(data, "data must not be null");

        ConnectionType type = data.type();
        ConnectionState ConnectionState = layers.get(type);

        // Import each network directly, preserving IDs
        for (NetworkStateStore.NetworkData network : data.networks()) {
            ConnectionState.importNetwork(network);
        }

        logger.info("Imported %d networks for %s", data.networks().size(), type);
    }

    /**
     * Statistics for a network type.
     */
    public record NetworkStatistics(
            ConnectionType type,
            int networkCount,
            int segmentCount,
            long version
    ) {}
}
