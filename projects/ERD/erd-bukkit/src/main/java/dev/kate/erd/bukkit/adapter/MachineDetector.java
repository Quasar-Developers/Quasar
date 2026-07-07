package dev.kate.erd.bukkit.adapter;

import dev.kate.erd.core.machine.*;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.util.ErdLogger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;

/**
 * Detects and registers machines when blocks are placed.
 *
 * <p>Scans around placed blocks to check if they complete a valid machine structure.
 */
public class MachineDetector {

    private final ErdLogger logger;
    private final InstanceManager instanceManager;
    private final WorldSnapshotBuilder snapshotBuilder;

    // Registered machine definitions
    private final List<MachineDefinition<?>> machineDefinitions = new ArrayList<>();

    // Map of controller block keys to their definitions
    private final Map<String, MachineDefinition<?>> controllerBlockMap = new HashMap<>();

    public MachineDetector(ErdLogger logger, InstanceManager instanceManager, WorldSnapshotBuilder snapshotBuilder) {
        this.logger = logger;
        this.instanceManager = instanceManager;
        this.snapshotBuilder = snapshotBuilder;

        // Machine definitions will be registered by addons
    }

    /**
     * Registers a machine definition for detection.
     */
    public void registerMachineDefinition(MachineDefinition<?> definition) {
        machineDefinitions.add(definition);
        controllerBlockMap.put(definition.controllerBlockKey(), definition);
        logger.info("Registered machine definition: " + definition.typeId());
    }

    /**
     * Called when a block is placed. Checks if it completes a machine structure.
     *
     * @param world the world
     * @param block the placed block
     * @return the created machine instance, or empty if no machine was formed
     */
    public Optional<MachineInstance> onBlockPlaced(World world, Block block) {
        Material material = block.getType();
        String blockKey = material.getKey().toString();

        // Check if this block is a controller block for any machine
        MachineDefinition<?> definition = controllerBlockMap.get(blockKey);
        if (definition != null) {
            return tryDetectMachine(world, block, definition);
        }

        // Also check if placing this block might complete a nearby machine
        // by scanning around for controller blocks
        return scanNearbyForMachines(world, block);
    }

    /**
     * Try to detect a machine at the given controller block location.
     */
    private Optional<MachineInstance> tryDetectMachine(World world, Block controllerBlock, MachineDefinition<?> definition) {
        BlockPos origin = snapshotBuilder.toBlockPos(controllerBlock);

        // Check if already registered
        if (instanceManager.getMachineAt(origin).isPresent()) {
            return Optional.empty();
        }

        // Build snapshot for detection
        var bounds = definition.detectionBounds();
        StructureSnapshot snapshot = snapshotBuilder.buildSnapshot(
            world,
            origin.x(), origin.y(), origin.z(),
            Math.max(Math.abs(bounds.minX()), Math.max(Math.abs(bounds.maxX()),
                Math.max(Math.abs(bounds.minY()), Math.max(Math.abs(bounds.maxY()),
                    Math.max(Math.abs(bounds.minZ()), Math.abs(bounds.maxZ()))))))
        );

        // Validate structure
        var result = definition.validate(snapshot);

        if (result instanceof ValidationResult.Valid valid) {
            // Create and register the machine
            MachineId id = MachineId.create();
            MachineInstance instance = definition.createInstance(id, valid.structure());

            instanceManager.registerMachine(instance);

            logger.info("Machine detected and registered: " + definition.displayName() +
                " at " + origin + " (ID: " + id.id().toString().substring(0, 8) + ")");

            return Optional.of(instance);
        } else if (result instanceof ValidationResult.Invalid invalid) {
            // Structure not valid - this is normal during building
            logger.debug("Machine validation failed at " + origin + ": " + invalid.reason());
        }

        return Optional.empty();
    }

    /**
     * Scan nearby blocks to see if placing this block completed a machine.
     */
    private Optional<MachineInstance> scanNearbyForMachines(World world, Block placedBlock) {
        // Check a small radius for controller blocks
        int radius = 3;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block nearby = world.getBlockAt(
                        placedBlock.getX() + dx,
                        placedBlock.getY() + dy,
                        placedBlock.getZ() + dz
                    );

                    String blockKey = nearby.getType().getKey().toString();
                    MachineDefinition<?> definition = controllerBlockMap.get(blockKey);

                    if (definition != null) {
                        var result = tryDetectMachine(world, nearby, definition);
                        if (result.isPresent()) {
                            return result;
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Force detection at a specific location.
     */
    public Optional<MachineInstance> detectAt(World world, Block block) {
        String blockKey = block.getType().getKey().toString();
        MachineDefinition<?> definition = controllerBlockMap.get(blockKey);

        if (definition != null) {
            return tryDetectMachine(world, block, definition);
        }

        return Optional.empty();
    }

    /**
     * Gets the definition for a block type, if any.
     */
    public Optional<MachineDefinition<?>> getDefinitionForBlock(Material material) {
        String blockKey = material.getKey().toString();
        return Optional.ofNullable(controllerBlockMap.get(blockKey));
    }
}
