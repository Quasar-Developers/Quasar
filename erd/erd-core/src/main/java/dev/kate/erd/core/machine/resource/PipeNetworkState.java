package dev.kate.erd.core.machine.resource;

import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.model.NetworkId;

import java.util.*;

/**
 * Tracks the state of a PIPE network including connected machines and resource flow.
 *
 * <p>Each PIPE network has:
 * <ul>
 *   <li>A locked resource type (determined by first provider)</li>
 *   <li>A maximum throughput per tick</li>
 *   <li>Connected providers and consumers</li>
 *   <li>Flow modifiers (valves, pumps)</li>
 *   <li><b>Internal Buffer:</b> Stores resources "in transit" within the pipes themselves.</li>
 * </ul>
 *
 * <h2>Network Locking</h2>
 * <p>When the first provider connects to a network, the network locks to that
 * provider's resource type. Other providers with different types cannot use the network.
 * When all providers of the locked type disconnect, the network unlocks.</p>
 *
 * <h2>Pipe Buffering</h2>
 * <p>The network acts as a small buffer. Resources extracted from providers are
 * first added to the network buffer, and then pushed to consumers. This simulates
 * "fluid in the pipes" even if the source tank is empty.</p>
 */
public class PipeNetworkState {

    private final NetworkId networkId;

    /**
     * Default maximum throughput per tick for a network.
     * Can be modified by pumps or network upgrades.
     */
    public static final int DEFAULT_MAX_THROUGHPUT = 100;

    /**
     * Buffer capacity per segment block in the network.
     * Set to 1000 (1 bucket) to allow for significant buffering and spillage mechanics.
     */
    public static final int BUFFER_PER_SEGMENT = 1000;

    // The resource type this network is locked to (null = unlocked)
    private ResourceType lockedResourceType;

    // The provider that first locked this network
    private MachineId lockingProviderId;

    // Maximum throughput per tick
    private int maxThroughput = DEFAULT_MAX_THROUGHPUT;

    // Amount transferred this tick (reset each tick)
    private int transferredThisTick = 0;

    // Internal buffer
    private int storedAmount = 0;
    private int networkSize = 0; // Number of segment blocks

    // Connected machines
    private final Map<MachineId, ProviderInfo> providers = new LinkedHashMap<>();
    private final Map<MachineId, ConsumerInfo> consumers = new LinkedHashMap<>();
    private final List<FlowModifierInfo> modifiers = new ArrayList<>();

    public PipeNetworkState(NetworkId networkId) {
        this.networkId = networkId;
    }

    public NetworkId networkId() {
        return networkId;
    }

    // === Resource Type Locking ===

    /**
     * Check if this network is locked to a specific resource type.
     */
    public boolean isLocked() {
        return lockedResourceType != null;
    }

    /**
     * Get the locked resource type, or null if unlocked.
     */
    public ResourceType getLockedResourceType() {
        return lockedResourceType;
    }

    /**
     * Get the ID of the provider that locked this network.
     */
    public MachineId getLockingProviderId() {
        return lockingProviderId;
    }

    /**
     * Try to lock this network to a resource type.
     * Fails if already locked to a different type.
     *
     * @param type the resource type to lock to
     * @param providerId the provider requesting the lock
     * @return true if lock succeeded, false if incompatible
     */
    public boolean tryLock(ResourceType type, MachineId providerId) {
        if (lockedResourceType == null) {
            // First provider - lock to their type
            lockedResourceType = type;
            lockingProviderId = providerId;
            return true;
        }
        // Already locked - only allow same type
        return lockedResourceType == type;
    }

    /**
     * Check if the network should unlock.
     * Unlocks only when no providers of the locked type remain AND buffer is empty.
     */
    public void checkUnlock() {
        if (lockedResourceType == null) return;

        // Check if any providers of the locked type remain
        boolean hasProvidersOfType = providers.values().stream()
            .anyMatch(p -> p.resourceType == lockedResourceType);

        // Also check if we have stored resources. We can't unlock if pipes are full!
        if (!hasProvidersOfType && storedAmount == 0) {
            // No more providers of this type AND buffer empty - unlock
            lockedResourceType = null;
            lockingProviderId = null;
        }
    }

    /**
     * Check if a resource type is compatible with this network.
     */
    public boolean isCompatible(ResourceType type) {
        return lockedResourceType == null || lockedResourceType == type;
    }

    // === Throughput & Buffer ===

    public int getMaxThroughput() {
        return maxThroughput;
    }

