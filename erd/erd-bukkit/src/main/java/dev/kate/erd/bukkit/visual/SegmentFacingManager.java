package dev.kate.erd.bukkit.visual;

import dev.kate.erd.bukkit.adapter.SegmentMaterialResolver;
import dev.kate.erd.core.engine.NetworkEngine;
import dev.kate.erd.core.model.Direction;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.util.ErdLogger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Manages segment block facings for visual connectivity.
 *
 * <p>Mushroom blocks use the MultipleFacing block data interface.
 * This manager updates the facing directions so segments visually
 * connect to adjacent segments and machine/controller ports.
 *
 * <p>Thread-safety: All methods must be called on the main server thread.
 */
public final class SegmentFacingManager {

    private final Plugin plugin;
    private final NetworkEngine engine;
    private final ErdLogger logger;

    // Mapping from core Direction to Bukkit BlockFace
    private static final Map<Direction, BlockFace> DIRECTION_TO_FACE = Map.of(
        Direction.NORTH, BlockFace.NORTH,
        Direction.SOUTH, BlockFace.SOUTH,
        Direction.EAST, BlockFace.EAST,
        Direction.WEST, BlockFace.WEST,
        Direction.UP, BlockFace.UP,
        Direction.DOWN, BlockFace.DOWN
    );

    /**
     * Creates a new segment facing manager.
     *
     * @param plugin the plugin instance
     * @param engine the network engine
     * @param logger the logger
     */
    public SegmentFacingManager(Plugin plugin, NetworkEngine engine, ErdLogger logger) {
        this.plugin = plugin;
        this.engine = engine;
        this.logger = logger;
    }

    /**
     * Updates the facing for a segment block and its neighbors.
     *
     * @param centerBlock the center block
     */
    public void updateFacingsAround(Block centerBlock) {
        // Update center block
        updateFacing(centerBlock);

        // Update all neighbors
        for (BlockFace face : BlockFace.values()) {
            if (face.isCartesian()) {
                Block neighbor = centerBlock.getRelative(face);
                updateFacing(neighbor);
            }
        }
    }

    /**
     * Updates the facing for a single segment block.
     *
     * @param block the block to update
     */
    public void updateFacing(Block block) {
        Material material = block.getType();

        // Only process segment materials
        Optional<ConnectionType> layerOpt = SegmentMaterialResolver.getConnectionType(material);
        if (layerOpt.isEmpty()) {
            return;
        }

        ConnectionType layer = layerOpt.get();

        if (!(block.getBlockData() instanceof MultipleFacing facing)) {
            return;
        }

        // Determine which faces should be connected
        Set<BlockFace> connectedFaces = new HashSet<>();

        for (BlockFace face : facing.getAllowedFaces()) {
            Block neighbor = block.getRelative(face);

            if (shouldConnect(layer, block, neighbor)) {
                connectedFaces.add(face);
            }
        }

        // Apply facing changes
        boolean changed = false;
        for (BlockFace face : facing.getAllowedFaces()) {
            boolean shouldBeSet = connectedFaces.contains(face);
            if (facing.hasFace(face) != shouldBeSet) {
                facing.setFace(face, shouldBeSet);
                changed = true;
            }
        }

        if (changed) {
            block.setBlockData(facing, false);
        }
    }

    /**
     * Refreshes facings for all loaded segment blocks.
     * Called periodically for self-healing.
     */
    public void refreshAll() {
        for (World world : Bukkit.getWorlds()) {
            // Only process loaded chunks
            for (var chunk : world.getLoadedChunks()) {
                refreshChunk(chunk);
            }
        }
    }

    /**
     * Refreshes facings for segments in a specific chunk.
     *
     * @param chunk the chunk
     */
    public void refreshChunk(org.bukkit.Chunk chunk) {
        // Get all segment positions in this chunk from the engine
        UUID worldId = chunk.getWorld().getUID();

        for (ConnectionType layer : ConnectionType.values()) {
            var chunkKey = new dev.kate.erd.core.model.ChunkKey(worldId, chunk.getX(), chunk.getZ());
            // Note: We'd need to add a method to get positions in chunk from engine
            // For now, this is a simplified implementation
        }
    }

    /**
     * Determines if a segment should visually connect to a neighbor.
     *
     * @param layer the segment's type
     * @param segment the segment block
     * @param neighbor the neighbor block
     * @return true if they should connect
     */
    private boolean shouldConnect(ConnectionType layer, Block segment, Block neighbor) {
        Material neighborMat = neighbor.getType();

        // Connect to same-type segments
        if (SegmentMaterialResolver.isSegmentOfLayer(neighborMat, layer)) {
            return true;
        }

        // Connect to machine/controller port blocks
        // This would check the instance manager for endpoints at this position
        // For now, we connect to any non-air solid block as a simple heuristic
        if (!neighborMat.isAir() && neighborMat.isSolid() && !SegmentMaterialResolver.isSegment(neighborMat)) {
            // Could be a machine/port block - connect for visual purposes
            return true;
        }

        return false;
    }
}
