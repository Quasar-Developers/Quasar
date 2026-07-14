package dev.kate.erd.bukkit.adapter;

import dev.kate.erd.core.machine.StructureSnapshot;
import dev.kate.erd.core.model.BlockPos;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds StructureSnapshot instances from Bukkit world state.
 *
 * <p>This class captures block data from the world on the main thread
 * and creates immutable snapshots that can be safely passed to the core
 * for validation and detection.
 *
 * <p>Thread-safety: Methods must be called from the main server thread.
 */
public final class WorldSnapshotBuilder {

    /**
     * Creates a snapshot of a region around an origin position.
     *
     * @param world the Bukkit world
     * @param originX origin X coordinate
     * @param originY origin Y coordinate
     * @param originZ origin Z coordinate
     * @param radius the radius to scan in all directions
     * @return the structure snapshot
     */
    public StructureSnapshot buildSnapshot(
            World world,
            int originX, int originY, int originZ,
            int radius) {
        Objects.requireNonNull(world, "world must not be null");

        UUID worldId = world.getUID();
        BlockPos origin = new BlockPos(worldId, originX, originY, originZ);

        Map<BlockPos, StructureSnapshot.BlockData> blocks = new HashMap<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = originX + dx;
                    int y = originY + dy;
                    int z = originZ + dz;

                    Block block = world.getBlockAt(x, y, z);
                    BlockPos pos = new BlockPos(worldId, x, y, z);

                    StructureSnapshot.BlockData data = createBlockData(block);
                    blocks.put(pos, data);
                }
            }
        }

        return new StructureSnapshot(blocks, origin);
    }

    /**
     * Creates a snapshot of a specific set of positions.
     *
     * @param world the Bukkit world
     * @param origin the origin position
     * @param relativePositions positions relative to origin to include
     * @return the structure snapshot
     */
    public StructureSnapshot buildSnapshot(
            World world,
            BlockPos origin,
            Iterable<BlockPos> relativePositions) {
        Objects.requireNonNull(world, "world must not be null");
        Objects.requireNonNull(origin, "origin must not be null");

        UUID worldId = world.getUID();
        Map<BlockPos, StructureSnapshot.BlockData> blocks = new HashMap<>();

        for (BlockPos relPos : relativePositions) {
            Block block = world.getBlockAt(relPos.x(), relPos.y(), relPos.z());
            StructureSnapshot.BlockData data = createBlockData(block);
            blocks.put(relPos, data);
        }

        // Also include origin
        Block originBlock = world.getBlockAt(origin.x(), origin.y(), origin.z());
        blocks.put(origin, createBlockData(originBlock));

        return new StructureSnapshot(blocks, origin);
    }

    /**
     * Creates a snapshot for a single block.
     *
     * @param world the Bukkit world
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @return the structure snapshot
     */
    public StructureSnapshot buildSingleBlockSnapshot(World world, int x, int y, int z) {
        return buildSnapshot(world, x, y, z, 0);
    }

    /**
     * Converts a Bukkit block position to a core BlockPos.
     *
     * @param world the world
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return the core BlockPos
     */
    public BlockPos toBlockPos(World world, int x, int y, int z) {
        return new BlockPos(world.getUID(), x, y, z);
    }

    /**
     * Converts a Bukkit Block to a core BlockPos.
     *
     * @param block the Bukkit block
     * @return the core BlockPos
     */
    public BlockPos toBlockPos(Block block) {
        return new BlockPos(
            block.getWorld().getUID(),
            block.getX(),
            block.getY(),
            block.getZ()
        );
    }

    private StructureSnapshot.BlockData createBlockData(Block block) {
        String typeKey = block.getType().getKey().toString();

        // Extract block state properties if available
        Map<String, String> properties = new HashMap<>();

        var blockData = block.getBlockData();
        String dataString = blockData.getAsString();

        // Parse properties from the data string (e.g., "minecraft:oak_stairs[facing=north,half=bottom]")
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

        return new StructureSnapshot.BlockData(typeKey, properties);
    }
}