    public void setMaxThroughput(int maxThroughput) {
        this.maxThroughput = maxThroughput;
    }

    public int getTransferredThisTick() {
        return transferredThisTick;
    }

    public int getRemainingThroughput() {
        return Math.max(0, maxThroughput - transferredThisTick);
    }

    public void recordTransfer(int amount) {
        transferredThisTick += amount;
    }

    public void resetTick() {
        transferredThisTick = 0;
    }

    public void setNetworkSize(int size) {
        this.networkSize = size;
    }

    public int getNetworkSize() {
        return networkSize;
    }

    public int getCapacity() {
        return Math.max(BUFFER_PER_SEGMENT, networkSize * BUFFER_PER_SEGMENT);
    }

    public int getStoredAmount() {
        return storedAmount;
    }

    public int getSpaceRemaining() {
        return Math.max(0, getCapacity() - storedAmount);
    }

    public void addToBuffer(int amount) {
        storedAmount = Math.min(storedAmount + amount, getCapacity());
    }

    public void removeFromBuffer(int amount) {
        storedAmount = Math.max(0, storedAmount - amount);
        // If buffer hits 0 and no providers, we might unlock
        if (storedAmount == 0) {
            checkUnlock();
        }
    }

    // === Provider Management ===

    public void addProvider(MachineId id, ResourceType type, int available) {
        providers.put(id, new ProviderInfo(id, type, available));
    }

    public void removeProvider(MachineId id) {
        providers.remove(id);
        checkUnlock();
    }

    public Collection<ProviderInfo> getProviders() {
        return Collections.unmodifiableCollection(providers.values());
    }

    public Optional<ProviderInfo> getProvider(MachineId id) {
        return Optional.ofNullable(providers.get(id));
    }

    /**
     * Get providers that can supply the locked resource type.
     */
    public List<ProviderInfo> getCompatibleProviders() {
        if (lockedResourceType == null) {
            return new ArrayList<>(providers.values());
        }
        return providers.values().stream()
            .filter(p -> p.resourceType == lockedResourceType)
            .toList();
    }

    // === Consumer Management ===

    public void addConsumer(MachineId id, Map<ResourceType, Integer> requests) {
        consumers.put(id, new ConsumerInfo(id, requests));
    }

    public void removeConsumer(MachineId id) {
        consumers.remove(id);
    }

    public Collection<ConsumerInfo> getConsumers() {
        return Collections.unmodifiableCollection(consumers.values());
    }

    /**
     * Get consumers that want the locked resource type.
     */
    public List<ConsumerInfo> getCompatibleConsumers() {
        if (lockedResourceType == null) {
            return new ArrayList<>(consumers.values());
        }
        return consumers.values().stream()
            .filter(c -> c.requests.containsKey(lockedResourceType) && c.requests.get(lockedResourceType) > 0)
            .toList();
    }

    // === Flow Modifiers ===

    public void addModifier(FlowModifier modifier, MachineId machineId) {
        modifiers.add(new FlowModifierInfo(modifier, machineId));
        // Sort by priority
        modifiers.sort(Comparator.comparingInt(m -> m.modifier.getPriority()));
    }

    public void removeModifier(MachineId machineId) {
        modifiers.removeIf(m -> m.machineId.equals(machineId));
    }

    public List<FlowModifier> getActiveModifiers() {
        return modifiers.stream()
            .map(m -> m.modifier)
            .filter(FlowModifier::isActive)
            .toList();
    }

    /**
     * Apply all flow modifiers to a proposed transfer rate.
     */
    public int applyModifiers(ResourceType type, int proposedRate) {
        int rate = proposedRate;
        for (FlowModifier modifier : getActiveModifiers()) {
            rate = modifier.modifyFlowRate(type, rate);
            if (rate <= 0) break;
        }
        return rate;
    }

    // === Info Records ===

    public record ProviderInfo(MachineId machineId, ResourceType resourceType, int available) {}

    public record ConsumerInfo(MachineId machineId, Map<ResourceType, Integer> requests) {
        public int getRequest(ResourceType type) {
            return requests.getOrDefault(type, 0);
        }
    }

    private record FlowModifierInfo(FlowModifier modifier, MachineId machineId) {}

    // === Debug ===

    @Override
    public String toString() {
        return String.format("PipeNetworkState[id=%s, locked=%s, stored=%d/%d, throughput=%d/%d]",
            networkId,
            lockedResourceType != null ? lockedResourceType.displayName() : "UNLOCKED",
            storedAmount,
            getCapacity(),
            transferredThisTick,
            maxThroughput
        );
    }
}
