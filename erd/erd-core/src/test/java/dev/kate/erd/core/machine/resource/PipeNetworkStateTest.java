package dev.kate.erd.core.machine.resource;

import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.model.NetworkId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PipeNetworkStateTest {

    private PipeNetworkState state;
    private NetworkId networkId;

    @BeforeEach
    void setUp() {
        networkId = NetworkId.create();
        state = new PipeNetworkState(networkId);
        state.setNetworkSize(10); // 10 segments = 10,000 mB capacity
    }

    @Test
    void locking_firstProviderLocksNetwork() {
        MachineId provider = MachineId.create();
        boolean locked = state.tryLock(ResourceType.WATER, provider);

        assertThat(locked).isTrue();
        assertThat(state.isLocked()).isTrue();
        assertThat(state.getLockedResourceType()).isEqualTo(ResourceType.WATER);
        assertThat(state.getLockingProviderId()).isEqualTo(provider);
    }

    @Test
    void locking_secondProviderSameType_allowed() {
        MachineId p1 = MachineId.create();
        state.tryLock(ResourceType.WATER, p1);

        MachineId p2 = MachineId.create();
        boolean locked = state.tryLock(ResourceType.WATER, p2);

        assertThat(locked).isTrue();
        assertThat(state.getLockedResourceType()).isEqualTo(ResourceType.WATER);
    }

    @Test
    void locking_secondProviderDifferentType_rejected() {
        MachineId p1 = MachineId.create();
        state.tryLock(ResourceType.WATER, p1);

        MachineId p2 = MachineId.create();
        boolean locked = state.tryLock(ResourceType.LAVA, p2);

        assertThat(locked).isFalse();
        assertThat(state.getLockedResourceType()).isEqualTo(ResourceType.WATER);
    }

    @Test
    void unlocking_lastProviderRemovesLock_ifBufferEmpty() {
        MachineId p1 = MachineId.create();
        state.tryLock(ResourceType.WATER, p1);
        state.addProvider(p1, ResourceType.WATER, 1000);

        state.removeProvider(p1);

        assertThat(state.isLocked()).isFalse();
        assertThat(state.getLockedResourceType()).isNull();
    }

    @Test
    void unlocking_preventedIfBufferNotEmpty() {
        MachineId p1 = MachineId.create();
        state.tryLock(ResourceType.WATER, p1);
        state.addProvider(p1, ResourceType.WATER, 1000);
        
        state.addToBuffer(500); // Add fluid to pipes

        state.removeProvider(p1);

        // Should still be locked because pipes have water
        assertThat(state.isLocked()).isTrue();
        assertThat(state.getLockedResourceType()).isEqualTo(ResourceType.WATER);
    }

    @Test
    void unlocking_occursWhenBufferDrains() {
        MachineId p1 = MachineId.create();
        state.tryLock(ResourceType.WATER, p1);
        state.addProvider(p1, ResourceType.WATER, 1000);
        state.addToBuffer(500);
        state.removeProvider(p1);

        // Still locked
        assertThat(state.isLocked()).isTrue();

        // Drain buffer
        state.removeFromBuffer(500);

        // Now should unlock
        assertThat(state.isLocked()).isFalse();
    }

    @Test
    void buffer_capacityCalculation() {
        state.setNetworkSize(5);
        // 5 * 1000 = 5000
        assertThat(state.getCapacity()).isEqualTo(5000);
    }

    @Test
    void buffer_clamping() {
        state.setNetworkSize(1); // Capacity 1000
        
        state.addToBuffer(500);
        assertThat(state.getStoredAmount()).isEqualTo(500);

        state.addToBuffer(600); // Should cap at 1000
        assertThat(state.getStoredAmount()).isEqualTo(1000);

        state.removeFromBuffer(1200); // Should floor at 0
        assertThat(state.getStoredAmount()).isEqualTo(0);
    }

    @Test
    void throughput_limitsTransfer() {
        state.setMaxThroughput(100);
        
        state.recordTransfer(60);
        assertThat(state.getRemainingThroughput()).isEqualTo(40);

        state.recordTransfer(50);
        assertThat(state.getRemainingThroughput()).isEqualTo(0); // Clamped
    }
}
