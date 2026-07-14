package dev.kate.erd.core.persistence;

import dev.kate.erd.core.model.ConnectionType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of NetworkStateStore for testing.
 *
 * <p>This store keeps all data in memory and does not persist across
 * JVM restarts. Useful for unit tests and development.
 *
 * <p>Thread-safety: This implementation is NOT thread-safe.
 */
public final class InMemoryStateStore implements NetworkStateStore {

    private final Map<ConnectionType, ConnectionStateData> ConnectionStates = new EnumMap<>(ConnectionType.class);
    private ControlPlaneStateData controlPlaneState;

    @Override
    public void saveConnectionState(ConnectionType layer, ConnectionStateData state) {
        ConnectionStates.put(layer, state);
    }

    @Override
    public Optional<ConnectionStateData> loadConnectionState(ConnectionType layer) {
        return Optional.ofNullable(ConnectionStates.get(layer));
    }

    @Override
    public void saveControlPlaneState(ControlPlaneStateData state) {
        this.controlPlaneState = state;
    }

    @Override
    public Optional<ControlPlaneStateData> loadControlPlaneState() {
        return Optional.ofNullable(controlPlaneState);
    }

    @Override
    public void clear() {
        ConnectionStates.clear();
        controlPlaneState = null;
    }

    @Override
    public void flush() {
        // No-op for in-memory store
    }
}
