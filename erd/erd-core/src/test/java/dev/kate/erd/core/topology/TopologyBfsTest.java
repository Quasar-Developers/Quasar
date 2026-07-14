package dev.kate.erd.core.topology;

import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.NetworkId;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyBfsTest {

    private static final UUID WORLD_ID = UUID.randomUUID();

    private BlockPos pos(int x, int y, int z) {
        return new BlockPos(WORLD_ID, x, y, z);
    }

    @Test
    void findConnectedComponent_singleBlock() {
        BlockPos start = pos(0, 0, 0);
        Set<BlockPos> segments = Set.of(start);

        Set<BlockPos> component = TopologyBfs.findConnectedComponent(start, segments);

        assertThat(component).containsExactly(start);
    }

    @Test
    void findConnectedComponent_linearChain() {
        // 0,0,0 -> 1,0,0 -> 2,0,0
        Set<BlockPos> segments = Set.of(pos(0, 0, 0), pos(1, 0, 0), pos(2, 0, 0));

        Set<BlockPos> component = TopologyBfs.findConnectedComponent(pos(0, 0, 0), segments);

        assertThat(component).hasSize(3);
        assertThat(component).containsAll(segments);
    }

    @Test
    void findConnectedComponent_disjointSets() {
        // Set 1: 0,0,0 -> 1,0,0
        // Set 2: 5,0,0 -> 6,0,0
        Set<BlockPos> segments = Set.of(
            pos(0, 0, 0), pos(1, 0, 0),
            pos(5, 0, 0), pos(6, 0, 0)
        );

        Set<BlockPos> component1 = TopologyBfs.findConnectedComponent(pos(0, 0, 0), segments);
        assertThat(component1).containsExactlyInAnyOrder(pos(0, 0, 0), pos(1, 0, 0));

        Set<BlockPos> component2 = TopologyBfs.findConnectedComponent(pos(5, 0, 0), segments);
        assertThat(component2).containsExactlyInAnyOrder(pos(5, 0, 0), pos(6, 0, 0));
    }

    @Test
    void detectSplitOnRemoval_noSplit() {
        // A - B - C. Remove C.
        Set<BlockPos> segments = Set.of(pos(0, 0, 0), pos(1, 0, 0), pos(2, 0, 0));
        
        List<Set<BlockPos>> components = TopologyBfs.detectSplitOnRemoval(pos(2, 0, 0), segments);

        assertThat(components).hasSize(1);
        assertThat(components.get(0)).containsExactlyInAnyOrder(pos(0, 0, 0), pos(1, 0, 0));
    }

    @Test
    void detectSplitOnRemoval_splitOccurs() {
        // A - B - C. Remove B.
        Set<BlockPos> segments = Set.of(pos(0, 0, 0), pos(1, 0, 0), pos(2, 0, 0));
        
        List<Set<BlockPos>> components = TopologyBfs.detectSplitOnRemoval(pos(1, 0, 0), segments);

        assertThat(components).hasSize(2);
        // One component has A, one has C
        assertThat(components).anySatisfy(c -> assertThat(c).containsExactly(pos(0, 0, 0)));
        assertThat(components).anySatisfy(c -> assertThat(c).containsExactly(pos(2, 0, 0)));
    }

    @Test
    void findAdjacentNetworks_findsNeighbors() {
        Map<BlockPos, NetworkId> map = new HashMap<>();
        NetworkId net1 = NetworkId.create();
        NetworkId net2 = NetworkId.create();

        map.put(pos(1, 0, 0), net1);
        map.put(pos(-1, 0, 0), net2);

        Set<NetworkId> adjacent = TopologyBfs.findAdjacentNetworks(pos(0, 0, 0), map);

        assertThat(adjacent).containsExactlyInAnyOrder(net1, net2);
    }

    @Test
    void detectSplitOnRemoval_noSplitWhenAlternatePathExists() {
        // Create a square: A - B
        //                  |   |
        //                  D - C
        // Remove B, but A can still reach C via D
        Set<BlockPos> segments = Set.of(
            pos(0, 0, 0), // A
            pos(1, 0, 0), // B
            pos(1, 0, 1), // C
            pos(0, 0, 1)  // D
        );

        List<Set<BlockPos>> components = TopologyBfs.detectSplitOnRemoval(pos(1, 0, 0), segments);

        // Should still be one component (fast-path optimization should detect this)
        assertThat(components).hasSize(1);
        assertThat(components.get(0)).containsExactlyInAnyOrder(
            pos(0, 0, 0),
            pos(1, 0, 1),
            pos(0, 0, 1)
        );
    }

    @Test
    void detectSplitOnRemoval_splitWhenNoAlternatePath() {
        // Create: A - B - C - D
        // Remove C, which splits into [A,B] and [D]
        Set<BlockPos> segments = Set.of(
            pos(0, 0, 0), // A
            pos(1, 0, 0), // B
            pos(2, 0, 0), // C
            pos(3, 0, 0)  // D
        );

        List<Set<BlockPos>> components = TopologyBfs.detectSplitOnRemoval(pos(2, 0, 0), segments);

        assertThat(components).hasSize(2);
        assertThat(components).anySatisfy(c -> 
            assertThat(c).containsExactlyInAnyOrder(pos(0, 0, 0), pos(1, 0, 0)));
        assertThat(components).anySatisfy(c -> 
            assertThat(c).containsExactly(pos(3, 0, 0)));
    }

    @Test
    void detectSplitOnRemoval_singleNeighborNoSplit() {
        // Remove a leaf node with only one neighbor
        Set<BlockPos> segments = Set.of(
            pos(0, 0, 0),
            pos(1, 0, 0)
        );

        List<Set<BlockPos>> components = TopologyBfs.detectSplitOnRemoval(pos(1, 0, 0), segments);

        // Fast-path: single neighbor means no split possible
        assertThat(components).hasSize(1);
        assertThat(components.get(0)).containsExactly(pos(0, 0, 0));
    }

    @Test
    void detectSplitOnRemoval_noNeighborsEmptyResult() {
        // Remove the only segment
        Set<BlockPos> segments = Set.of(pos(0, 0, 0));

        List<Set<BlockPos>> components = TopologyBfs.detectSplitOnRemoval(pos(0, 0, 0), segments);

        assertThat(components).isEmpty();
    }

    @Test
    void detectSplitOnRemoval_threeWaySplit() {
        // Create a T-junction: 
        //     A
        //     |
        // D - B - C
        // Remove B, which splits into [A], [C], [D]
        Set<BlockPos> segments = Set.of(
            pos(0, 1, 0), // A (above)
            pos(0, 0, 0), // B (center)
            pos(1, 0, 0), // C (right)
            pos(-1, 0, 0) // D (left)
        );

        List<Set<BlockPos>> components = TopologyBfs.detectSplitOnRemoval(pos(0, 0, 0), segments);

        assertThat(components).hasSize(3);
        assertThat(components).anySatisfy(c -> assertThat(c).containsExactly(pos(0, 1, 0)));
        assertThat(components).anySatisfy(c -> assertThat(c).containsExactly(pos(1, 0, 0)));
        assertThat(components).anySatisfy(c -> assertThat(c).containsExactly(pos(-1, 0, 0)));
    }
}
