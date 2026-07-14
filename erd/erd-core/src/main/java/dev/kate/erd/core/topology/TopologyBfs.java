package dev.kate.erd.core.topology;

import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.Direction;
import dev.kate.erd.core.model.NetworkId;

import java.util.*;

/**
 * Performs breadth-first search operations on network topology graphs.
 *
 * <p>This class provides algorithms for finding connected components,
 * detecting network splits, and computing network membership.
 *
 * <p>All methods in this class operate on immutable snapshots and
 * produce new result objects, making them safe for async execution.
 *
 * <p>Thread-safety: All methods are stateless and thread-safe.
 */
public final class TopologyBfs {

    private TopologyBfs() {
        // Utility class
    }

    /**
     * Finds all positions reachable from a starting position.
     *
     * @param start the starting position
     * @param cablePositions all segment positions in the search space
     * @return an unmodifiable set of all reachable positions including start
     */
    public static Set<BlockPos> findConnectedComponent(
            BlockPos start,
            Set<BlockPos> cablePositions) {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(cablePositions, "segmentPositions must not be null");

        if (!cablePositions.contains(start)) {
            return Set.of();
        }

        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            for (Direction dir : Direction.ALL) {
                BlockPos neighbor = current.adjacent(dir);
                if (cablePositions.contains(neighbor) && visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        return Collections.unmodifiableSet(visited);
    }

    /**
     * Finds all connected components in a set of positions.
     *
     * @param positions the positions to partition into components
     * @return a list of disjoint sets, each representing a connected component
     */
    public static List<Set<BlockPos>> findAllComponents(Set<BlockPos> positions) {
        Objects.requireNonNull(positions, "positions must not be null");

        if (positions.isEmpty()) {
            return List.of();
        }

        Set<BlockPos> remaining = new HashSet<>(positions);
        List<Set<BlockPos>> components = new ArrayList<>();

        while (!remaining.isEmpty()) {
            BlockPos start = remaining.iterator().next();
            Set<BlockPos> component = findConnectedComponent(start, remaining);
            components.add(component);
            remaining.removeAll(component);
        }

        return Collections.unmodifiableList(components);
    }

    /**
     * Detects if removing a segment from a network would split it.
     *
     * <p>Returns the resulting components after removal. If the result
     * has more than one component, a split has occurred.
     *
     * <p>Optimization: First checks if any neighbors of the removed position
     * are directly connected without going through it. If so, no split occurs.
     * This avoids the full O(N) component search in many common cases.
     *
     * @param removedPos the position being removed
     * @param networkCables all segments in the network (including removedPos)
     * @return the components that would result from removal
     */
    public static List<Set<BlockPos>> detectSplitOnRemoval(
            BlockPos removedPos,
            Set<BlockPos> networkCables) {
        Objects.requireNonNull(removedPos, "removedPos must not be null");
        Objects.requireNonNull(networkCables, "networkCables must not be null");

        // Get neighbors that are part of the network
        List<BlockPos> neighbors = new ArrayList<>();
        for (Direction dir : Direction.ALL) {
            BlockPos neighbor = removedPos.adjacent(dir);
            if (networkCables.contains(neighbor)) {
                neighbors.add(neighbor);
            }
        }

        // If 0 or 1 neighbors, no split can occur
        if (neighbors.size() <= 1) {
            // Create set without the removed position
            Set<BlockPos> remaining = new HashSet<>(networkCables);
            remaining.remove(removedPos);
            
            if (remaining.isEmpty()) {
                return List.of();
            }
            
            // Return single component
            return List.of(remaining);
        }

        // Optimization: Check if all neighbors are still connected without going through removedPos
        // Start BFS from first neighbor, see if we can reach all others
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        
        BlockPos start = neighbors.get(0);
        queue.add(start);
        visited.add(start);
        visited.add(removedPos); // Mark as visited to prevent traversing through it

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            for (Direction dir : Direction.ALL) {
                BlockPos neighbor = current.adjacent(dir);
                if (networkCables.contains(neighbor) && visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        // Check if we reached all neighbors
        boolean allNeighborsConnected = true;
        for (BlockPos neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                allNeighborsConnected = false;
                break;
            }
        }

        // If all neighbors still connected, no split occurred
        if (allNeighborsConnected) {
            Set<BlockPos> remaining = new HashSet<>(networkCables);
            remaining.remove(removedPos);
            return List.of(remaining);
        }

        // Split detected - do full component search
        Set<BlockPos> remaining = new HashSet<>(networkCables);
        remaining.remove(removedPos);
        
        if (remaining.isEmpty()) {
            return List.of();
        }

        return findAllComponents(remaining);
    }

    /**
     * Finds all adjacent networks for a new segment position.
     *
     * @param newPos the new segment position
     * @param positionToNetwork mapping of existing positions to their networks
     * @return set of network IDs adjacent to the new position
     */
    public static Set<NetworkId> findAdjacentNetworks(
            BlockPos newPos,
            Map<BlockPos, NetworkId> positionToNetwork) {
        Objects.requireNonNull(newPos, "newPos must not be null");
        Objects.requireNonNull(positionToNetwork, "positionToNetwork must not be null");

        Set<NetworkId> adjacent = new HashSet<>();

        for (Direction dir : Direction.ALL) {
            BlockPos neighbor = newPos.adjacent(dir);
            NetworkId networkId = positionToNetwork.get(neighbor);
            if (networkId != null) {
                adjacent.add(networkId);
            }
        }

        return Collections.unmodifiableSet(adjacent);
    }

    /**
     * Checks if two positions are directly adjacent (differ by 1 in exactly one axis).
     *
     * @param a first position
     * @param b second position
     * @return true if the positions are adjacent
     */
    public static boolean areAdjacent(BlockPos a, BlockPos b) {
        if (a == null || b == null || !a.worldId().equals(b.worldId())) {
            return false;
        }

        int dx = Math.abs(a.x() - b.x());
        int dy = Math.abs(a.y() - b.y());
        int dz = Math.abs(a.z() - b.z());

        return (dx + dy + dz) == 1;
    }
}
