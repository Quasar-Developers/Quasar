package dev.kate.erd.bukkit.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.kate.erd.core.model.*;
import dev.kate.erd.core.persistence.NetworkStateStore;
import dev.kate.erd.core.util.ErdLogger;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chunk-based network persistence for scalability.
 *
 * <p>Instead of loading ALL networks into memory at startup, this store:
 * <ul>
 *   <li>Persists one JSON file per chunk</li>
 *   <li>Only loads chunks when they become active (loaded in-game)</li>
 *   <li>Networks spanning multiple chunks are indexed per-chunk</li>
 * </ul>
 *
 * <p>Performance: O(chunks_loaded) instead of O(total_networks).
 */
public final class ChunkedNetworkStateStore implements NetworkStateStore {

    private static final Gson GSON = new GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .create();

    private final ErdLogger logger;
    private final Path baseDir;

    // In-memory index: (ChunkKey, ConnectionType) -> cached chunk data
    // Using a composite key to separate data by chunk AND layer
    private final Map<ChunkLayerKey, ChunkData> loadedChunks = new ConcurrentHashMap<>();

    // Network metadata cache (shared across chunks): NetworkId -> family/createdAt
    private final Map<NetworkId, NetworkMeta> networkMetadata = new ConcurrentHashMap<>();
    
    // Helper record for composite key
    private record ChunkLayerKey(ChunkKey chunk, ConnectionType layer) {}

