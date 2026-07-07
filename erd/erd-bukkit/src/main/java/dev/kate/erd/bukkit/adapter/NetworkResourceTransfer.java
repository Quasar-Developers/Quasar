package dev.kate.erd.bukkit.adapter;

import dev.kate.erd.core.util.ErdLogger;
import dev.kate.erd.core.engine.NetworkEngine;
import dev.kate.erd.core.event.topology.TopologyChangedEvent;
import dev.kate.erd.core.topology.TopologyResult;
import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.endpoint.ResourceEndpoint;
import dev.kate.erd.core.machine.InstanceManager;
import dev.kate.erd.core.machine.MachineInstance;
import dev.kate.erd.core.machine.resource.*;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.model.NetworkId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles resource transfer between machines via PIPE networks.
 *
 * <h2>Architecture</h2>
 * <p>Uses a three-phase approach each tick:</p>
 * <ol>
 *   <li><b>COLLECT:</b> Gather available resources from providers and requests from consumers</li>
 *   <li><b>ROUTE:</b> Calculate transfers considering network locks, throughput, and flow modifiers</li>
 *   <li><b>EXECUTE:</b> Perform the actual resource transfers</li>
 * </ol>
 *
 * <h2>Network Locking</h2>
 * <p>PIPE networks lock to the first provider's resource type. Other providers
 * with different types cannot use the network until all providers of the locked type disconnect.</p>
 *
 * <h2>Throughput Limits</h2>
 * <p>Each network has a maximum throughput per tick (default: 100 units).
 * This can be modified by pumps or network upgrades.</p>
 *
 * <h2>Buffering</h2>
 * <p>Resources are transferred from Providers -> Network Buffer -> Consumers.
 * This allows pipes to hold fluid even if the source is empty.</p>
 */
public class NetworkResourceTransfer {

    private final ErdLogger logger;
    private final NetworkEngine engine;
    private final InstanceManager instanceManager;

    // Network states - persisted between ticks for locking
    private final Map<NetworkId, PipeNetworkState> networkStates = new ConcurrentHashMap<>();

    // Transfer rate per provider/consumer pair per tick
    private static final int TRANSFER_RATE_PER_PAIR = 20;

    // Demand-driven: Only process networks that have active requests/injections
    private final Set<NetworkId> dirtyNetworks = ConcurrentHashMap.newKeySet();

    public NetworkResourceTransfer(ErdLogger logger, NetworkEngine engine, InstanceManager instanceManager) {
        this.logger = logger;
        this.engine = engine;
        this.instanceManager = instanceManager;

        // Register for topology changes to invalidate affected networks
        engine.getEventBus().register(TopologyChangedEvent.class, this::onTopologyChange);
    }

    /**
     * Process resource transfers for all PIPE networks.
     * Called once per tick.
     */
    public void tick() {
        // Reset per-tick state
        for (PipeNetworkState state : networkStates.values()) {
            state.resetTick();
        }

        // Get all PIPE networks
        Set<NetworkId> pipeNetworks = engine.getAllNetworks(ConnectionType.PIPE);

        // Clean up states for networks that no longer exist
        networkStates.keySet().removeIf(id -> !pipeNetworks.contains(id));

        // Process each network
        for (NetworkId networkId : pipeNetworks) {
            processNetwork(networkId);
        }
    }

    /**
     * Process a single PIPE network through all three phases.
     */
    private void processNetwork(NetworkId networkId) {
        // Get or create network state
        PipeNetworkState state = networkStates.computeIfAbsent(networkId, PipeNetworkState::new);

        // Phase 1: COLLECT
        collectPhase(networkId, state);

        // Phase 2: ROUTE (Input & Output)
        List<Transfer> transfers = routePhase(state);

        // Phase 3: EXECUTE
        executePhase(transfers, state);
    }

    // ===========================================
    // PHASE 1: COLLECT
    // ===========================================

