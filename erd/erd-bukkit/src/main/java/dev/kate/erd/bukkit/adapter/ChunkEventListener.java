package dev.kate.erd.bukkit.adapter;

import dev.kate.erd.bukkit.persistence.ChunkedMachineStateStore;
import dev.kate.erd.bukkit.persistence.ChunkedNetworkStateStore;
import dev.kate.erd.bukkit.persistence.ChunkedNetworkStateStore.SegmentEntry;
import dev.kate.erd.core.engine.NetworkEngine;
import dev.kate.erd.core.machine.InstanceManager;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ChunkKey;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.NetworkId;
import dev.kate.erd.core.model.PipeFamily;
import dev.kate.erd.core.persistence.NetworkStateStore;
import dev.kate.erd.core.util.ErdLogger;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Listens for chunk load/unload events and notifies the network engine.
 *
 * <p>This allows the engine to track which chunks are active for performance
 * optimizations and dormant network handling. It also loads/saves network
 * state from/to persistent storage.
 *
 * <p>Thread-safety: Event handlers are called on the main server thread.
 */
public final class ChunkEventListener implements Listener {

    private final NetworkEngine engine;
    private final ChunkedNetworkStateStore networkStateStore;
    private final ChunkedMachineStateStore machineStateStore;
    private final MachineDetector machineDetector;
    private final InstanceManager instanceManager;
    private final ErdLogger logger;

    /**
     * Creates a new chunk event listener.
     *
     * @param engine the network engine
     * @param networkStateStore the network state store for persistence
     * @param machineStateStore the machine state store for persistence
     * @param machineDetector the machine detector for restoring machines
     * @param instanceManager the instance manager for machine restoration
     * @param logger the logger for error reporting
     */
    public ChunkEventListener(
            NetworkEngine engine, 
            ChunkedNetworkStateStore networkStateStore,
            ChunkedMachineStateStore machineStateStore,
            MachineDetector machineDetector,
            InstanceManager instanceManager,
            ErdLogger logger) {
        this.engine = engine;
        this.networkStateStore = networkStateStore;
        this.machineStateStore = machineStateStore;
        this.machineDetector = machineDetector;
        this.instanceManager = instanceManager;
        this.logger = logger;
    }

    /**
     * Handles chunk load events.
     * Loads persisted network data and restores segments to the engine,
     * preserving the original network IDs.
     * Also loads and restores machines from the chunk.
     *
     * @param event the chunk load event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        ChunkKey key = toChunkKey(chunk);

        // Notify engine about chunk activation
        engine.onChunkLoaded(key);

        // Load and restore networks for each type
        for (ConnectionType layer : ConnectionType.values()) {
            List<SegmentEntry> segments = networkStateStore.loadChunkEntries(key, layer);
            if (segments.isEmpty()) continue;

            // Group segments by network ID to import them together
            Map<NetworkId, Set<BlockPos>> cablesByNetwork = segments.stream()
                .collect(Collectors.groupingBy(
                    SegmentEntry::networkId,
                    Collectors.mapping(SegmentEntry::position, Collectors.toSet())
                ));

            // Import each network, preserving the original ID
            for (var entry : cablesByNetwork.entrySet()) {
                NetworkId networkId = entry.getKey();
                Set<BlockPos> positions = entry.getValue();

                // Check if network already exists (from a previously loaded chunk)
                if (!engine.getNetworkSegments(layer, networkId).isEmpty()) {
                    // Network already loaded - merge positions via addSegment
                    for (BlockPos pos : positions) {
                        if (engine.getNetworkAt(layer, pos).isEmpty()) {
                            engine.addSegment(layer, pos);
                        }
                    }
                } else {
                    // New network - import with original ID preserved
                    // Use stored metadata for PipeFamily and createdAt
                    PipeFamily pipeFamily = layer == ConnectionType.PIPE
                        ? networkStateStore.getPipeFamily(networkId)
                        : PipeFamily.UNASSIGNED;
                    long createdAt = networkStateStore.getCreatedAt(networkId);
                    if (createdAt == 0L) createdAt = System.currentTimeMillis();

                    var networkData = new NetworkStateStore.NetworkData(
                        networkId,
                        positions,
                        createdAt,
                        pipeFamily
                    );
                    engine.importConnectionState(new NetworkStateStore.ConnectionStateData(layer, List.of(networkData), 0));
                }
            }
        }
        
        // Load and restore machines from this chunk
        if (machineStateStore != null && machineDetector != null && instanceManager != null) {
            var entries = machineStateStore.loadChunk(key);
            for (var entry : entries) {
                try {
                    var anchor = entry.toAnchorPos();
                    
                    // Check if machine is already registered (may have been restored at startup)
                    // Note: This iterates through all machines, which is acceptable since chunk loading
                    // is a relatively rare event. For more frequent checks, consider adding an index.
                    if (instanceManager.allMachines().stream()
                            .noneMatch(m -> m.anchorPosition().equals(anchor))) {
                        
                        // Queue state for restore under the original MachineId
                        instanceManager.restoreFromSnapshots(List.of(entry.toSnapshot()));
                        
                        // Re-detect at anchor. If valid, the instance will register and restore state.
                        machineDetector.detectAt(
                            chunk.getWorld(), 
                            chunk.getWorld().getBlockAt(anchor.x(), anchor.y(), anchor.z())
                        );
                    }
                } catch (Exception e) {
                    logger.error("Failed to restore machine %s in chunk %s: %s", 
                        entry.machineId(), key, e.getMessage());
                }
            }
        }
    }

    /**
     * Handles chunk unload events.
     * Saves network data from this chunk to persistent storage,
     * preserving network IDs.
     *
     * @param event the chunk unload event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        ChunkKey key = toChunkKey(chunk);

        // Save networks for each type before chunk unloads
        for (ConnectionType layer : ConnectionType.values()) {
            // Get all segment positions in this chunk from the engine, with their network IDs
            List<SegmentEntry> entries = new ArrayList<>();
            Set<NetworkId> networksInChunk = new HashSet<>();

            for (NetworkId netId : engine.getAllNetworks(layer)) {
                for (BlockPos pos : engine.getNetworkSegments(layer, netId)) {
                    if (pos.toChunkKey().equals(key)) {
                        entries.add(new SegmentEntry(netId, pos, layer));
                        networksInChunk.add(netId);
                    }
                }
            }

            networkStateStore.saveChunkEntries(key, layer, entries);

            // Update network metadata for PIPE networks
            if (layer == ConnectionType.PIPE) {
                for (NetworkId netId : networksInChunk) {
                    PipeFamily family = engine.getPipeFamily(netId);
                    networkStateStore.updateNetworkMeta(netId, family, System.currentTimeMillis());
                }
            }
        }

        // Notify engine about chunk deactivation
        engine.onChunkUnloaded(key);
    }

    private ChunkKey toChunkKey(Chunk chunk) {
        return new ChunkKey(
            chunk.getWorld().getUID(),
            chunk.getX(),
            chunk.getZ()
        );
    }
}
