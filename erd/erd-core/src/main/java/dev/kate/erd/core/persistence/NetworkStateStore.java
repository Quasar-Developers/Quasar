package dev.kate.erd.core.persistence;

import dev.kate.erd.core.dataplane.Binding;
import dev.kate.erd.core.model.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Interface for persisting network state.
 *
 * <p>Implementations handle saving and loading network topology data.
 * The core provides interface definitions; actual persistence implementations
 * (file-based, database, etc.) are in adapter modules.
 *
 * <p>Thread-safety: Implementations should be thread-safe for read operations.
 * Write operations should be serialized.
 */
public interface NetworkStateStore {

    /**
     * Saves the state of a network type.
     *
     * @param layer the type kind
     * @param state the state to save
     */
    void saveConnectionState(ConnectionType layer, ConnectionStateData state);

    /**
     * Loads the state of a network type.
     *
     * @param layer the type kind
     * @return the loaded state, or empty if none saved
     */
    Optional<ConnectionStateData> loadConnectionState(ConnectionType layer);

    /**
     * Saves DATA control plane state.
     *
     * @param state the control plane state
     */
    void saveControlPlaneState(ControlPlaneStateData state);

    /**
     * Loads DATA control plane state.
     *
     * @return the loaded state, or empty if none saved
     */
    Optional<ControlPlaneStateData> loadControlPlaneState();

    /**
     * Clears all persisted state.
     */
    void clear();

    /**
     * Flushes any buffered writes to persistent storage.
     */
    void flush();

    /**
     * Persisted state for a network type.
     *
     * @param type the type kind
     * @param networks the network data
     * @param version the version at save time
     */
    record ConnectionStateData(
            ConnectionType type,
            List<NetworkData> networks,
            long version
    ) {
        public ConnectionStateData {
            networks = List.copyOf(networks);
        }
    }

    /**
     * Persisted data for a single network.
     *
     * @param networkId the network ID
     * @param segmentPositions the segment positions
     * @param createdAt creation timestamp
     * @param pipeFamily pipe family (for PIPE networks)
     */
    record NetworkData(
            NetworkId networkId,
            Set<BlockPos> segmentPositions,
            long createdAt,
            PipeFamily pipeFamily
    ) {
        public NetworkData {
            segmentPositions = Set.copyOf(segmentPositions);
        }
    }

    /**
     * Persisted state for the DATA control plane.
     *
     * @param registryData per-network registry data
     * @param bindings all bindings
     */
    record ControlPlaneStateData(
            List<RegistryData> registryData,
            List<Binding> bindings
    ) {
        public ControlPlaneStateData {
            registryData = List.copyOf(registryData);
            bindings = List.copyOf(bindings);
        }
    }

    /**
     * Persisted registry data for a DATA network.
     *
     * @param networkId the network ID
     * @param mainframeData mainframe creation times for leader election
     */
    record RegistryData(
            NetworkId networkId,
            Map<ControllerId, Long> mainframeData
    ) {
        public RegistryData {
            mainframeData = Map.copyOf(mainframeData);
        }
    }
}
