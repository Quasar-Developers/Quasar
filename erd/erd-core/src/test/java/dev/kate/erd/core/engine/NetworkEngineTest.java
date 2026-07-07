package dev.kate.erd.core.engine;

import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.NetworkId;
import dev.kate.erd.core.topology.TopologyResult;
import dev.kate.erd.core.util.ErdLogger;
import dev.kate.erd.core.util.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for NetworkEngine topology operations.
 */
class NetworkEngineTest {

    private static final UUID WORLD_ID = UUID.randomUUID();

    private NetworkEngine engine;
    private TestClock clock;

    @BeforeEach
    void setUp() {
        clock = new TestClock(1000);
        engine = new NetworkEngine(ErdLogger.silent(), clock);
    }

    private BlockPos pos(int x, int y, int z) {
        return new BlockPos(WORLD_ID, x, y, z);
    }

    // ========== Segment Addition Tests ==========

    @Test
    void addSegment_firstSegment_createsNetwork() {
        TopologyResult result = engine.addSegment(ConnectionType.POWER, pos(0, 0, 0));

        assertThat(result).isInstanceOf(TopologyResult.NetworkCreated.class);

        TopologyResult.NetworkCreated created = (TopologyResult.NetworkCreated) result;
        assertThat(created.position()).isEqualTo(pos(0, 0, 0));
        assertThat(created.newNetworkId()).isNotNull();

        // Verify network exists
        Optional<NetworkId> networkOpt = engine.getNetworkAt(ConnectionType.POWER, pos(0, 0, 0));
        assertThat(networkOpt).isPresent();
        assertThat(networkOpt.get()).isEqualTo(created.newNetworkId());
    }

    @Test
    void addSegment_adjacentToExisting_joinsNetwork() {
        // Add first segment
        TopologyResult first = engine.addSegment(ConnectionType.POWER, pos(0, 0, 0));
        NetworkId networkId = ((TopologyResult.NetworkCreated) first).newNetworkId();

        // Add adjacent segment
        TopologyResult result = engine.addSegment(ConnectionType.POWER, pos(1, 0, 0));

        assertThat(result).isInstanceOf(TopologyResult.SegmentAdded.class);
        TopologyResult.SegmentAdded added = (TopologyResult.SegmentAdded) result;
        assertThat(added.networkId()).isEqualTo(networkId);

        // Verify both in same network
        assertThat(engine.getNetworkAt(ConnectionType.POWER, pos(0, 0, 0))).contains(networkId);
        assertThat(engine.getNetworkAt(ConnectionType.POWER, pos(1, 0, 0))).contains(networkId);
    }

    @Test
    void addSegment_bridgingTwoNetworks_merges() {
        // Create two separate networks
        engine.addSegment(ConnectionType.POWER, pos(0, 0, 0));
        engine.addSegment(ConnectionType.POWER, pos(2, 0, 0));

        // Verify they're separate
        NetworkId network1 = engine.getNetworkAt(ConnectionType.POWER, pos(0, 0, 0)).orElseThrow();
        NetworkId network2 = engine.getNetworkAt(ConnectionType.POWER, pos(2, 0, 0)).orElseThrow();
        assertThat(network1).isNotEqualTo(network2);

        // Bridge them
        TopologyResult result = engine.addSegment(ConnectionType.POWER, pos(1, 0, 0));

        assertThat(result).isInstanceOf(TopologyResult.NetworksMerged.class);
        TopologyResult.NetworksMerged merged = (TopologyResult.NetworksMerged) result;

        // Verify all in same network now
        NetworkId finalNetwork = engine.getNetworkAt(ConnectionType.POWER, pos(0, 0, 0)).orElseThrow();
        assertThat(engine.getNetworkAt(ConnectionType.POWER, pos(1, 0, 0))).contains(finalNetwork);
        assertThat(engine.getNetworkAt(ConnectionType.POWER, pos(2, 0, 0))).contains(finalNetwork);
    }

    @Test
    void addSegment_duplicate_returnsNoChange() {
        engine.addSegment(ConnectionType.POWER, pos(0, 0, 0));

        TopologyResult result = engine.addSegment(ConnectionType.POWER, pos(0, 0, 0));

        assertThat(result).isInstanceOf(TopologyResult.NoChange.class);
    }

    @Test
    void addSegment_differentLayers_separateNetworks() {
        engine.addSegment(ConnectionType.POWER, pos(0, 0, 0));
        engine.addSegment(ConnectionType.DATA, pos(0, 0, 0));

        Optional<NetworkId> powerNet = engine.getNetworkAt(ConnectionType.POWER, pos(0, 0, 0));
        Optional<NetworkId> dataNet = engine.getNetworkAt(ConnectionType.DATA, pos(0, 0, 0));

        assertThat(powerNet).isPresent();
        assertThat(dataNet).isPresent();
        assertThat(powerNet.get()).isNotEqualTo(dataNet.get());
    }

    // ========== Segment Removal Tests ==========

    @Test
    void removeSegment_lastInNetwork_dissolvesNetwork() {
        engine.addSegment(ConnectionType.POWER, pos(0, 0, 0));
        NetworkId networkId = engine.getNetworkAt(ConnectionType.POWER, pos(0, 0, 0)).orElseThrow();

        TopologyResult result = engine.removeSegment(ConnectionType.POWER, pos(0, 0, 0));

        assertThat(result).isInstanceOf(TopologyResult.NetworkDissolved.class);
        TopologyResult.NetworkDissolved dissolved = (TopologyResult.NetworkDissolved) result;
        assertThat(dissolved.networkId()).isEqualTo(networkId);

        // Verify network gone
        assertThat(engine.getNetworkAt(ConnectionType.POWER, pos(0, 0, 0))).isEmpty();
        assertThat(engine.getAllNetworks(ConnectionType.POWER)).doesNotContain(networkId);
    }

