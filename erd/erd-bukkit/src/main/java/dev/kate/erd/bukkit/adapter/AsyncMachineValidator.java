package dev.kate.erd.bukkit.adapter;

import dev.kate.erd.core.machine.InstanceManager;
import dev.kate.erd.core.machine.MachineInstance;
import dev.kate.erd.core.machine.RescanResult;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.util.ErdLogger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Asynchronous machine validator that offloads heavy validation work to async thread.
 *
 * <p>This validator uses ChunkSnapshots to safely access block data from async threads,
 * reducing the load on the main thread and enabling faster validation cycles.
 *
 * <h2>Validation Flow</h2>
 * <ol>
 *   <li>Main thread: Capture chunk snapshots (fast)</li>
 *   <li>Async thread: Build structure snapshots and validate (slow)</li>
 *   <li>Main thread: Process results and remove invalid machines (fast)</li>
 * </ol>
 *
 * <p>Thread-safety: Designed for async operation with proper synchronization.
 */
public class AsyncMachineValidator {

    private final ErdLogger logger;
    private final Plugin plugin;
    private final InstanceManager instanceManager;

    // Queue of machines to validate (thread-safe)
    private final Queue<MachineInstance> validationQueue = new ConcurrentLinkedQueue<>();

    // Queue of validation results from async thread (thread-safe)
    private final Queue<ValidationResult> resultQueue = new ConcurrentLinkedQueue<>();

    // Machines per tick to validate
    private static final int MACHINES_PER_TICK = 10;

    /**
     * Creates an async machine validator.
     *
     * @param logger the logger
     * @param plugin the plugin instance for scheduling
     * @param instanceManager the instance manager
     */
    public AsyncMachineValidator(ErdLogger logger, Plugin plugin, InstanceManager instanceManager) {
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.instanceManager = Objects.requireNonNull(instanceManager, "instanceManager must not be null");
    }

    /**
     * Queues all machines for validation.
     * Called periodically from main thread.
     */
    public void queueAllMachines() {
        validationQueue.clear();
        for (MachineInstance machine : instanceManager.allMachines()) {
            validationQueue.offer(machine);
        }
        logger.debug("Queued %d machines for async validation", validationQueue.size());
    }

    /**
     * Processes validation queue - validates N machines per tick.
     * Called every tick from main thread.
     */
    public void tick() {
        // Process results first
        processResults();

        // Validate next batch of machines
        int validated = 0;
        while (validated < MACHINES_PER_TICK && !validationQueue.isEmpty()) {
            MachineInstance machine = validationQueue.poll();
            if (machine != null) {
                validateMachineAsync(machine);
                validated++;
            }
        }
    }

    /**
     * Validates a machine asynchronously.
     * Called from main thread, offloads work to async thread.
     *
     * <p>Each validation task creates its own {@link AsyncSnapshotBuilder} to avoid
     * race conditions when multiple async tasks run concurrently. A shared builder
     * would have its cache cleared by one task's completion, causing other in-flight
     * tasks to see empty chunks (AIR) and incorrectly invalidate machines.
     */
    private void validateMachineAsync(MachineInstance machine) {
        try {
            // Check if chunk is loaded (main thread only)
            World world = Bukkit.getWorld(machine.anchorPosition().worldId());
            if (world == null) return;

            var chunk = machine.anchorPosition().toChunkKey();
            if (!world.isChunkLoaded(chunk.chunkX(), chunk.chunkZ())) {
                return; // Skip unloaded chunks
            }

            // Capture chunk snapshots on main thread (fast)
            // Use a per-task builder to avoid race conditions between concurrent async tasks
            Set<BlockPos> positions = machine.occupiedPositions();
            AsyncSnapshotBuilder taskBuilder = new AsyncSnapshotBuilder();
            taskBuilder.captureChunks(world, positions);

            // Offload validation to async thread (slow)
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    // Build snapshot from captured chunks (async-safe)
                    var snapshot = taskBuilder.buildSnapshotAsync(
                        machine.anchorPosition(),
                        positions
                    );

                    // Validate structure (async-safe)
                    var result = machine.rescan(snapshot);

                    // Queue result for main thread processing
                    resultQueue.offer(new ValidationResult(machine, result));

                } catch (Exception e) {
                    logger.error("Async validation error for machine %s: %s",
                        machine.id(), e.getMessage());
                }
            });

        } catch (Exception e) {
            logger.error("Error starting async validation for machine %s: %s",
                machine.id(), e.getMessage());
        }
    }

    /**
     * Processes validation results from async thread.
     * Called from main thread.
     */
    private void processResults() {
        int processed = 0;
        int removed = 0;

        while (!resultQueue.isEmpty()) {
            ValidationResult result = resultQueue.poll();
            if (result == null) break;

            try {
                processed++;

                switch (result.rescanResult) {
                    case INVALID -> {
                        // Machine structure is no longer valid, remove it
                        instanceManager.removeMachine(result.machine);
                        removed++;
                        logger.info("Machine %s at %s failed async validation and was removed",
                            result.machine.definition().typeId(),
                            result.machine.anchorPosition());
                    }
                    case RESIZED -> {
                        // Machine structure changed but is still valid
                        logger.debug("Machine %s at %s was resized during async validation",
                            result.machine.definition().typeId(),
                            result.machine.anchorPosition());
                    }
                    case UNCHANGED -> {
                        // Machine is still valid, no action needed
                    }
                }

            } catch (Exception e) {
                logger.error("Error processing validation result: %s", e.getMessage());
            }
        }

        if (removed > 0) {
            logger.info("Async validation removed %d invalid machines (%d results processed)",
                removed, processed);
        }
    }

    /**
     * Gets the number of machines remaining in validation queue.
     *
     * @return queue size
     */
    public int getQueueSize() {
        return validationQueue.size();
    }

    /**
     * Result of an async validation operation.
     */
    private record ValidationResult(MachineInstance machine, RescanResult rescanResult) {}
}