    /**
     * Gather information about providers and consumers on this network.
     */
    private void collectPhase(NetworkId networkId, PipeNetworkState state) {
        // Clear old machine references (keep lock state)
        clearMachineReferences(state);

        // Find all segment positions in this network
        Set<BlockPos> networkPositions = engine.getNetworkSegments(ConnectionType.PIPE, networkId);
        
        // Update network size for capacity calculation
        state.setNetworkSize(networkPositions.size());

        // Find machines adjacent to network segments
        // Use spatial lookup for O(1) access instead of O(N) iteration over all machines
        Set<MachineInstance> connectedMachines = new HashSet<>();
        for (BlockPos cablePos : networkPositions) {
            for (BlockPos adjacent : getAdjacentPositions(cablePos)) {
                // Check for machines at adjacent positions
                instanceManager.getMachineAt(adjacent).ifPresent(connectedMachines::add);
                
                // Check for endpoints at adjacent positions
                // This replaces the O(Networks × Machines) iteration with O(Networks × Segments × 6)
                instanceManager.getEndpointAt(adjacent).ifPresent(endpoint -> {
                    if (endpoint.layer() == ConnectionType.PIPE) {
                        // Get the machine that owns this endpoint
                        instanceManager.getMachineForEndpoint(endpoint).ifPresent(machine -> {
                            connectedMachines.add(machine);
                            
                            // IMPORTANT: Attach the endpoint to this network!
                            // This enables proper filtering by ResourceEndpoint type
                            if (!endpoint.attachedNetwork().isPresent() ||
                                !endpoint.attachedNetwork().get().equals(networkId)) {
                                endpoint.onAttach(networkId);
                            }
                        });
                    }
                });
            }
        }

        // Categorize machines and collect their info
        for (MachineInstance machine : connectedMachines) {
            // Check if provider
            if (machine instanceof ResourceProvider provider) {
                collectProvider(state, machine, provider, networkId);
            }

            // Check if consumer (note: machines can be both)
            if (machine instanceof ResourceConsumer consumer) {
                collectConsumer(state, machine, consumer, networkId);
            }

            // Check if flow modifier
            if (machine instanceof FlowModifier modifier) {
                state.addModifier(modifier, machine.id());
            }
        }
    }

    /**
     * Collect provider information and handle network locking.
     * Filters resources based on ResourceEndpoints connected to this network.
     */
    private void collectProvider(PipeNetworkState state, MachineInstance machine,
                                ResourceProvider provider, NetworkId networkId) {
        Map<ResourceType, Integer> available = provider.getAvailableResources();
        if (available.isEmpty()) return;

        // Find which resource types can be provided through endpoints connected to THIS network
        Set<ResourceType> allowedTypes = new HashSet<>();
        for (Endpoint endpoint : machine.endpoints()) {
            if (endpoint.layer() == ConnectionType.PIPE &&
                endpoint.attachedNetwork().isPresent() &&
                endpoint.attachedNetwork().get().equals(networkId)) {

                if (endpoint instanceof ResourceEndpoint resourceEndpoint) {
                    // This endpoint is connected to our network and has a specific resource type
                    allowedTypes.add(resourceEndpoint.resourceType());
                } else {
                    // Generic endpoint - allow all resources (backwards compatibility)
                    allowedTypes.addAll(available.keySet());
                    break;
                }
            }
        }

        if (allowedTypes.isEmpty()) return;

        // Filter available resources to only those allowed by connected endpoints
        Map<ResourceType, Integer> filteredAvailable = new HashMap<>();
        for (ResourceType type : allowedTypes) {
            if (available.containsKey(type)) {
                filteredAvailable.put(type, available.get(type));
            }
        }

        if (filteredAvailable.isEmpty()) return;

        // Get the provider's primary resource type from filtered list
        ResourceType primaryType = provider.getPrimaryResourceType();
        if (primaryType == null || !filteredAvailable.containsKey(primaryType)) {
            // Take first filtered available
            primaryType = filteredAvailable.keySet().iterator().next();
        }

        // Try to lock network to this type (or verify compatibility)
        if (!state.tryLock(primaryType, machine.id())) {
            // Network is locked to a different type - this provider is incompatible
            logger.debug("Provider {} incompatible with network {} (locked to {})",
                machine.id(), state.networkId(), state.getLockedResourceType());
            return;
        }

        // Add provider with the amount of the locked resource type
        int availableAmount = filteredAvailable.getOrDefault(state.getLockedResourceType(), 0);
        if (availableAmount > 0) {
            state.addProvider(machine.id(), state.getLockedResourceType(), availableAmount);
        }
    }