    @Test
    void removeSegment_middleOfChain_splitsNetwork() {
        // Create chain: A - B - C
        engine.addSegment(ConnectionType.POWER, pos(0, 0, 0));
        engine.addSegment(ConnectionType.POWER, pos(1, 0, 0));
        engine.addSegment(ConnectionType.POWER, pos(2, 0, 0));

        // Remove middle
        TopologyResult result = engine.removeSegment(ConnectionType.POWER, pos(1, 0, 0));

        assertThat(result).isInstanceOf(TopologyResult.NetworkSplit.class);
        TopologyResult.NetworkSplit split = (TopologyResult.NetworkSplit) result;
        assertThat(split.resultingComponents()).hasSize(2);

        // Verify A and C in different networks
        NetworkId networkA = engine.getNetworkAt(ConnectionType.POWER, pos(0, 0, 0)).orElseThrow();
        NetworkId networkC = engine.getNetworkAt(ConnectionType.POWER, pos(2, 0, 0)).orElseThrow();
        assertThat(networkA).isNotEqualTo(networkC);
    }

    @Test
    void removeSegment_endOfChain_noSplit() {
        // Create chain: A - B - C
        engine.addSegment(ConnectionType.POWER, pos(0, 0, 0));
        engine.addSegment(ConnectionType.POWER, pos(1, 0, 0));
        engine.addSegment(ConnectionType.POWER, pos(2, 0, 0));

        // Remove end
        TopologyResult result = engine.removeSegment(ConnectionType.POWER, pos(2, 0, 0));

        assertThat(result).isInstanceOf(TopologyResult.SegmentRemoved.class);

        // Verify A and B still in same network
        NetworkId networkA = engine.getNetworkAt(ConnectionType.POWER, pos(0, 0, 0)).orElseThrow();
        NetworkId networkB = engine.getNetworkAt(ConnectionType.POWER, pos(1, 0, 0)).orElseThrow();
        assertThat(networkA).isEqualTo(networkB);
    }

    @Test
    void removeSegment_nonExistent_returnsNoChange() {
        TopologyResult result = engine.removeSegment(ConnectionType.POWER, pos(0, 0, 0));

        assertThat(result).isInstanceOf(TopologyResult.NoChange.class);
    }

    // ========== Query Tests ==========

    @Test
    void getNetworkPositions_returnsAllPositions() {
        engine.addSegment(ConnectionType.POWER, pos(0, 0, 0));
        engine.addSegment(ConnectionType.POWER, pos(1, 0, 0));
        engine.addSegment(ConnectionType.POWER, pos(2, 0, 0));

        NetworkId networkId = engine.getNetworkAt(ConnectionType.POWER, pos(0, 0, 0)).orElseThrow();
        Set<BlockPos> positions = engine.getNetworkSegments(ConnectionType.POWER, networkId);

        assertThat(positions).containsExactlyInAnyOrder(
            pos(0, 0, 0), pos(1, 0, 0), pos(2, 0, 0)
        );
    }

    @Test
    void getAllNetworks_returnsAllNetworkIds() {
        engine.addSegment(ConnectionType.POWER, pos(0, 0, 0));
        engine.addSegment(ConnectionType.POWER, pos(10, 0, 0)); // Separate network

        Set<NetworkId> networks = engine.getAllNetworks(ConnectionType.POWER);

        assertThat(networks).hasSize(2);
    }

    // ========== Version Tests ==========

    @Test
    void version_incrementsOnChange() {
        long v0 = engine.getVersion(ConnectionType.POWER);

        engine.addSegment(ConnectionType.POWER, pos(0, 0, 0));
        long v1 = engine.getVersion(ConnectionType.POWER);

        engine.addSegment(ConnectionType.POWER, pos(1, 0, 0));
        long v2 = engine.getVersion(ConnectionType.POWER);

        assertThat(v1).isGreaterThan(v0);
        assertThat(v2).isGreaterThan(v1);
    }

    @Test
    void version_unchangedOnNoOp() {
        engine.addSegment(ConnectionType.POWER, pos(0, 0, 0));
        long v1 = engine.getVersion(ConnectionType.POWER);

        // Try to add same segment again
        engine.addSegment(ConnectionType.POWER, pos(0, 0, 0));
        long v2 = engine.getVersion(ConnectionType.POWER);

        assertThat(v2).isEqualTo(v1);
    }

    // ========== Statistics Tests ==========

    @Test
    void statistics_accurate() {
        engine.addSegment(ConnectionType.POWER, pos(0, 0, 0));
        engine.addSegment(ConnectionType.POWER, pos(1, 0, 0));
        engine.addSegment(ConnectionType.POWER, pos(10, 0, 0)); // Separate network

        var stats = engine.getStatistics(ConnectionType.POWER);

        assertThat(stats.type()).isEqualTo(ConnectionType.POWER);
        assertThat(stats.segmentCount()).isEqualTo(3);
        assertThat(stats.networkCount()).isEqualTo(2);
    }
}