    public ChunkedNetworkStateStore(Path baseDir, ErdLogger logger) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        loadNetworkMetadata();
    }

    // ========== Network Metadata ==========

    /**
     * Gets the pipe family for a network.
     *
     * @param networkId the network ID
     * @return the pipe family, or UNASSIGNED if not found
     */
    public PipeFamily getPipeFamily(NetworkId networkId) {
        NetworkMeta meta = networkMetadata.get(networkId);
        return meta != null ? meta.pipeFamily() : PipeFamily.UNASSIGNED;
    }

    /**
     * Gets the creation time for a network.
     *
     * @param networkId the network ID
     * @return the creation time, or 0 if not found
     */
    public long getCreatedAt(NetworkId networkId) {
        NetworkMeta meta = networkMetadata.get(networkId);
        return meta != null ? meta.createdAt() : 0L;
    }

    /**
     * Updates network metadata.
     *
     * @param networkId the network ID
     * @param pipeFamily the pipe family
     * @param createdAt the creation time
     */
    public void updateNetworkMeta(NetworkId networkId, PipeFamily pipeFamily, long createdAt) {
        networkMetadata.put(networkId, new NetworkMeta(networkId, pipeFamily, createdAt));
    }

    private void loadNetworkMetadata() {
        Path file = getNetworkMetaFile();
        if (!Files.exists(file)) return;

        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            NetworkMetaData data = GSON.fromJson(r, NetworkMetaData.class);
            if (data != null && data.networks() != null) {
                for (NetworkMeta meta : data.networks()) {
                    networkMetadata.put(meta.networkId(), meta);
                }
            }
            logger.info("Loaded %d network metadata entries", networkMetadata.size());
        } catch (Exception e) {
            logger.error("Failed to load network metadata: %s", e.getMessage());
        }
    }

    private void saveNetworkMetadata() {
        Path file = getNetworkMetaFile();
        try {
            Files.createDirectories(file.getParent());
            NetworkMetaData data = new NetworkMetaData(new ArrayList<>(networkMetadata.values()));
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            logger.error("Failed to save network metadata: %s", e.getMessage());
        }
    }

    private Path getNetworkMetaFile() {
        return baseDir.resolve("networks").resolve("network_meta.json");
    }

    // ========== Chunk lifecycle ==========

    /**
     * Loads network segment data for a specific chunk and connection type.
     * Returns the list of segment entries with their network IDs preserved.
     *
     * @param chunk the chunk to load
     * @param type the connection type (POWER, PIPE, DATA)
     * @return list of segment entries in this chunk, or empty list if none
     */
    public List<SegmentEntry> loadChunkSegments(ChunkKey chunk, ConnectionType type) {
        return loadChunkEntries(chunk, type);
    }

    /**
     * Saves network segment data for a specific chunk and connection type, preserving network IDs.
     *
     * @param chunk the chunk to save
     * @param type the connection type (POWER, PIPE, DATA)
     * @param segments the segment entries with network IDs
     */
    public void saveChunkSegments(ChunkKey chunk, ConnectionType type, List<SegmentEntry> segments) {
        saveChunkEntries(chunk, type, segments);
    }

    /**
     * Loads network segment data for a specific chunk and type.
     * Returns the list of segment entries with their network IDs preserved.
     *
     * @param chunk the chunk to load
     * @param layer the network type
     * @return list of segment entries in this chunk, or empty list if none
     */
    public List<SegmentEntry> loadChunkEntries(ChunkKey chunk, ConnectionType layer) {
        ChunkLayerKey key = new ChunkLayerKey(chunk, layer);
        
        if (loadedChunks.containsKey(key)) {
            // Already loaded
            ChunkData data = loadedChunks.get(key);
            return data.segments().stream()
                .filter(c -> c.type() == layer)
                .toList();
        }

        var chunkData = loadChunkFromDisk(chunk, layer);
        if (chunkData != null) {
            loadedChunks.put(key, chunkData);
            return chunkData.segments().stream()
                .filter(c -> c.type() == layer)
                .toList();
        }

        return List.of();
    }

    /**
     * Saves network segment data for a specific chunk and type, preserving network IDs.
     *
     * @param chunk the chunk to save
     * @param layer the network type
     * @param entries the segment entries with network IDs
     */
    public void saveChunkEntries(ChunkKey chunk, ConnectionType layer, List<SegmentEntry> entries) {
        ChunkLayerKey key = new ChunkLayerKey(chunk, layer);
        
        if (entries.isEmpty()) {
            // Remove the chunk file if no segments
            loadedChunks.remove(key);
            Path file = getChunkFile(chunk, layer);
            try {
                Files.deleteIfExists(file);
            } catch (Exception e) {
                logger.error("Failed to delete chunk file %s: %s", file, e.getMessage());
            }
            return;
        }

        ChunkData chunkData = new ChunkData(entries, System.currentTimeMillis());
        loadedChunks.put(key, chunkData);
        saveChunkToDisk(chunk, layer, chunkData);
    }

    /**
     * Saves all currently loaded chunks and network metadata to disk.
     */
    @Override
    public void flush() {
        int totalSegments = 0;
        int totalChunks = 0;
        for (var entry : loadedChunks.entrySet()) {
            ChunkLayerKey key = entry.getKey();
            ChunkData data = entry.getValue();
            totalSegments += data.segments().size();
            totalChunks++;
            saveChunkToDisk(key.chunk(), key.layer(), data);
        }
        // Also save network metadata (PipeFamily, createdAt)
        saveNetworkMetadata();
        if (totalSegments > 0) {
            logger.info("Flushed %d network segments from %d chunk-layers, %d metadata entries", 
                totalSegments, totalChunks, networkMetadata.size());
        }
    }

    // ========== NetworkStateStore implementation ==========

    @Override
    public void saveConnectionState(ConnectionType layer, ConnectionStateData state) {
        // Convert full type state into per-chunk files
        Map<ChunkKey, List<SegmentEntry>> cablesByChunk = new HashMap<>();

        for (NetworkData net : state.networks()) {
            NetworkId netId = net.networkId();

            // Cache metadata
            networkMetadata.put(netId, new NetworkMeta(netId, net.pipeFamily(), net.createdAt()));

            // Distribute segment positions by chunk
            for (BlockPos pos : net.segmentPositions()) {
                ChunkKey chunk = pos.toChunkKey();
                cablesByChunk.computeIfAbsent(chunk, k -> new ArrayList<>())
                    .add(new SegmentEntry(netId, pos, layer));
            }
        }

        // Clear stale cache entries for this layer before writing fresh data
        loadedChunks.entrySet().removeIf(e -> e.getKey().layer() == layer);

        // Write one file per chunk and update the cache
        for (var entry : cablesByChunk.entrySet()) {
            ChunkKey chunk = entry.getKey();
            List<SegmentEntry> segments = entry.getValue();

            ChunkData chunkData = new ChunkData(segments, state.version());
            saveChunkToDisk(chunk, layer, chunkData);
            loadedChunks.put(new ChunkLayerKey(chunk, layer), chunkData);
        }
    }

    @Override
    public Optional<ConnectionStateData> loadConnectionState(ConnectionType layer) {
        // This is called at startup. Instead of loading everything, we return empty.
        // Actual data is loaded per-chunk via onChunkLoad().
        return Optional.empty();
    }

    @Override
    public void saveControlPlaneState(ControlPlaneStateData state) {
        // TODO: implement control plane persistence (can be single file, it's small)
    }

    @Override
    public Optional<ControlPlaneStateData> loadControlPlaneState() {
        return Optional.empty();
    }

    @Override
    public void clear() {
        loadedChunks.clear();
        networkMetadata.clear();
        // TODO: delete all chunk files on disk
    }

    // ========== Disk I/O ==========

    private ChunkData loadChunkFromDisk(ChunkKey chunk, ConnectionType layer) {
        Path file = getChunkFile(chunk, layer);
        if (!Files.exists(file)) return null;

        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, ChunkData.class);
        } catch (Exception e) {
            logger.error("Failed to load chunk %s type %s: %s", chunk, layer, e.getMessage());
            return null;
        }
    }

    private void saveChunkToDisk(ChunkKey chunk, ConnectionType layer, ChunkData data) {
        Path file = getChunkFile(chunk, layer);
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            logger.error("Failed to save chunk %s type %s: %s", chunk, layer, e.getMessage());
        }
    }

    private Path getChunkFile(ChunkKey chunk, ConnectionType layer) {
        // plugins/ERD/networks/{worldId}/{type}/{chunkX}_{chunkZ}.json
        String worldId = chunk.worldId().toString();
        String fileName = chunk.chunkX() + "_" + chunk.chunkZ() + ".json";
        return baseDir.resolve("networks")
            .resolve(worldId)
            .resolve(layer.name().toLowerCase())
            .resolve(fileName);
    }

    // ========== Data structures ==========

    private record ChunkData(
        List<SegmentEntry> segments,
        long version
    ) {}

    /**
     * Represents a single connection segment entry with its network membership.
     *
     * @param networkId the ID of the network this segment belongs to
     * @param position the world position of the segment
     * @param type the connection type (POWER, PIPE, DATA)
     */
    public record SegmentEntry(
        NetworkId networkId,
        BlockPos position,
        ConnectionType type
    ) {
    }

    private record NetworkMeta(
        NetworkId networkId,
        PipeFamily pipeFamily,
        long createdAt
    ) {}

    private record NetworkMetaData(
        List<NetworkMeta> networks
    ) {}
}