    /**
     * Collect consumer requests.
     * Filters resources based on ResourceEndpoints connected to this network.
     */
    private void collectConsumer(PipeNetworkState state, MachineInstance machine,
                                ResourceConsumer consumer, NetworkId networkId) {
        Map<ResourceType, Integer> requests = consumer.getResourceRequests();
        if (requests.isEmpty()) return;

        // Find which resource types can be consumed through endpoints connected to THIS network
        Set<ResourceType> allowedTypes = new HashSet<>();
        for (Endpoint endpoint : machine.endpoints()) {
            if (endpoint.layer() == ConnectionType.PIPE &&
                endpoint.attachedNetwork().isPresent() &&
                endpoint.attachedNetwork().get().equals(networkId)) {

                if (endpoint instanceof ResourceEndpoint resourceEndpoint) {
                    // This endpoint is connected to our network and has a specific resource type
                    allowedTypes.add(resourceEndpoint.resourceType());
                } else {
                    // Generic endpoint - allow all resources (backwards compatibility)
                    allowedTypes.addAll(requests.keySet());
                    break;
                }
            }
        }

        if (allowedTypes.isEmpty()) return;

        // Filter requests to only those allowed by connected endpoints
        Map<ResourceType, Integer> filteredRequests = new HashMap<>();
        for (ResourceType type : allowedTypes) {
            if (requests.containsKey(type)) {
                filteredRequests.put(type, requests.get(type));
            }
        }

        if (!filteredRequests.isEmpty()) {
            state.addConsumer(machine.id(), filteredRequests);
        }
    }

    /**
     * Clear machine references but keep lock state.
     */
    private void clearMachineReferences(PipeNetworkState state) {
        // We need to re-collect each tick, but preserve lock state
        // Remove providers and check if we should unlock
        List<MachineId> providerIds = state.getProviders().stream()
            .map(PipeNetworkState.ProviderInfo::machineId)
            .toList();
        for (MachineId id : providerIds) {
            state.removeProvider(id);
        }

        // Remove all consumers
        List<MachineId> consumerIds = state.getConsumers().stream()
            .map(PipeNetworkState.ConsumerInfo::machineId)
            .toList();
        for (MachineId id : consumerIds) {
            state.removeConsumer(id);
        }
    }

    // ===========================================
    // PHASE 2: ROUTE
    // ===========================================

