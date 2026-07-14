package dev.kate.erd.bukkit.adapter;

import dev.kate.erd.bukkit.ErdPlugin;
import dev.kate.erd.bukkit.visual.SegmentFacingManager;
import dev.kate.erd.core.engine.NetworkEngine;
import dev.kate.erd.core.machine.InstanceManager;
import dev.kate.erd.core.machine.resource.PipeNetworkState;
import dev.kate.erd.core.machine.resource.ResourceType;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.NetworkId;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Optional;

public final class BlockEventListener implements Listener {
    private final ErdPlugin plugin;
    private final NetworkEngine engine;
    private final InstanceManager instanceManager;
    private final WorldSnapshotBuilder snapshotBuilder;
    private final SegmentFacingManager facingManager;
    private final MachineDetector machineDetector;
    private final NetworkResourceTransfer resourceTransfer;

    public BlockEventListener(
            ErdPlugin plugin,
            NetworkEngine engine,
            InstanceManager instanceManager,
            WorldSnapshotBuilder snapshotBuilder,
            SegmentFacingManager facingManager,
            MachineDetector machineDetector,
            NetworkResourceTransfer resourceTransfer) {
        this.plugin = plugin;
        this.engine = engine;
        this.instanceManager = instanceManager;
        this.snapshotBuilder = snapshotBuilder;
        this.facingManager = facingManager;
        this.machineDetector = machineDetector;
        this.resourceTransfer = resourceTransfer;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Material material = block.getType();
        Optional<ConnectionType> layerOpt = SegmentMaterialResolver.getConnectionType(material);
        if (layerOpt.isPresent()) {
            ConnectionType layer = layerOpt.get();
            BlockPos pos = snapshotBuilder.toBlockPos(block);
            engine.addSegment(layer, pos);
            facingManager.updateFacingsAround(block);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                machineDetector.onBlockPlaced(block.getWorld(), block);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material material = block.getType();
        BlockPos pos = snapshotBuilder.toBlockPos(block);
        Optional<ConnectionType> layerOpt = SegmentMaterialResolver.getConnectionType(material);

        if (layerOpt.isPresent()) {
            ConnectionType layer = layerOpt.get();

            // === Spillage Mechanic ===
            if (layer == ConnectionType.PIPE) {
                handlePipeSpillage(pos, block);
            }

            engine.removeSegment(layer, pos);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                facingManager.updateFacingsAround(block);
            });
        } else {
            var machineOpt = instanceManager.getMachineAt(pos);
            if (machineOpt.isPresent()) {
                plugin.getLogger().info("Machine broken, removing: " + machineOpt.get().definition().displayName());
                instanceManager.removeMachine(machineOpt.get());
            }
            var controllerOpt = instanceManager.getControllerAt(pos);
            if (controllerOpt.isPresent()) {
                instanceManager.removeController(controllerOpt.get());
            }
        }
    }

    /**
     * Checks if the broken pipe contains fluid and spills it if necessary.
     */
    private void handlePipeSpillage(BlockPos pos, Block block) {
        Optional<NetworkId> netIdOpt = engine.getNetworkAt(ConnectionType.PIPE, pos);
        if (netIdOpt.isEmpty()) return;

        NetworkId netId = netIdOpt.get();
        Optional<PipeNetworkState> stateOpt = resourceTransfer.getNetworkState(netId);

        if (stateOpt.isPresent()) {
            PipeNetworkState state = stateOpt.get();
            int stored = state.getStoredAmount();
            int size = state.getNetworkSize();

            if (stored > 0 && size > 0) {
                // Calculate fluid density in this block
                int amountInBlock = stored / size;

                // Threshold: 1000mB (1 Bucket)
                if (amountInBlock >= 1000) {
                    // Determine fluid type
                    ResourceType lockedType = state.getLockedResourceType();
                    if (lockedType == null) return;

                    Material fluidBlock = null;
                    if (ResourceType.WATER.equals(lockedType)) {
                        fluidBlock = Material.WATER;
                    } else if (ResourceType.LAVA.equals(lockedType)) {
                        fluidBlock = Material.LAVA;
                    }

                    if (fluidBlock != null) {
                        // Schedule fluid placement after break event
                        Material finalFluidBlock = fluidBlock;
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            block.setType(finalFluidBlock, true);
                        });

                        // Remove spilled amount from network
                        state.removeFromBuffer(amountInBlock);
                    }
                }
            }
        }
    }
}
