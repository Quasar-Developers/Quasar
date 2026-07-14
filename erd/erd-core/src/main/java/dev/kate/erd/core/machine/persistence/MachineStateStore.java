package dev.kate.erd.core.machine.persistence;

import dev.kate.erd.core.machine.MachineSnapshot;
import dev.kate.erd.core.model.ChunkKey;
import dev.kate.erd.core.model.MachineId;

import java.util.List;
import java.util.Optional;

/**
 * Interface for persisting machine state.
 *
 * <p>This interface defines the contract for machine persistence with full support
 * for cross-chunk machines and components. Implementations handle the actual
 * storage mechanism (JSON files, database, etc.).
 *
 * <p>Key features:
 * <ul>
 *   <li>Cross-chunk awareness — machines spanning multiple chunks are tracked</li>
 *   <li>Component support — components are persisted with their parent</li>
 *   <li>Chunk-based loading — only load machines when their chunks are active</li>
 *   <li>Dirty tracking — minimize unnecessary disk writes</li>
 * </ul>
 *
 * <p>The store uses a dual indexing strategy:
 * <ol>
 *   <li>Primary storage: Machine data is stored in the anchor chunk's file</li>
 *   <li>Cross-chunk index: A separate index maps non-anchor chunks to machine IDs</li>
 * </ol>
 *
 * <p>Example usage:
 * <pre>{@code
 * // Save a machine
 * MachineSnapshot snapshot = createSnapshot(machine);
 * store.saveMachine(snapshot);
 *
 * // Load machines when chunk loads
 * List<MachineSnapshot> machines = store.loadMachinesForChunk(chunkKey);
 * for (MachineSnapshot snapshot : machines) {
 *     restoreMachine(snapshot);
 * }
 *
 * // Query cross-chunk machines
 * List<MachineSnapshot> spanning = store.loadMachinesSpanningChunk(chunkKey);
 * }</pre>
 *
 * <p>Thread-safety: Implementations should be thread-safe for read operations.
 * Write operations should be serialized or use proper synchronization.
 *
 * @see MachineSnapshot
 */
public interface MachineStateStore {

    /**
     * Saves a machine snapshot.
     *
     * <p>If the machine already exists, it is overwritten.
     * The snapshot's spannedChunks are used to update the cross-chunk index.
     *
     * @param snapshot the machine snapshot to save
     */
    void saveMachine(MachineSnapshot snapshot);

    /**
     * Loads a machine by its ID.
     *
     * @param id the machine ID
     * @return the snapshot, or empty if not found
     */
    Optional<MachineSnapshot> loadMachine(MachineId id);

    /**
     * Deletes a machine by its ID.
     *
     * <p>Also removes the machine from the cross-chunk index.
     *
     * @param id the machine ID
     */
    void deleteMachine(MachineId id);

    /**
     * Loads all machines anchored in a specific chunk.
     *
     * <p>This returns machines whose anchor position is in the given chunk.
     * Use {@link #loadMachinesSpanningChunk} to also get machines that
     * extend into this chunk from other anchor chunks.
     *
     * @param chunk the chunk key
     * @return list of machine snapshots anchored in this chunk
     */
    List<MachineSnapshot> loadMachinesInChunk(ChunkKey chunk);

    /**
     * Loads all machines that span into a chunk but are anchored elsewhere.
     *
     * <p>This is used when a chunk loads to restore machines that extend
     * into it from neighboring chunks.
     *
     * @param chunk the chunk key
     * @return list of machine snapshots spanning this chunk (excluding anchored)
     */
    List<MachineSnapshot> loadMachinesSpanningChunk(ChunkKey chunk);

    /**
     * Loads all machines that have any blocks in a chunk.
     *
     * <p>Convenience method combining anchored and spanning machines.
     *
     * @param chunk the chunk key
     * @return all machines with blocks in this chunk
     */
    default List<MachineSnapshot> loadMachinesForChunk(ChunkKey chunk) {
        List<MachineSnapshot> anchored = loadMachinesInChunk(chunk);
        List<MachineSnapshot> spanning = loadMachinesSpanningChunk(chunk);

        if (spanning.isEmpty()) return anchored;
        if (anchored.isEmpty()) return spanning;

        var combined = new java.util.ArrayList<>(anchored);
        combined.addAll(spanning);
        return combined;
    }

    /**
     * Marks a machine as dirty (needs saving).
     *
     * <p>The actual save may be deferred until {@link #flush()}.
     *
     * @param id the machine ID
     */
    void markDirty(MachineId id);

    /**
     * Flushes all pending writes to storage.
     */
    void flush();

    /**
     * Called when a chunk unloads.
     *
     * <p>Saves any dirty machines in this chunk and clears cache.
     *
     * @param chunk the unloading chunk
     */
    void onChunkUnload(ChunkKey chunk);

    /**
     * Clears all cached data without saving.
     *
     * <p>Use with caution — unsaved changes will be lost.
     */
    void clearCache();

    /**
     * Clears all persisted data.
     *
     * <p>Use with extreme caution — all machine data will be deleted.
     */
    void clearAll();
}