    /**
     * Calculate transfers based on available resources and requests.
     * Uses buffered flow: Provider -> Network -> Consumer.
     */
    private List<Transfer> routePhase(PipeNetworkState state) {
        List<Transfer> transfers = new ArrayList<>();

        // If network is not locked, nothing to transfer
        if (!state.isLocked()) {
            return transfers;
        }

        ResourceType lockedType = state.getLockedResourceType();

        // --- Step 1: Input (Provider -> Network) ---
        int spaceRemaining = state.getSpaceRemaining();
        if (spaceRemaining > 0) {
            List<PipeNetworkState.ProviderInfo> providers = state.getCompatibleProviders();
            for (PipeNetworkState.ProviderInfo provider : providers) {
                if (spaceRemaining <= 0) break;

                int available = provider.available();
                if (available <= 0) continue;

                // Check throughput limit for injection
                int remainingThroughput = state.getRemainingThroughput();
                if (remainingThroughput <= 0) break;

                int baseAmount = Math.min(available, Math.min(spaceRemaining, TRANSFER_RATE_PER_PAIR));
                baseAmount = Math.min(baseAmount, remainingThroughput);

                // Apply flow modifiers
                int finalAmount = state.applyModifiers(lockedType, baseAmount);

                if (finalAmount > 0) {
                    // Create "Injection" transfer (Consumer ID is null to signify network)
                    transfers.add(new Transfer(
                        provider.machineId(),
                        null, // Target is network
                        lockedType,
                        finalAmount
                    ));

                    state.recordTransfer(finalAmount);
                    state.addToBuffer(finalAmount); // Optimistically add to buffer for calculation
                    spaceRemaining -= finalAmount;
                }
            }
        }

        // --- Step 2: Output (Network -> Consumer) ---
        int storedAmount = state.getStoredAmount(); // Use current stored amount (including what we just injected)
        if (storedAmount > 0) {
            List<PipeNetworkState.ConsumerInfo> consumers = state.getCompatibleConsumers();
            for (PipeNetworkState.ConsumerInfo consumer : consumers) {
                if (storedAmount <= 0) break;

                int wanted = consumer.getRequest(lockedType);
                if (wanted <= 0) continue;

                // Check throughput limit for ejection
                int remainingThroughput = state.getRemainingThroughput();
                if (remainingThroughput <= 0) break;

                int baseAmount = Math.min(wanted, Math.min(storedAmount, TRANSFER_RATE_PER_PAIR));
                baseAmount = Math.min(baseAmount, remainingThroughput);

                // Apply flow modifiers
                int finalAmount = state.applyModifiers(lockedType, baseAmount);

                if (finalAmount > 0) {
                    // Create "Ejection" transfer (Provider ID is null to signify network)
                    transfers.add(new Transfer(
                        null, // Source is network
                        consumer.machineId(),
                        lockedType,
                        finalAmount
                    ));

                    state.recordTransfer(finalAmount);
                    state.removeFromBuffer(finalAmount); // Optimistically remove
                    storedAmount -= finalAmount;
                }
            }
        }

        return transfers;
    }

    // ===========================================
    // PHASE 3: EXECUTE
    // ===========================================

    /**
     * Execute the calculated transfers.
     */
    private void executePhase(List<Transfer> transfers, PipeNetworkState state) {
        // Revert optimistic buffer changes from route phase so we can apply real results
        // (This is a bit hacky, but simpler than tracking "pending" buffer state separately)
        // Actually, since we are single-threaded here, we can just apply the transfers.
        // The route phase modified the state object's buffer, but NOT the machines.
        // We need to make sure the machines actually give/take the resources.

        // Reset buffer to pre-route state? No, route phase updated it correctly assuming success.
        // If a machine fails to give/take, we need to correct the buffer.

        for (Transfer transfer : transfers) {
            if (transfer.consumerId == null) {
                // === Injection: Provider -> Network ===
                Optional<MachineInstance> providerOpt = instanceManager.getMachine(transfer.providerId);
                if (providerOpt.isPresent() && providerOpt.get() instanceof ResourceProvider provider) {
                    int extracted = provider.extractResource(transfer.resourceType, transfer.amount);
                    if (extracted < transfer.amount) {
                        // Provider gave less than expected, correct the buffer
                        state.removeFromBuffer(transfer.amount - extracted);
                    }
                } else {
                    // Provider gone, revert buffer add
                    state.removeFromBuffer(transfer.amount);
                }

            } else if (transfer.providerId == null) {
                // === Ejection: Network -> Consumer ===
                Optional<MachineInstance> consumerOpt = instanceManager.getMachine(transfer.consumerId);
                if (consumerOpt.isPresent() && consumerOpt.get() instanceof ResourceConsumer consumer) {
                    int accepted = consumer.acceptResource(transfer.resourceType, transfer.amount);
                    if (accepted < transfer.amount) {
                        // Consumer took less than expected, put back in buffer
                        state.addToBuffer(transfer.amount - accepted);
                    }
                } else {
                    // Consumer gone, put back in buffer
                    state.addToBuffer(transfer.amount);
                }
            }
        }
    }

