package dev.kate.erd.bukkit.adapter;

import dev.kate.erd.core.machine.StructureSnapshot;
import dev.kate.erd.core.model.BlockPos;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds StructureSnapshot instances from Bukkit ChunkSnapshot for async validation.
 *
 * <p>This builder uses ChunkSnapshot which is thread-safe and can be accessed
 * from async threads without blocking the main thread.
 *
 * <p>Thread-safety: Methods can be called from any thread after snapshots are captured.
 */
public final class AsyncSnapshotBuilder {

    // Cache of chunk snapshots for async access
    private final Map<ChunkKey, ChunkSnapshot> chunkCache = new ConcurrentHashMap<>();

    /**
     * Captures chunk snapshots for the given positions on the main thread.
     * Must be called from the main server thread.
     *
     * @param world the Bukkit world
     * @param positions the positions to capture
     */
    public void captureChunks(World world, Iterable<BlockPos> positions) {
        Objects.requireNonNull(world, "world must not be null");
        
        // Collect unique chunks
        Map<ChunkKey, Boolean> chunks = new HashMap<>();
        for (BlockPos pos : positions) {
            var chunkKey = new ChunkKey(pos.worldId(), pos.x() >> 4, pos.z() >> 4);
            chunks.put(chunkKey, true);
        }

        // Capture chunk snapshots (must be on main thread)
        for (ChunkKey key : chunks.keySet()) {
            if (!world.isChunkLoaded(key.chunkX, key.chunkZ)) continue;
            
            var chunk = world.getChunkAt(key.chunkX, key.chunkZ);
            var snapshot = chunk.getChunkSnapshot(false, false, false);
            chunkCache.put(key, snapshot);
        }
    }

    /**
     * Builds a structure snapshot from cached chunk snapshots.
     * Can be called from async thread.
     *
     * @param origin the origin position
     * @param positions positions to include in snapshot
     * @return the structure snapshot
     */
    public StructureSnapshot buildSnapshotAsync(BlockPos origin, Iterable<BlockPos> positions) {
        Objects.requireNonNull(origin, "origin must not be null");

        Map<BlockPos, StructureSnapshot.BlockData> blocks = new HashMap<>();

        for (BlockPos pos : positions) {
            var chunkKey = new ChunkKey(pos.worldId(), pos.x() >> 4, pos.z() >> 4);
            ChunkSnapshot chunk = chunkCache.get(chunkKey);
            
            if (chunk == null) {
                // Chunk not captured, treat as AIR
                blocks.put(pos, new StructureSnapshot.BlockData("minecraft:air", Map.of()));
                continue;
            }

            // Get block from chunk snapshot (thread-safe)
            int localX = pos.x() & 15;
            int localZ = pos.z() & 15;
            int y = pos.y();

            var blockData = chunk.getBlockData(localX, y, localZ);
            String typeKey = blockData.getMaterial().getKey().toString();

            // Parse properties from block data
            Map<String, String> properties = new HashMap<>();
            String dataString = blockData.getAsString();
            
            int bracketStart = dataString.indexOf('[');
            if (bracketStart != -1) {
                int bracketEnd = dataString.indexOf(']');
                if (bracketEnd > bracketStart) {
                    String propsString = dataString.substring(bracketStart + 1, bracketEnd);
                    for (String prop : propsString.split(",")) {
                        String[] parts = prop.split("=");
                        if (parts.length == 2) {
                            properties.put(parts[0].trim(), parts[1].trim());
                        }
                    }
                }
            }

            blocks.put(pos, new StructureSnapshot.BlockData(typeKey, properties));
        }

        // Also include origin
        var chunkKey = new ChunkKey(origin.worldId(), origin.x() >> 4, origin.z() >> 4);
        ChunkSnapshot chunk = chunkCache.get(chunkKey);
        
        if (chunk != null) {
            int localX = origin.x() & 15;
            int localZ = origin.z() & 15;
            int y = origin.y();

            var blockData = chunk.getBlockData(localX, y, localZ);
            String typeKey = blockData.getMaterial().getKey().toString();
            blocks.put(origin, new StructureSnapshot.BlockData(typeKey, Map.of()));
        } else {
            blocks.put(origin, new StructureSnapshot.BlockData("minecraft:air", Map.of()));
        }

        return new StructureSnapshot(blocks, origin);
    }

    /**
     * Clears the chunk snapshot cache.
     * Should be called after async validation completes.
     */
    public void clearCache() {
        chunkCache.clear();
    }

    /**
     * Simple chunk key for caching.
     */
    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {}
}
