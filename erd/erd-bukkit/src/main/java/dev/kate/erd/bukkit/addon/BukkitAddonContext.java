package dev.kate.erd.bukkit.addon;

import dev.kate.erd.bukkit.adapter.MachineDetector;
import dev.kate.erd.core.addon.AddonContext;
import dev.kate.erd.core.controller.ControllerDefinition;
import dev.kate.erd.core.machine.InstanceManager;
import dev.kate.erd.core.machine.MachineDefinition;
import dev.kate.erd.core.machine.MachineSnapshot;
import dev.kate.erd.core.machine.resource.ResourceType;
import dev.kate.erd.core.util.ErdLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Bukkit implementation of AddonContext.
 */
public class BukkitAddonContext implements AddonContext {
    private final ErdLogger logger;
    private final InstanceManager instanceManager;
    private final MachineDetector machineDetector;
    private final String dataFolder;
    private final List<MachineSnapshot> pendingRedetection = new ArrayList<>();

    public BukkitAddonContext(ErdLogger logger, InstanceManager instanceManager,
                             MachineDetector machineDetector, String dataFolder) {
        this.logger = logger;
        this.instanceManager = instanceManager;
        this.machineDetector = machineDetector;
        this.dataFolder = dataFolder;
    }
    @Override
    public void registerMachine(MachineDefinition<?> definition) {
        instanceManager.registerMachineDefinition(definition);
        machineDetector.registerMachineDefinition(definition);
        logger.info("Addon registered machine: %s", definition.typeId());
    }
    @Override
    public void registerController(ControllerDefinition<?> definition) {
        instanceManager.registerControllerDefinition(definition);
        logger.info("Addon registered controller: %s", definition.typeId());
    }

    @Override
    public ResourceType registerResource(String id, String symbol, String displayName, boolean isGas, boolean isLiquid) {
        try {
            ResourceType type = ResourceType.register(id, symbol, displayName, isGas, isLiquid);
            logger.info("Addon registered resource: %s (%s)", displayName, id);
            return type;
        } catch (IllegalArgumentException e) {
            logger.warn("Addon tried to register duplicate resource: %s", id);
            return ResourceType.get(id).orElseThrow();
        }
    }

    @Override
    public List<MachineSnapshot> snapshotMachines() {
        return instanceManager.snapshotAllMachines();
    }

    @Override
    public void restoreMachines(List<MachineSnapshot> snapshots) {
        instanceManager.restoreFromSnapshots(snapshots);
        // Store for re-detection
        pendingRedetection.clear();
        pendingRedetection.addAll(snapshots);
    }

    @Override
    public void redetectMachinesFromMarkers() {
        if (pendingRedetection.isEmpty()) {
            logger.info("No machines to re-detect after reload");
            return;
        }

        int totalMachines = pendingRedetection.size();
        logger.info("Re-detecting %d machines after reload...", totalMachines);

        int redetected = 0;
        for (var snapshot : new ArrayList<>(pendingRedetection)) {
            // Get the world and block at anchor position
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(snapshot.anchorPosition().worldId());
            if (world == null) {
                logger.warn("World not loaded for machine at %s", snapshot.anchorPosition());
                continue;
            }

            org.bukkit.block.Block block = world.getBlockAt(
                snapshot.anchorPosition().x(),
                snapshot.anchorPosition().y(),
                snapshot.anchorPosition().z()
            );

            // Try to detect machine at this location
            var result = machineDetector.detectAt(world, block);
            if (result.isPresent()) {
                redetected++;
                logger.info("Re-detected machine: %s (id=%s)",
                    result.get().definition().typeId(),
                    result.get().id().toString().substring(0, 8));
            } else {
                logger.warn("Failed to re-detect machine %s at %s (structure may be broken or definition not loaded)",
                    snapshot.typeId(),
                    snapshot.anchorPosition());
            }
        }

        pendingRedetection.clear();
        logger.info("Re-detected %d/%d machines", redetected, totalMachines);
    }

    @Override
    public ErdLogger getLogger() {
        return logger;
    }
    @Override
    public String getPluginDataFolder() {
        return dataFolder;
    }
}