    // ===========================================
    // EVENT LISTENERS
    // ===========================================

    /**
     * Handle topology changes in PIPE networks.
     * When a network splits/merges, we need to invalidate cached state.
     */
    private void onTopologyChange(TopologyChangedEvent event) {
        if (event.layer() != ConnectionType.PIPE) {
            return;
        }

        // Invalidate network states for affected networks
        TopologyResult result = event.result();

        switch (result) {
            case TopologyResult.NetworkCreated created ->
                logger.debug("PIPE network created: {}", created.newNetworkId());

            case TopologyResult.NetworkDissolved dissolved -> {
                networkStates.remove(dissolved.networkId());
                // Detach endpoints that were attached to this network
                detachEndpointsFromNetwork(dissolved.networkId());
                logger.debug("PIPE network dissolved: {}", dissolved.networkId());
            }

            case TopologyResult.NetworksMerged merged -> {
                for (NetworkId oldId : merged.mergedNetworkIds()) {
                    networkStates.remove(oldId);
                    // Detach endpoints from old networks (they'll be re-attached on next tick)
                    detachEndpointsFromNetwork(oldId);
                }
                logger.debug("PIPE networks merged into: {}", merged.primaryNetworkId());
            }

            case TopologyResult.NetworkSplit split -> {
                networkStates.remove(split.originalNetworkId());
                // Detach endpoints from original network (they'll be re-attached on next tick)
                detachEndpointsFromNetwork(split.originalNetworkId());
                logger.debug("PIPE network split: {} -> {} components",
                    split.originalNetworkId(), split.resultingComponents().size());
            }

            default -> {
                // Other results don't require state invalidation
            }
        }
    }


    /**
     * Get the state of a specific network (for debugging).
     */
    public Optional<PipeNetworkState> getNetworkState(NetworkId networkId) {
        return Optional.ofNullable(networkStates.get(networkId));
    }

    /**
     * Get all network states (for debugging).
     */
    public Collection<PipeNetworkState> getAllNetworkStates() {
        return Collections.unmodifiableCollection(networkStates.values());
    }

    /**
     * Force unlock a network (for debugging/admin).
     */
    public void forceUnlock(NetworkId networkId) {
        networkStates.remove(networkId);
    }

    /**
     * Detach all endpoints that are currently attached to the given network.
     * Called when a network is dissolved, merged, or split.
     */
    private void detachEndpointsFromNetwork(NetworkId networkId) {
        for (MachineInstance machine : instanceManager.allMachines()) {
            for (Endpoint endpoint : machine.endpoints()) {
                if (endpoint.layer() == ConnectionType.PIPE &&
                    endpoint.attachedNetwork().isPresent() &&
                    endpoint.attachedNetwork().get().equals(networkId)) {
                    endpoint.onDetach();
                }
            }
        }
    }

    /**
     * Get positions adjacent to the given position (6 directions).
     */
    private List<BlockPos> getAdjacentPositions(BlockPos pos) {
        return List.of(
            pos.offset(1, 0, 0),
            pos.offset(-1, 0, 0),
            pos.offset(0, 1, 0),
            pos.offset(0, -1, 0),
            pos.offset(0, 0, 1),
            pos.offset(0, 0, -1)
        );
    }

    /**
     * A planned resource transfer.
     * If providerId is null, it's an ejection from network.
     * If consumerId is null, it's an injection to network.
     */
    private record Transfer(
        MachineId providerId,
        MachineId consumerId,
        ResourceType resourceType,
        int amount
    ) {}
}
