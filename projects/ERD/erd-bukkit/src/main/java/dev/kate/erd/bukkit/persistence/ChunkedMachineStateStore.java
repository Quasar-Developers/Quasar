package dev.kate.erd.bukkit.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.kate.erd.core.machine.MachineSnapshot;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ChunkKey;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.util.ErdLogger;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chunk-based machine persistence for scalability.
 *
 * <p>Stores one JSON file per chunk containing all machines anchored in that chunk.
 * Only loads/saves chunks that are actually in use.
 */
public final class ChunkedMachineStateStore {

    private static final Gson GSON = new GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .create();

    private final ErdLogger logger;
    private final Path baseDir;

    // In-memory cache: ChunkKey -> machines in that chunk
    private final Map<ChunkKey, List<MachineEntry>> loadedChunks = new ConcurrentHashMap<>();

    public ChunkedMachineStateStore(Path baseDir, ErdLogger logger) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

    // ========== Chunk lifecycle ==========

    /**
     * Loads machines for a specific chunk.
     * Returns entries for machines anchored in that chunk.
     */
    public List<MachineEntry> loadChunk(ChunkKey chunk) {
        // Check cache first
        if (loadedChunks.containsKey(chunk)) {
            return loadedChunks.get(chunk);
        }

        // Load from disk
        List<MachineEntry> entries = loadChunkFromDisk(chunk);
        loadedChunks.put(chunk, entries);
        return entries;
    }

    /**
     * Saves machines for all loaded chunks and clears cache.
     */
    public void saveAll(Map<ChunkKey, List<MachineEntry>> entriesByChunk) {
        int totalMachines = 0;
        for (var entry : entriesByChunk.entrySet()) {
            totalMachines += entry.getValue().size();
            saveChunkToDisk(entry.getKey(), entry.getValue());
        }
        loadedChunks.clear();
        if (totalMachines > 0) {
            logger.info("Saved %d machines across %d chunks", totalMachines, entriesByChunk.size());
        }
    }

    /**
     * Saves only the chunks that are currently loaded/dirty.
     */
    public void flush() {
        int totalMachines = 0;
        for (var entry : loadedChunks.entrySet()) {
            totalMachines += entry.getValue().size();
            saveChunkToDisk(entry.getKey(), entry.getValue());
        }
        if (totalMachines > 0) {
            logger.info("Flushed %d machines from %d chunks", totalMachines, loadedChunks.size());
        }
    }

    /**
     * Called when a chunk unloads. Saves to disk and evicts from cache.
     */
    public void onChunkUnload(ChunkKey chunk) {
        List<MachineEntry> entries = loadedChunks.remove(chunk);
        if (entries != null) {
            saveChunkToDisk(chunk, entries);
        }
    }

    // ========== Disk I/O ==========

