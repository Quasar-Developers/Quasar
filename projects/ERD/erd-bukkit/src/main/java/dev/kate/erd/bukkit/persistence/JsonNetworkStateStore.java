package dev.kate.erd.bukkit.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.kate.erd.core.persistence.NetworkStateStore;
import dev.kate.erd.core.util.ErdLogger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Simple JSON file-backed implementation of {@link NetworkStateStore}.
 *
 * <p>Stores all type snapshots + DATA control plane state in a single JSON file.
 * Intended for Bukkit adapter usage (plugin data folder).</p>
 */
public final class JsonNetworkStateStore implements NetworkStateStore {

    private static final Gson GSON = new GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .create();

    private final ErdLogger logger;
    private final Path file;

    private volatile Persisted persisted = new Persisted();

    public JsonNetworkStateStore(Path file, ErdLogger logger) {
        this.file = Objects.requireNonNull(file, "file must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        loadFromDisk();
    }

    @Override
    public synchronized void saveConnectionState(dev.kate.erd.core.model.ConnectionType layer, ConnectionStateData state) {
        Objects.requireNonNull(layer, "type must not be null");
        Objects.requireNonNull(state, "state must not be null");
        persisted.ConnectionStates.put(layer.name(), state);
    }

    @Override
    public synchronized Optional<ConnectionStateData> loadConnectionState(dev.kate.erd.core.model.ConnectionType layer) {
        Objects.requireNonNull(layer, "type must not be null");
        return Optional.ofNullable(persisted.ConnectionStates.get(layer.name()));
    }

    @Override
    public synchronized void saveControlPlaneState(ControlPlaneStateData state) {
        Objects.requireNonNull(state, "state must not be null");
        persisted.controlPlaneState = state;
    }

    @Override
    public synchronized Optional<ControlPlaneStateData> loadControlPlaneState() {
        return Optional.ofNullable(persisted.controlPlaneState);
    }

    @Override
    public synchronized void clear() {
        persisted = new Persisted();
        flush();
    }

    @Override
    public synchronized void flush() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(persisted, w);
            }
        } catch (IOException e) {
            logger.error("Failed to flush network state store to %s: %s", file, e.getMessage());
        }
    }

    private void loadFromDisk() {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Persisted loaded = GSON.fromJson(r, Persisted.class);
            if (loaded != null) {
                persisted = loaded;
            }
        } catch (Exception e) {
            logger.error("Failed to load network state store from %s: %s", file, e.getMessage());
        }
    }

    private static final class Persisted {
        java.util.Map<String, ConnectionStateData> ConnectionStates = new java.util.HashMap<>();
        ControlPlaneStateData controlPlaneState;
    }
}
