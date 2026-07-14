package dev.kate.erd.bukkit;

import dev.kate.erd.bukkit.adapter.AsyncMachineValidator;
import dev.kate.erd.bukkit.adapter.BlockEventListener;
import dev.kate.erd.bukkit.adapter.ChunkEventListener;
import dev.kate.erd.bukkit.adapter.MachineDetector;
import dev.kate.erd.bukkit.adapter.NetworkResourceTransfer;
import dev.kate.erd.bukkit.adapter.WorldSnapshotBuilder;
import dev.kate.erd.bukkit.addon.AddonManager;
import dev.kate.erd.bukkit.addon.BukkitAddonContext;
import dev.kate.erd.bukkit.persistence.ChunkedMachineStateStore;
import dev.kate.erd.bukkit.persistence.ChunkedNetworkStateStore;
import dev.kate.erd.bukkit.visual.SegmentFacingManager;
import dev.kate.erd.bukkit.ui.MainframeCommand;
import dev.kate.erd.core.addon.AddonContext;
import dev.kate.erd.core.dataplane.DataControlPlane;
import dev.kate.erd.core.engine.NetworkEngine;
import dev.kate.erd.core.machine.InstanceManager;
import dev.kate.erd.core.machine.MachineInstance;
import dev.kate.erd.core.machine.MachineStateful;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.util.Clock;
import dev.kate.erd.core.util.ErdLogger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Main plugin class for ERD v2.
 *
 * <p>This class bootstraps the ERD system, creating the core engine components
 * and registering Bukkit event listeners and commands.
 *
 * <p>Thread-safety: Plugin lifecycle methods are called on the main thread.
 */
public final class ErdPlugin extends JavaPlugin {

    private ErdLogger logger;
    private NetworkEngine networkEngine;
    private InstanceManager instanceManager;
    private DataControlPlane controlPlane;
    private WorldSnapshotBuilder snapshotBuilder;
    private SegmentFacingManager facingManager;
    private MachineDetector machineDetector;
    private NetworkResourceTransfer resourceTransfer;
    private AddonManager addonManager;
    private AsyncMachineValidator asyncValidator;

    private BukkitTask mainTickTask;

    private ChunkedNetworkStateStore networkStateStore;
    private ChunkedMachineStateStore machineStateStore;

    @Override
    public void onEnable() {
        // Initialize logger adapter
        logger = new BukkitLoggerAdapter(getLogger());
        Clock clock = Clock.system();

        logger.info("Initializing ERD v2...");

        // Create core components
        networkEngine = new NetworkEngine(logger, clock);
        instanceManager = new InstanceManager(logger, clock);
        controlPlane = new DataControlPlane(logger, clock, networkEngine);

        // Network persistence (chunk-based for scalability)
        networkStateStore = new ChunkedNetworkStateStore(getDataFolder().toPath(), logger);
        // Networks are loaded on-demand per chunk, not at startup

        // Create Bukkit adapters
        snapshotBuilder = new WorldSnapshotBuilder();
        facingManager = new SegmentFacingManager(this, networkEngine, logger);
        machineDetector = new MachineDetector(logger, instanceManager, snapshotBuilder);
        resourceTransfer = new NetworkResourceTransfer(logger, networkEngine, instanceManager);
        
        // Create async validator for faster machine validation
        asyncValidator = new AsyncMachineValidator(logger, this, instanceManager);

        // Machine persistence (chunk-based for scalability)
        machineStateStore = new ChunkedMachineStateStore(getDataFolder().toPath(), logger);

        // Create addon system and load addons
        AddonContext addonContext = new BukkitAddonContext(
            logger, instanceManager, machineDetector, getDataFolder().getAbsolutePath());
        addonManager = new AddonManager(logger, getDataFolder(), addonContext);
        addonManager.loadAddons();

        // Register event listeners
        getServer().getPluginManager().registerEvents(
            new BlockEventListener(this, networkEngine, instanceManager, snapshotBuilder, facingManager, machineDetector, resourceTransfer),
            this
        );
        getServer().getPluginManager().registerEvents(
            new ChunkEventListener(networkEngine, networkStateStore, machineStateStore, machineDetector, instanceManager, logger),
            this
        );

        // Register Breeze Rod debug tool
        getServer().getPluginManager().registerEvents(
            new dev.kate.erd.bukkit.debug.BreezeRodDebugTool(this),
            this
        );

        // Register commands
        var mainframeCommand = new MainframeCommand(controlPlane, instanceManager);
        Objects.requireNonNull(getCommand("mainframe")).setExecutor(mainframeCommand);
        Objects.requireNonNull(getCommand("mainframe")).setTabCompleter(mainframeCommand);


        // Start main tick task
        mainTickTask = getServer().getScheduler().runTaskTimer(this, this::onTick, 1L, 1L);

        // Enable addons after core systems are running
        addonManager.enableAddons();

        // Restore machines from chunks that are currently loaded
        getServer().getScheduler().runTaskLater(this, this::restoreMachinesFromLoadedChunks, 20L);

        logger.info("ERD v2 enabled successfully!");
    }

