package dev.kate.erd.core.engine;

import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.NetworkId;
import dev.kate.erd.core.model.PipeFamily;
import dev.kate.erd.core.persistence.NetworkStateStore;
import dev.kate.erd.core.util.Clock;
import dev.kate.erd.core.util.ErdLogger;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NetworkPersistenceTest {

    @Test
    void exportImportRoundTrip_preservesTopologyAndPipeFamily() {
        NetworkEngine engine = new NetworkEngine(ErdLogger.silent(), Clock.system());

        UUID world = UUID.randomUUID();
        BlockPos a = new BlockPos(world, 0, 64, 0);
        BlockPos b = new BlockPos(world, 1, 64, 0);
        BlockPos c = new BlockPos(world, 2, 64, 0);

        // Build a simple PIPE line
        engine.addSegment(ConnectionType.PIPE, a);
        engine.addSegment(ConnectionType.PIPE, b);
        engine.addSegment(ConnectionType.PIPE, c);

        NetworkId id = engine.getNetworkAt(ConnectionType.PIPE, a).orElseThrow();
        engine.setPipeFamily(id, PipeFamily.FLUID);

        var exported = engine.exportConnectionState(ConnectionType.PIPE);

        // Import into a fresh engine
        NetworkEngine engine2 = new NetworkEngine(ErdLogger.silent(), Clock.system());
        engine2.importConnectionState(exported);

        assertEquals(Set.of(a, b, c), engine2.getNetworkSegments(ConnectionType.PIPE, id));
        assertEquals(PipeFamily.FLUID, engine2.getPipeFamily(id));
    }

    @Test
    void exportAfterModification_capturesNewSegments() {
        NetworkEngine engine = new NetworkEngine(ErdLogger.silent(), Clock.system());

        UUID world = UUID.randomUUID();
        BlockPos a = new BlockPos(world, 0, 64, 0);
        BlockPos b = new BlockPos(world, 1, 64, 0);

        // Initial state
        engine.addSegment(ConnectionType.POWER, a);
        engine.addSegment(ConnectionType.POWER, b);

        // Verify initial export
        var export1 = engine.exportConnectionState(ConnectionType.POWER);
        assertEquals(1, export1.networks().size());
        assertEquals(Set.of(a, b), export1.networks().get(0).segmentPositions());

        // Add a new segment after initial creation
        BlockPos c = new BlockPos(world, 2, 64, 0);
        engine.addSegment(ConnectionType.POWER, c);

        // Export must capture the new segment
        var export2 = engine.exportConnectionState(ConnectionType.POWER);
        assertEquals(1, export2.networks().size());
        assertEquals(Set.of(a, b, c), export2.networks().get(0).segmentPositions());

        // Import into fresh engine and verify all segments restored
        NetworkEngine engine2 = new NetworkEngine(ErdLogger.silent(), Clock.system());
        engine2.importConnectionState(export2);

        NetworkId netId = engine2.getNetworkAt(ConnectionType.POWER, a).orElseThrow();
        assertEquals(Set.of(a, b, c), engine2.getNetworkSegments(ConnectionType.POWER, netId));
    }

    @Test
    void exportAfterRemoval_excludesRemovedSegments() {
        NetworkEngine engine = new NetworkEngine(ErdLogger.silent(), Clock.system());

        UUID world = UUID.randomUUID();
        BlockPos a = new BlockPos(world, 0, 64, 0);
        BlockPos b = new BlockPos(world, 1, 64, 0);
        BlockPos c = new BlockPos(world, 2, 64, 0);

        engine.addSegment(ConnectionType.POWER, a);
        engine.addSegment(ConnectionType.POWER, b);
        engine.addSegment(ConnectionType.POWER, c);

        // Remove end segment
        engine.removeSegment(ConnectionType.POWER, c);

        // Export must not include the removed segment
        var exported = engine.exportConnectionState(ConnectionType.POWER);
        assertEquals(1, exported.networks().size());
        assertEquals(Set.of(a, b), exported.networks().get(0).segmentPositions());

        // Import into fresh engine and verify
        NetworkEngine engine2 = new NetworkEngine(ErdLogger.silent(), Clock.system());
        engine2.importConnectionState(exported);

        assertTrue(engine2.getNetworkAt(ConnectionType.POWER, a).isPresent());
        assertTrue(engine2.getNetworkAt(ConnectionType.POWER, b).isPresent());
        assertTrue(engine2.getNetworkAt(ConnectionType.POWER, c).isEmpty());
    }

    @Test
    void exportAllLayers_capturesCurrentState() {
        NetworkEngine engine = new NetworkEngine(ErdLogger.silent(), Clock.system());

        UUID world = UUID.randomUUID();
        BlockPos p1 = new BlockPos(world, 0, 64, 0);
        BlockPos p2 = new BlockPos(world, 10, 64, 0);

        engine.addSegment(ConnectionType.POWER, p1);
        engine.addSegment(ConnectionType.DATA, p2);

        // Export and import all layers into a fresh engine (simulates saveLoadedChunks)
        NetworkEngine engine2 = new NetworkEngine(ErdLogger.silent(), Clock.system());
        for (ConnectionType layer : ConnectionType.values()) {
            NetworkStateStore.ConnectionStateData state = engine.exportConnectionState(layer);
            engine2.importConnectionState(state);
        }

        assertTrue(engine2.getNetworkAt(ConnectionType.POWER, p1).isPresent());
        assertTrue(engine2.getNetworkAt(ConnectionType.DATA, p2).isPresent());
        assertTrue(engine2.getNetworkAt(ConnectionType.POWER, p2).isEmpty());
        assertTrue(engine2.getNetworkAt(ConnectionType.DATA, p1).isEmpty());
    }
}
