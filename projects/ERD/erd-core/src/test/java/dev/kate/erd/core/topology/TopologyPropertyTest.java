package dev.kate.erd.core.topology;

import dev.kate.erd.core.model.BlockPos;
import net.jqwik.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for topology graph invariants.
 */
class TopologyPropertyTest {

    private static final UUID WORLD_ID = UUID.randomUUID();

    @Property
    void everyPositionInExactlyOneComponent(@ForAll("cableGraph") Set<BlockPos> segments) {
        List<Set<BlockPos>> components = TopologyBfs.findAllComponents(segments);

        // Verify disjoint
        Set<BlockPos> seen = new HashSet<>();
        for (Set<BlockPos> component : components) {
            for (BlockPos pos : component) {
                assertThat(seen.add(pos))
                    .as("Position %s should not appear in multiple components", pos)
                    .isTrue();
            }
        }

        // Verify union equals original
        assertThat(seen).containsExactlyInAnyOrderElementsOf(segments);
    }

    @Property
    void componentsAreConnected(@ForAll("cableGraph") Set<BlockPos> segments) {
        List<Set<BlockPos>> components = TopologyBfs.findAllComponents(segments);

        for (Set<BlockPos> component : components) {
            if (component.size() <= 1) continue;

            // Pick any start point
            BlockPos start = component.iterator().next();

            // BFS should reach all positions in the component
            Set<BlockPos> reachable = TopologyBfs.findConnectedComponent(start, component);

            assertThat(reachable)
                .as("All positions in a component should be reachable from any start point")
                .containsExactlyInAnyOrderElementsOf(component);
        }
    }

    @Property
    void removePositionReducesTotalByOne(@ForAll("cableGraph") Set<BlockPos> segments) {
        Assume.that(segments.size() > 0);

        // Pick random position to remove
        BlockPos toRemove = segments.iterator().next();

        List<Set<BlockPos>> afterRemoval = TopologyBfs.detectSplitOnRemoval(toRemove, segments);

        // Count total positions after removal
        int totalAfter = afterRemoval.stream().mapToInt(Set::size).sum();

        assertThat(totalAfter).isEqualTo(segments.size() - 1);
    }

    @Property
    void splitPreservesAllOtherPositions(@ForAll("cableGraph") Set<BlockPos> segments) {
        Assume.that(segments.size() > 1);

        BlockPos toRemove = segments.iterator().next();

        List<Set<BlockPos>> components = TopologyBfs.detectSplitOnRemoval(toRemove, segments);

        // Collect all positions from components
        Set<BlockPos> afterRemoval = new HashSet<>();
        for (Set<BlockPos> comp : components) {
            afterRemoval.addAll(comp);
        }

        // Should contain all except removed
        Set<BlockPos> expected = new HashSet<>(segments);
        expected.remove(toRemove);

        assertThat(afterRemoval).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Property
    void connectedComponentIsSubset(@ForAll("cableGraph") Set<BlockPos> segments) {
        Assume.that(segments.size() > 0);

        BlockPos start = segments.iterator().next();
        Set<BlockPos> component = TopologyBfs.findConnectedComponent(start, segments);

        assertThat(segments).containsAll(component);
    }

    @Property
    void componentContainsStart(@ForAll("cableGraph") Set<BlockPos> segments) {
        Assume.that(segments.size() > 0);

        BlockPos start = segments.iterator().next();
        Set<BlockPos> component = TopologyBfs.findConnectedComponent(start, segments);

        assertThat(component).contains(start);
    }

    @Property
    void adjacencyIsSymmetric(@ForAll("position") BlockPos a, @ForAll("position") BlockPos b) {
        assertThat(TopologyBfs.areAdjacent(a, b))
            .isEqualTo(TopologyBfs.areAdjacent(b, a));
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<Set<BlockPos>> cableGraph() {
        return Arbitraries.integers().between(0, 50)
            .flatMap(size -> {
                if (size == 0) {
                    return Arbitraries.just(Set.of());
                }

                return Arbitraries.integers().between(-20, 20)
                    .tuple3()
                    .map(t -> new BlockPos(WORLD_ID, t.get1(), t.get2(), t.get3()))
                    .set()
                    .ofMinSize(1)
                    .ofMaxSize(size);
            });
    }

    @Provide
    Arbitrary<BlockPos> position() {
        return Arbitraries.integers().between(-100, 100)
            .tuple3()
            .map(t -> new BlockPos(WORLD_ID, t.get1(), t.get2(), t.get3()));
    }
}