    @Override
    public void onDisable() {
        logger.info("Disabling ERD v2...");

        // Save all current state to disk before shutdown
        // This creates fresh machine snapshots and exports current engine state for networks,
        // rather than just flushing the (potentially stale/empty) caches.
        saveLoadedChunks();

        // Disable addons first
        if (addonManager != null) {
            addonManager.disableAddons();
        }

        // Cancel tick task
        if (mainTickTask != null) {
            mainTickTask.cancel();
            mainTickTask = null;
        }


        // Process remaining operations
        if (networkEngine != null) {
            networkEngine.processQueue();
        }

        logger.info("ERD v2 disabled.");
    }

    /**
     * Main tick processing - called every server tick on main thread.
     */
    private void onTick() {
        // Process engine operation queue
        networkEngine.processQueue(100); // Max 100 ops per tick

        // Tick machines and controllers
        instanceManager.tick();

        // Transfer resources via PIPE networks
        resourceTransfer.tick();
        
        // Process async validation every tick (validates N machines per tick)
        if (asyncValidator != null) {
            asyncValidator.tick();
        }

        // Periodic segment facing refresh (every 20 ticks = 1 second)
        if (getServer().getCurrentTick() % 20 == 0) {
            facingManager.refreshAll();
        }

        // Periodically save loaded chunks + prune networks (every 5 seconds)
        if (getServer().getCurrentTick() % 100 == 0) {
            saveLoadedChunks();
            pruneNetworksAgainstWorld();
        }

        // Queue all machines for async validation (every 2 seconds)
        // This ensures all machines are validated within ~2 seconds
        if (getServer().getCurrentTick() % 40 == 0) {
            if (asyncValidator != null) {
                asyncValidator.queueAllMachines();
            }
        }
    }

    /**
     * Saves only the chunks that are currently loaded (machines + networks).
     * Much more efficient than saving everything.
     */
    private void saveLoadedChunks() {
        if (machineStateStore == null || instanceManager == null) return;

        // Group machines by chunk
        Map<dev.kate.erd.core.model.ChunkKey, List<ChunkedMachineStateStore.MachineEntry>> machinesByChunk = new HashMap<>();

        for (MachineInstance machine : instanceManager.allMachines()) {
            try {
                java.util.Map<String, Object> state = java.util.Map.of();
                int stateVersion = 0;
                if (machine instanceof MachineStateful stateful) {
                    state = stateful.saveState();
                }
                var snap = dev.kate.erd.core.machine.MachineSnapshot.builder()
                    .id(machine.id())
                    .typeId(machine.definition().typeId())
                    .anchorPosition(machine.anchorPosition())
                    .occupiedPositions(machine.structure().positions())
                    .stateVersion(stateVersion)
                    .state(state)
                    .build();
                var entry = ChunkedMachineStateStore.MachineEntry.from(machine.anchorPosition(), snap);

                var chunk = machine.anchorPosition().toChunkKey();
                machinesByChunk.computeIfAbsent(chunk, k -> new ArrayList<>()).add(entry);
            } catch (Exception e) {
                logger.error("Failed to save machine %s: %s", machine.id(), e.getMessage());
            }
        }

        machineStateStore.saveAll(machinesByChunk);

        // Export current engine state for networks and save per-chunk
        if (networkStateStore != null && networkEngine != null) {
            for (ConnectionType layer : ConnectionType.values()) {
                var state = networkEngine.exportConnectionState(layer);
                networkStateStore.saveConnectionState(layer, state);
            }
            networkStateStore.flush();
        }
    }