    private List<MachineEntry> loadChunkFromDisk(ChunkKey chunk) {
        Path file = getChunkFile(chunk);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }

        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            MachineEntry[] arr = GSON.fromJson(r, MachineEntry[].class);
            if (arr == null) return new ArrayList<>();
            return new ArrayList<>(Arrays.asList(arr));
        } catch (Exception e) {
            logger.error("Failed to load machines from chunk %s: %s", chunk, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveChunkToDisk(ChunkKey chunk, List<MachineEntry> entries) {
        if (entries.isEmpty()) {
            // Delete file if chunk has no machines
            Path file = getChunkFile(chunk);
            try {
                Files.deleteIfExists(file);
            } catch (Exception e) {
                logger.error("Failed to delete empty chunk file %s: %s", chunk, e.getMessage());
            }
            return;
        }

        Path file = getChunkFile(chunk);
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(entries, w);
            }
        } catch (Exception e) {
            logger.error("Failed to save machines to chunk %s: %s", chunk, e.getMessage());
        }
    }

    private Path getChunkFile(ChunkKey chunk) {
        // plugins/ERD/machines/{worldId}/{chunkX}_{chunkZ}.json
        String worldId = chunk.worldId().toString();
        String fileName = chunk.chunkX() + "_" + chunk.chunkZ() + ".json";
        return baseDir.resolve("machines")
            .resolve(worldId)
            .resolve(fileName);
    }

    /**
     * Discovers all saved chunks from disk across all worlds.
     * @return List of chunk keys that have saved machine data
     */
    public List<ChunkKey> getAllSavedChunks() {
        List<ChunkKey> chunks = new ArrayList<>();
        Path machinesDir = baseDir.resolve("machines");
        
        if (!Files.exists(machinesDir)) {
            return chunks;
        }
        
        try (var worldDirStream = Files.list(machinesDir)) {
            // Iterate through world directories
            List<Path> worldDirs = worldDirStream.filter(Files::isDirectory).toList();
            
            for (Path worldDir : worldDirs) {
                try {
                    UUID worldId = UUID.fromString(worldDir.getFileName().toString());
                    
                    // Iterate through chunk files
                    try (var chunkFileStream = Files.list(worldDir)) {
                        List<Path> chunkFiles = chunkFileStream
                            .filter(p -> p.getFileName().toString().endsWith(".json"))
                            .toList();
                            
                        for (Path chunkFile : chunkFiles) {
                            try {
                                // Parse filename: {chunkX}_{chunkZ}.json
                                String fileName = chunkFile.getFileName().toString();
                                String baseName = fileName.substring(0, fileName.length() - 5); // Remove .json
                                String[] parts = baseName.split("_");
                                if (parts.length == 2) {
                                    int chunkX = Integer.parseInt(parts[0]);
                                    int chunkZ = Integer.parseInt(parts[1]);
                                    chunks.add(new ChunkKey(worldId, chunkX, chunkZ));
                                }
                            } catch (NumberFormatException e) {
                                logger.error("Failed to parse chunk coordinates from file name: %s", chunkFile.getFileName());
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Failed to list chunk files in world directory %s: %s", 
                            worldDir.getFileName(), e.getMessage());
                    }
                } catch (IllegalArgumentException e) {
                    logger.error("Invalid world UUID in directory name: %s", worldDir.getFileName());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to discover saved chunks: %s", e.getMessage());
        }
        
        return chunks;
    }

    // ========== Data structures ==========

    /**
     * Persisted machine record with full structure and state information.
     */
    public record MachineEntry(
        String machineId,
        String typeId,
        String worldId,
        int x,
        int y,
        int z,
        List<int[]> occupiedPositions,      // Relative positions [dx, dy, dz]
        List<ComponentEntry> components,
        int stateVersion,
        Map<String, Object> state
    ) {
        public MachineSnapshot toSnapshot() {
            BlockPos anchor = new BlockPos(UUID.fromString(worldId), x, y, z);

            // Convert relative positions back to absolute
            Set<BlockPos> positions = new HashSet<>();
            positions.add(anchor);
            if (occupiedPositions != null) {
                for (int[] rel : occupiedPositions) {
                    positions.add(anchor.offset(rel[0], rel[1], rel[2]));
                }
            }

            // Convert component entries
            List<MachineSnapshot.ComponentSnapshot> componentSnapshots = new ArrayList<>();
            if (components != null) {
                for (ComponentEntry ce : components) {
                    componentSnapshots.add(ce.toComponentSnapshot(anchor));
                }
            }

            return new MachineSnapshot(
                MachineId.parse(machineId),
                typeId,
                anchor,
                positions,
                null, // spannedChunks computed automatically
                componentSnapshots,
                stateVersion,
                state
            );
        }

        public BlockPos toAnchorPos() {
            return new BlockPos(UUID.fromString(worldId), x, y, z);
        }

        public static MachineEntry from(BlockPos anchor, MachineSnapshot snapshot) {
            // Convert absolute positions to relative
            List<int[]> relativePositions = new ArrayList<>();
            for (BlockPos pos : snapshot.occupiedPositions()) {
                if (!pos.equals(anchor)) {
                    relativePositions.add(new int[]{
                        pos.x() - anchor.x(),
                        pos.y() - anchor.y(),
                        pos.z() - anchor.z()
                    });
                }
            }

            // Convert components
            List<ComponentEntry> componentEntries = new ArrayList<>();
            for (MachineSnapshot.ComponentSnapshot cs : snapshot.components()) {
                componentEntries.add(ComponentEntry.from(anchor, cs));
            }

            return new MachineEntry(
                snapshot.id().toString(),
                snapshot.typeId(),
                anchor.worldId().toString(),
                anchor.x(),
                anchor.y(),
                anchor.z(),
                relativePositions,
                componentEntries,
                snapshot.stateVersion(),
                snapshot.state()
            );
        }
    }

    /**
     * Persisted component record.
     */
    public record ComponentEntry(
        String componentId,
        String componentTypeId,
        int attachX,
        int attachY,
        int attachZ,
        List<int[]> occupiedPositions,
        int stateVersion,
        Map<String, Object> state
    ) {
        public MachineSnapshot.ComponentSnapshot toComponentSnapshot(BlockPos machineAnchor) {
            BlockPos attachmentPoint = machineAnchor.offset(attachX, attachY, attachZ);

            Set<BlockPos> positions = new HashSet<>();
            positions.add(attachmentPoint);
            if (occupiedPositions != null) {
                for (int[] rel : occupiedPositions) {
                    positions.add(attachmentPoint.offset(rel[0], rel[1], rel[2]));
                }
            }

            return new MachineSnapshot.ComponentSnapshot(
                dev.kate.erd.core.machine.component.ComponentId.parse(componentId),
                componentTypeId,
                attachmentPoint,
                positions,
                stateVersion,
                state
            );
        }

        public static ComponentEntry from(BlockPos machineAnchor, MachineSnapshot.ComponentSnapshot snapshot) {
            BlockPos attach = snapshot.attachmentPoint();

            List<int[]> relativePositions = new ArrayList<>();
            for (BlockPos pos : snapshot.occupiedPositions()) {
                if (!pos.equals(attach)) {
                    relativePositions.add(new int[]{
                        pos.x() - attach.x(),
                        pos.y() - attach.y(),
                        pos.z() - attach.z()
                    });
                }
            }

            return new ComponentEntry(
                snapshot.id().toString(),
                snapshot.componentTypeId(),
                attach.x() - machineAnchor.x(),
                attach.y() - machineAnchor.y(),
                attach.z() - machineAnchor.z(),
                relativePositions,
                snapshot.stateVersion(),
                snapshot.state()
            );
        }
    }
}