    /**
     * Restores machines from all saved chunks on disk.
     * Called after server start with a delay to let chunks load.
     * 
     * This method now loads machines from ALL saved chunks, not just currently loaded chunks,
     * ensuring that machines are restored even if their chunks aren't loaded yet.
     */
    private void restoreMachinesFromLoadedChunks() {
        if (machineStateStore == null || instanceManager == null) return;

        int restored = 0;
        int skipped = 0;
        
        // Get all saved chunks from disk
        List<dev.kate.erd.core.model.ChunkKey> savedChunks = machineStateStore.getAllSavedChunks();
        logger.info("Found %d chunks with saved machines", savedChunks.size());

        for (var chunkKey : savedChunks) {
            // Find the world by UUID
            World world = Bukkit.getWorld(chunkKey.worldId());
            if (world == null) {
                skipped++;
                continue; // World not loaded, skip
            }

            var entries = machineStateStore.loadChunk(chunkKey);

            for (var entry : entries) {
                try {
                    var anchor = entry.toAnchorPos();

                    // Queue state for restore under the original MachineId
                    instanceManager.restoreFromSnapshots(java.util.List.of(entry.toSnapshot()));

                    // Check if the chunk is loaded before trying to detect
                    if (world.isChunkLoaded(chunkKey.chunkX(), chunkKey.chunkZ())) {
                        // Re-detect at anchor. If valid, the instance will register and restore state.
                        machineDetector.detectAt(world, world.getBlockAt(anchor.x(), anchor.y(), anchor.z()));
                        restored++;
                    } else {
                        // Chunk not loaded yet - machine will be detected when chunk loads
                        logger.debug("Machine %s queued for detection when chunk loads", entry.machineId());
                        restored++;
                    }

                } catch (Exception e) {
                    logger.error("Failed to restore machine %s: %s", entry.machineId(), e.getMessage());
                }
            }
        }

        if (restored > 0) {
            logger.info("Restored %d machines from %d chunks (%d chunks skipped - world not loaded)", 
                restored, savedChunks.size() - skipped, skipped);
        }
    }

    /**
     * Removes persisted/loaded segments from the engine if the physical block no longer exists.
     *
     * <p>This protects against "ghost" networks after restarts when the world changed.
     * We only validate positions in currently-loaded chunks to avoid force-loading.
     */
    private void pruneNetworksAgainstWorld() {
        if (networkEngine == null) return;

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                // For each type, check positions in this loaded chunk
                for (ConnectionType layer : ConnectionType.values()) {
                    // We don't have a fast chunk index exposed publicly; use export snapshot and filter.
                    // This is called infrequently (5s) and only over loaded chunks.
                    var state = networkEngine.exportConnectionState(layer);
                    for (var net : state.networks()) {
                        for (BlockPos pos : net.segmentPositions()) {
                            if (!pos.worldId().equals(world.getUID())) continue;
                            var ck = pos.toChunkKey();
                            if (ck.chunkX() != chunk.getX() || ck.chunkZ() != chunk.getZ()) continue;

                            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
                            if (block.getType() == org.bukkit.Material.AIR) {
                                // Physical segment is gone, remove from engine
                                networkEngine.removeSegment(layer, pos);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * @return the network engine
     */
    public NetworkEngine getNetworkEngine() {
        return networkEngine;
    }

    /**
     * @return the instance manager
     */
    public InstanceManager getInstanceManager() {
        return instanceManager;
    }


    /**
     * @return the network resource transfer handler
     */
    public NetworkResourceTransfer getResourceTransfer() {
        return resourceTransfer;
    }
}
