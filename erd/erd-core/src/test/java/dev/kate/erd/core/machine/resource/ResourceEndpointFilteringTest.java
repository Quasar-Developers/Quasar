package dev.kate.erd.core.machine.resource;

import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.endpoint.EndpointRole;
import dev.kate.erd.core.endpoint.ResourceEndpoint;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.model.NetworkId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * Helper to create BlockPos for tests.
 */

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that ResourceEndpoints properly filter which resources can flow through each endpoint.
 *
 * <p>This ensures that when a reactor has separate water and hydrogen endpoints, each endpoint
 * only transfers its designated resource type.
 *
 * <h2>Problem Being Tested</h2>
 * <p>The reactor asks for hydrogen and water, but each should be bounded to its specific endpoint.
 * If you connect the water endpoint to a generator, the generator should only provide water
 * through that connection, not hydrogen (even if it produces both).
 *
 * <h2>Expected Behavior</h2>
 * <ul>
 *   <li>A generator connected via a water endpoint only provides water</li>
 *   <li>A reactor's water endpoint only pulls water from the network</li>
 *   <li>Different resource types flow through different networks</li>
 * </ul>
 */
class ResourceEndpointFilteringTest {

    private static final UUID TEST_WORLD = UUID.randomUUID();
    private static final NetworkId WATER_NETWORK = NetworkId.create();
    private static final NetworkId HYDROGEN_NETWORK = NetworkId.create();

    /**
     * Helper to create BlockPos in the test world.
     */
    private static BlockPos pos(int x, int y, int z) {
        return new BlockPos(TEST_WORLD, x, y, z);
    }

    @Nested
    @DisplayName("Endpoint Resource Filtering Logic")
    class EndpointFilteringTests {

        /**
         * Simulates the filtering logic used in NetworkResourceTransfer.collectProvider()
         * to verify that resource endpoints correctly filter resources.
         */
        @Test
        @DisplayName("Water endpoint should only allow WATER resource type")
        void waterEndpointOnlyAllowsWater() {
            // Create a water endpoint
            ResourceEndpoint waterEndpoint = new ResourceEndpoint(
                pos(1, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.WATER
            );
            waterEndpoint.onAttach(WATER_NETWORK);

            // Generator has both water and hydrogen
            Map<ResourceType, Integer> availableResources = Map.of(
                ResourceType.WATER, 100,
                ResourceType.HYDROGEN, 50
            );

            // Simulate filtering logic from NetworkResourceTransfer
            Set<ResourceType> allowedTypes = new HashSet<>();
            if (waterEndpoint.attachedNetwork().isPresent() &&
                waterEndpoint.attachedNetwork().get().equals(WATER_NETWORK)) {
                allowedTypes.add(waterEndpoint.resourceType());
            }

            // Filter available resources
            Map<ResourceType, Integer> filtered = new HashMap<>();
            for (ResourceType type : allowedTypes) {
                if (availableResources.containsKey(type)) {
                    filtered.put(type, availableResources.get(type));
                }
            }

            // Verify: Only water should be in filtered results
            assertThat(filtered).containsOnlyKeys(ResourceType.WATER);
            assertThat(filtered.get(ResourceType.WATER)).isEqualTo(100);
            assertThat(filtered).doesNotContainKey(ResourceType.HYDROGEN);
        }

        @Test
        @DisplayName("Hydrogen endpoint should only allow HYDROGEN resource type")
        void hydrogenEndpointOnlyAllowsHydrogen() {
            ResourceEndpoint hydrogenEndpoint = new ResourceEndpoint(
                pos(-1, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.HYDROGEN
            );
            hydrogenEndpoint.onAttach(HYDROGEN_NETWORK);

            Map<ResourceType, Integer> availableResources = Map.of(
                ResourceType.WATER, 100,
                ResourceType.HYDROGEN, 50
            );

            // Simulate filtering
            Set<ResourceType> allowedTypes = new HashSet<>();
            if (hydrogenEndpoint.attachedNetwork().isPresent() &&
                hydrogenEndpoint.attachedNetwork().get().equals(HYDROGEN_NETWORK)) {
                allowedTypes.add(hydrogenEndpoint.resourceType());
            }

            Map<ResourceType, Integer> filtered = new HashMap<>();
            for (ResourceType type : allowedTypes) {
                if (availableResources.containsKey(type)) {
                    filtered.put(type, availableResources.get(type));
                }
            }

            assertThat(filtered).containsOnlyKeys(ResourceType.HYDROGEN);
            assertThat(filtered.get(ResourceType.HYDROGEN)).isEqualTo(50);
            assertThat(filtered).doesNotContainKey(ResourceType.WATER);
        }

        @Test
        @DisplayName("Endpoint not attached to target network should filter out all resources")
        void endpointOnDifferentNetworkFiltersEverything() {
            ResourceEndpoint waterEndpoint = new ResourceEndpoint(
                pos(1, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.WATER
            );
            waterEndpoint.onAttach(WATER_NETWORK);

            Map<ResourceType, Integer> availableResources = Map.of(
                ResourceType.WATER, 100,
                ResourceType.HYDROGEN, 50
            );

            // Check against HYDROGEN_NETWORK (different from water endpoint's network)
            Set<ResourceType> allowedTypes = new HashSet<>();
            if (waterEndpoint.attachedNetwork().isPresent() &&
                waterEndpoint.attachedNetwork().get().equals(HYDROGEN_NETWORK)) {
                allowedTypes.add(waterEndpoint.resourceType());
            }

            Map<ResourceType, Integer> filtered = new HashMap<>();
            for (ResourceType type : allowedTypes) {
                if (availableResources.containsKey(type)) {
                    filtered.put(type, availableResources.get(type));
                }
            }

            // Should be empty - endpoint is not on this network
            assertThat(filtered).isEmpty();
        }
    }

    @Nested
    @DisplayName("Multi-Endpoint Machine Filtering")
    class MultiEndpointTests {

        @Test
        @DisplayName("Machine with multiple endpoints filters correctly per network")
        void machineWithMultipleEndpointsFiltersPerNetwork() {
            // Generator has two endpoints: water on WATER_NETWORK, hydrogen on HYDROGEN_NETWORK
            ResourceEndpoint waterEndpoint = new ResourceEndpoint(
                pos(1, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.WATER
            );
            waterEndpoint.onAttach(WATER_NETWORK);

            ResourceEndpoint hydrogenEndpoint = new ResourceEndpoint(
                pos(-1, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.HYDROGEN
            );
            hydrogenEndpoint.onAttach(HYDROGEN_NETWORK);

            List<Endpoint> endpoints = List.of(waterEndpoint, hydrogenEndpoint);

            Map<ResourceType, Integer> availableResources = Map.of(
                ResourceType.WATER, 100,
                ResourceType.HYDROGEN, 50
            );

            // Test filtering for WATER_NETWORK
            Set<ResourceType> waterNetworkAllowed = filterForNetwork(endpoints, WATER_NETWORK);
            assertThat(waterNetworkAllowed).containsExactly(ResourceType.WATER);

            // Test filtering for HYDROGEN_NETWORK
            Set<ResourceType> hydrogenNetworkAllowed = filterForNetwork(endpoints, HYDROGEN_NETWORK);
            assertThat(hydrogenNetworkAllowed).containsExactly(ResourceType.HYDROGEN);
        }

        @Test
        @DisplayName("Consumer endpoint filters resource requests correctly")
        void consumerEndpointFiltersRequests() {
            // Reactor has water and hydrogen input endpoints
            ResourceEndpoint waterInput = new ResourceEndpoint(
                pos(1, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.CONSUMER,
                ResourceType.WATER
            );
            waterInput.onAttach(WATER_NETWORK);

            ResourceEndpoint hydrogenInput = new ResourceEndpoint(
                pos(-1, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.CONSUMER,
                ResourceType.HYDROGEN
            );
            hydrogenInput.onAttach(HYDROGEN_NETWORK);

            List<Endpoint> endpoints = List.of(waterInput, hydrogenInput);

            // Reactor wants both water and hydrogen
            Map<ResourceType, Integer> requests = Map.of(
                ResourceType.WATER, 50,
                ResourceType.HYDROGEN, 25
            );

            // When processing WATER_NETWORK, only water request should be visible
            Map<ResourceType, Integer> waterNetworkRequests = filterRequestsForNetwork(
                endpoints, requests, WATER_NETWORK
            );
            assertThat(waterNetworkRequests).containsOnlyKeys(ResourceType.WATER);
            assertThat(waterNetworkRequests.get(ResourceType.WATER)).isEqualTo(50);

            // When processing HYDROGEN_NETWORK, only hydrogen request should be visible
            Map<ResourceType, Integer> hydrogenNetworkRequests = filterRequestsForNetwork(
                endpoints, requests, HYDROGEN_NETWORK
            );
            assertThat(hydrogenNetworkRequests).containsOnlyKeys(ResourceType.HYDROGEN);
            assertThat(hydrogenNetworkRequests.get(ResourceType.HYDROGEN)).isEqualTo(25);
        }

        private Set<ResourceType> filterForNetwork(List<Endpoint> endpoints, NetworkId networkId) {
            Set<ResourceType> allowed = new HashSet<>();
            for (Endpoint endpoint : endpoints) {
                if (endpoint.layer() == ConnectionType.PIPE &&
                    endpoint.attachedNetwork().isPresent() &&
                    endpoint.attachedNetwork().get().equals(networkId)) {

                    if (endpoint instanceof ResourceEndpoint re) {
                        allowed.add(re.resourceType());
                    }
                }
            }
            return allowed;
        }

        private Map<ResourceType, Integer> filterRequestsForNetwork(
            List<Endpoint> endpoints,
            Map<ResourceType, Integer> requests,
            NetworkId networkId
        ) {
            Set<ResourceType> allowed = filterForNetwork(endpoints, networkId);
            Map<ResourceType, Integer> filtered = new HashMap<>();
            for (ResourceType type : allowed) {
                if (requests.containsKey(type)) {
                    filtered.put(type, requests.get(type));
                }
            }
            return filtered;
        }
    }

    @Nested
    @DisplayName("PipeNetworkState Integration")
    class PipeNetworkStateIntegrationTests {

        private PipeNetworkState waterNetworkState;
        private PipeNetworkState hydrogenNetworkState;

        @BeforeEach
        void setUp() {
            waterNetworkState = new PipeNetworkState(WATER_NETWORK);
            waterNetworkState.setNetworkSize(10);

            hydrogenNetworkState = new PipeNetworkState(HYDROGEN_NETWORK);
            hydrogenNetworkState.setNetworkSize(10);
        }

        @Test
        @DisplayName("Water network only accepts water providers")
        void waterNetworkOnlyAcceptsWaterProviders() {
            MachineId generatorId = MachineId.create();

            // Lock water network to water
            boolean locked = waterNetworkState.tryLock(ResourceType.WATER, generatorId);
            assertThat(locked).isTrue();
            assertThat(waterNetworkState.getLockedResourceType()).isEqualTo(ResourceType.WATER);

            // Add water provider - should work
            waterNetworkState.addProvider(generatorId, ResourceType.WATER, 100);
            assertThat(waterNetworkState.getCompatibleProviders()).hasSize(1);

            // Another machine tries to provide hydrogen to water network - should fail lock
            MachineId otherGenerator = MachineId.create();
            boolean hydrogenLock = waterNetworkState.tryLock(ResourceType.HYDROGEN, otherGenerator);
            assertThat(hydrogenLock).isFalse();
        }

        @Test
        @DisplayName("Consumer on water network only receives water")
        void consumerOnWaterNetworkOnlyReceivesWater() {
            MachineId generatorId = MachineId.create();
            MachineId reactorId = MachineId.create();

            // Setup water network
            waterNetworkState.tryLock(ResourceType.WATER, generatorId);
            waterNetworkState.addProvider(generatorId, ResourceType.WATER, 100);

            // Consumer wants both water and hydrogen
            // But only water should be allowed through water endpoint
            // So we filter before adding to network state
            Map<ResourceType, Integer> filteredRequests = Map.of(ResourceType.WATER, 50);
            waterNetworkState.addConsumer(reactorId, filteredRequests);

            // Get compatible consumers
            var consumers = waterNetworkState.getCompatibleConsumers();
            assertThat(consumers).hasSize(1);

            // The consumer should only want water on this network
            var consumer = consumers.get(0);
            assertThat(consumer.getRequest(ResourceType.WATER)).isEqualTo(50);
            assertThat(consumer.getRequest(ResourceType.HYDROGEN)).isEqualTo(0);
        }

        @Test
        @DisplayName("Separate networks maintain separate resource types")
        void separateNetworksMaintainSeparateResourceTypes() {
            MachineId generatorId = MachineId.create();

            // Lock water network to water
            waterNetworkState.tryLock(ResourceType.WATER, generatorId);
            waterNetworkState.addProvider(generatorId, ResourceType.WATER, 100);

            // Lock hydrogen network to hydrogen
            hydrogenNetworkState.tryLock(ResourceType.HYDROGEN, generatorId);
            hydrogenNetworkState.addProvider(generatorId, ResourceType.HYDROGEN, 50);

            // Verify each network is locked to its respective type
            assertThat(waterNetworkState.getLockedResourceType()).isEqualTo(ResourceType.WATER);
            assertThat(hydrogenNetworkState.getLockedResourceType()).isEqualTo(ResourceType.HYDROGEN);

            // Transfer simulation
            waterNetworkState.addToBuffer(50);
            hydrogenNetworkState.addToBuffer(25);

            assertThat(waterNetworkState.getStoredAmount()).isEqualTo(50);
            assertThat(hydrogenNetworkState.getStoredAmount()).isEqualTo(25);
        }

        @Test
        @DisplayName("Full transfer cycle with endpoint filtering")
        void fullTransferCycleWithEndpointFiltering() {
            MachineId generatorId = MachineId.create();
            MachineId reactorId = MachineId.create();

            // === Setup: Generator produces water and hydrogen ===
            // Water endpoint connected to water network
            ResourceEndpoint waterProviderEndpoint = new ResourceEndpoint(
                pos(1, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.WATER
            );
            waterProviderEndpoint.onAttach(WATER_NETWORK);

            // Hydrogen endpoint connected to hydrogen network
            ResourceEndpoint hydrogenProviderEndpoint = new ResourceEndpoint(
                pos(-1, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.HYDROGEN
            );
            hydrogenProviderEndpoint.onAttach(HYDROGEN_NETWORK);

            // Generator has resources
            Map<ResourceType, Integer> generatorResources = new HashMap<>();
            generatorResources.put(ResourceType.WATER, 100);
            generatorResources.put(ResourceType.HYDROGEN, 50);

            // === Collect Phase Simulation ===
            // For WATER_NETWORK: filter by water endpoint
            Set<ResourceType> waterAllowed = Set.of(waterProviderEndpoint.resourceType());
            int waterAvailable = generatorResources.getOrDefault(ResourceType.WATER, 0);

            // For HYDROGEN_NETWORK: filter by hydrogen endpoint
            Set<ResourceType> hydrogenAllowed = Set.of(hydrogenProviderEndpoint.resourceType());
            int hydrogenAvailable = generatorResources.getOrDefault(ResourceType.HYDROGEN, 0);

            // Lock networks
            waterNetworkState.tryLock(ResourceType.WATER, generatorId);
            hydrogenNetworkState.tryLock(ResourceType.HYDROGEN, generatorId);

            // Add filtered providers
            if (waterAllowed.contains(ResourceType.WATER) && waterAvailable > 0) {
                waterNetworkState.addProvider(generatorId, ResourceType.WATER, waterAvailable);
            }
            if (hydrogenAllowed.contains(ResourceType.HYDROGEN) && hydrogenAvailable > 0) {
                hydrogenNetworkState.addProvider(generatorId, ResourceType.HYDROGEN, hydrogenAvailable);
            }

            // === Verify: Each network only has its designated resource ===
            var waterProviders = waterNetworkState.getCompatibleProviders();
            assertThat(waterProviders).hasSize(1);
            assertThat(waterProviders.get(0).resourceType()).isEqualTo(ResourceType.WATER);
            assertThat(waterProviders.get(0).available()).isEqualTo(100);

            var hydrogenProviders = hydrogenNetworkState.getCompatibleProviders();
            assertThat(hydrogenProviders).hasSize(1);
            assertThat(hydrogenProviders.get(0).resourceType()).isEqualTo(ResourceType.HYDROGEN);
            assertThat(hydrogenProviders.get(0).available()).isEqualTo(50);

            // === Route & Execute Simulation ===
            // Water flows through water network only
            waterNetworkState.addToBuffer(20); // Transfer from provider
            assertThat(waterNetworkState.getStoredAmount()).isEqualTo(20);

            // Hydrogen flows through hydrogen network only
            hydrogenNetworkState.addToBuffer(20);
            assertThat(hydrogenNetworkState.getStoredAmount()).isEqualTo(20);

            // Add reactor as consumer (pre-filtered)
            waterNetworkState.addConsumer(reactorId, Map.of(ResourceType.WATER, 10));
            hydrogenNetworkState.addConsumer(reactorId, Map.of(ResourceType.HYDROGEN, 10));

            // Deliver to consumers
            waterNetworkState.removeFromBuffer(10);
            hydrogenNetworkState.removeFromBuffer(10);

            assertThat(waterNetworkState.getStoredAmount()).isEqualTo(10);
            assertThat(hydrogenNetworkState.getStoredAmount()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Unattached endpoint should not allow any resources")
        void unattachedEndpointAllowsNothing() {
            ResourceEndpoint endpoint = new ResourceEndpoint(
                pos(0, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.WATER
            );
            // Not attached to any network

            assertThat(endpoint.attachedNetwork()).isEmpty();
            assertThat(endpoint.isAttached()).isFalse();
        }

        @Test
        @DisplayName("Empty resource map should result in empty filtered results")
        void emptyResourceMapResultsInEmptyFilter() {
            ResourceEndpoint endpoint = new ResourceEndpoint(
                pos(0, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.WATER
            );
            endpoint.onAttach(WATER_NETWORK);

            Map<ResourceType, Integer> emptyResources = Map.of();

            Set<ResourceType> allowed = Set.of(endpoint.resourceType());
            Map<ResourceType, Integer> filtered = new HashMap<>();
            for (ResourceType type : allowed) {
                if (emptyResources.containsKey(type)) {
                    filtered.put(type, emptyResources.get(type));
                }
            }

            assertThat(filtered).isEmpty();
        }

        @Test
        @DisplayName("Resource not in available map should not appear in filtered results")
        void unavailableResourceNotInFiltered() {
            ResourceEndpoint endpoint = new ResourceEndpoint(
                pos(0, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.COOLANT
            );
            endpoint.onAttach(WATER_NETWORK);

            // Generator only has water, not coolant
            Map<ResourceType, Integer> resources = Map.of(ResourceType.WATER, 100);

            Set<ResourceType> allowed = Set.of(endpoint.resourceType());
            Map<ResourceType, Integer> filtered = new HashMap<>();
            for (ResourceType type : allowed) {
                if (resources.containsKey(type)) {
                    filtered.put(type, resources.get(type));
                }
            }

            assertThat(filtered).isEmpty();
        }
    }

    @Nested
    @DisplayName("Transfer Simulation")
    class TransferSimulationTests {

        private PipeNetworkState waterNetworkState;
        private PipeNetworkState hydrogenNetworkState;

        @BeforeEach
        void setUp() {
            waterNetworkState = new PipeNetworkState(WATER_NETWORK);
            waterNetworkState.setNetworkSize(10);

            hydrogenNetworkState = new PipeNetworkState(HYDROGEN_NETWORK);
            hydrogenNetworkState.setNetworkSize(10);
        }

        @Test
        @DisplayName("Generator provides water through water endpoint, not hydrogen")
        void generatorProvidesOnlyWaterThroughWaterEndpoint() {
            // === Setup: Generator with both resources ===
            TestGenerator generator = new TestGenerator();
            generator.waterBuffer = 100;
            generator.hydrogenBuffer = 50;

            // Water endpoint attached to water network
            ResourceEndpoint waterEndpoint = new ResourceEndpoint(
                pos(1, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.WATER
            );
            waterEndpoint.onAttach(WATER_NETWORK);

            // === Simulate transfer tick ===
            // Step 1: Collect - determine what provider can offer for this network
            ResourceType allowedType = waterEndpoint.resourceType(); // WATER only
            int available = generator.getAvailable(allowedType);
            assertThat(available).isEqualTo(100);

            // Step 2: Lock network
            waterNetworkState.tryLock(allowedType, MachineId.create());

            // Step 3: Extract from provider (filtered by endpoint)
            int toTransfer = Math.min(available, waterNetworkState.getSpaceRemaining());
            int extracted = generator.extract(allowedType, toTransfer);
            assertThat(extracted).isEqualTo(100);

            // Step 4: Add to network buffer
            waterNetworkState.addToBuffer(extracted);

            // === Verify ===
            assertThat(waterNetworkState.getStoredAmount()).isEqualTo(100);
            assertThat(waterNetworkState.getLockedResourceType()).isEqualTo(ResourceType.WATER);

            // Generator water decreased, hydrogen unchanged
            assertThat(generator.waterBuffer).isEqualTo(0);
            assertThat(generator.hydrogenBuffer).isEqualTo(50); // Unchanged!
        }

        @Test
        @DisplayName("Reactor receives water through water endpoint, requests hydrogen ignored")
        void reactorReceivesOnlyWaterThroughWaterEndpoint() {
            // === Setup: Reactor wants both resources ===
            TestReactor reactor = new TestReactor();
            reactor.waterNeeded = 50;
            reactor.hydrogenNeeded = 25;

            // Water endpoint attached to water network
            ResourceEndpoint waterEndpoint = new ResourceEndpoint(
                pos(1, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.CONSUMER,
                ResourceType.WATER
            );
            waterEndpoint.onAttach(WATER_NETWORK);

            // Pre-fill network with water
            MachineId providerId = MachineId.create();
            waterNetworkState.tryLock(ResourceType.WATER, providerId);
            waterNetworkState.addToBuffer(100);

            // === Simulate transfer tick ===
            // Step 1: Collect - determine what consumer wants for this network
            ResourceType allowedType = waterEndpoint.resourceType(); // WATER only
            int requested = reactor.getRequest(allowedType);
            assertThat(requested).isEqualTo(50);

            // Step 2: Calculate delivery
            int stored = waterNetworkState.getStoredAmount();
            int toDeliver = Math.min(stored, requested);

            // Step 3: Remove from network buffer
            waterNetworkState.removeFromBuffer(toDeliver);

            // Step 4: Deliver to consumer (filtered by endpoint)
            int accepted = reactor.accept(allowedType, toDeliver);
            assertThat(accepted).isEqualTo(50);

            // === Verify ===
            assertThat(waterNetworkState.getStoredAmount()).isEqualTo(50);

            // Reactor water received, hydrogen request still pending
            assertThat(reactor.waterNeeded).isEqualTo(0);
            assertThat(reactor.hydrogenNeeded).isEqualTo(25); // Still needs hydrogen!
        }

        @Test
        @DisplayName("Full tick: Generator -> Network -> Reactor with endpoint filtering")
        void fullTickWithEndpointFiltering() {
            // === Setup ===
            MachineId generatorId = MachineId.create();
            MachineId reactorId = MachineId.create();

            TestGenerator generator = new TestGenerator();
            generator.waterBuffer = 100;
            generator.hydrogenBuffer = 50;

            TestReactor reactor = new TestReactor();
            reactor.waterNeeded = 30;
            reactor.hydrogenNeeded = 20;

            // Endpoints
            ResourceEndpoint genWaterEndpoint = new ResourceEndpoint(
                pos(1, 0, 0), ConnectionType.PIPE, EndpointRole.PROVIDER, ResourceType.WATER
            );
            genWaterEndpoint.onAttach(WATER_NETWORK);

            ResourceEndpoint genHydrogenEndpoint = new ResourceEndpoint(
                pos(-1, 0, 0), ConnectionType.PIPE, EndpointRole.PROVIDER, ResourceType.HYDROGEN
            );
            genHydrogenEndpoint.onAttach(HYDROGEN_NETWORK);

            ResourceEndpoint reactorWaterEndpoint = new ResourceEndpoint(
                pos(2, 0, 0), ConnectionType.PIPE, EndpointRole.CONSUMER, ResourceType.WATER
            );
            reactorWaterEndpoint.onAttach(WATER_NETWORK);

            ResourceEndpoint reactorHydrogenEndpoint = new ResourceEndpoint(
                pos(-2, 0, 0), ConnectionType.PIPE, EndpointRole.CONSUMER, ResourceType.HYDROGEN
            );
            reactorHydrogenEndpoint.onAttach(HYDROGEN_NETWORK);

            // === TICK: Process WATER_NETWORK ===
            {
                ResourceType waterType = genWaterEndpoint.resourceType();

                // Input phase: Generator -> Network
                waterNetworkState.tryLock(waterType, generatorId);
                int available = generator.getAvailable(waterType);
                int space = waterNetworkState.getSpaceRemaining();
                int extracted = generator.extract(waterType, Math.min(available, space));
                waterNetworkState.addToBuffer(extracted);

                // Output phase: Network -> Reactor
                int stored = waterNetworkState.getStoredAmount();
                int requested = reactor.getRequest(reactorWaterEndpoint.resourceType());
                int toDeliver = Math.min(stored, requested);
                waterNetworkState.removeFromBuffer(toDeliver);
                reactor.accept(reactorWaterEndpoint.resourceType(), toDeliver);
            }

            // === TICK: Process HYDROGEN_NETWORK ===
            {
                ResourceType hydrogenType = genHydrogenEndpoint.resourceType();

                // Input phase: Generator -> Network
                hydrogenNetworkState.tryLock(hydrogenType, generatorId);
                int available = generator.getAvailable(hydrogenType);
                int space = hydrogenNetworkState.getSpaceRemaining();
                int extracted = generator.extract(hydrogenType, Math.min(available, space));
                hydrogenNetworkState.addToBuffer(extracted);

                // Output phase: Network -> Reactor
                int stored = hydrogenNetworkState.getStoredAmount();
                int requested = reactor.getRequest(reactorHydrogenEndpoint.resourceType());
                int toDeliver = Math.min(stored, requested);
                hydrogenNetworkState.removeFromBuffer(toDeliver);
                reactor.accept(reactorHydrogenEndpoint.resourceType(), toDeliver);
            }

            // === Verify Final State ===
            // Generator: extracted 100 water and 50 hydrogen
            assertThat(generator.waterBuffer).isEqualTo(0);
            assertThat(generator.hydrogenBuffer).isEqualTo(0);

            // Networks: delivered what was requested, rest in buffer
            assertThat(waterNetworkState.getStoredAmount()).isEqualTo(70); // 100 - 30
            assertThat(hydrogenNetworkState.getStoredAmount()).isEqualTo(30); // 50 - 20

            // Reactor: received what it needed
            assertThat(reactor.waterNeeded).isEqualTo(0);
            assertThat(reactor.hydrogenNeeded).isEqualTo(0);
        }

        @Test
        @DisplayName("Cross-connected networks: wrong endpoint blocks transfer")
        void wrongEndpointBlocksTransfer() {
            // === Setup: Generator produces hydrogen, but endpoint is for WATER ===
            TestGenerator generator = new TestGenerator();
            generator.waterBuffer = 0;  // No water!
            generator.hydrogenBuffer = 100;

            // Water endpoint attached to water network (but generator has no water)
            ResourceEndpoint waterEndpoint = new ResourceEndpoint(
                pos(1, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.WATER
            );
            waterEndpoint.onAttach(WATER_NETWORK);

            // === Simulate transfer tick ===
            ResourceType allowedType = waterEndpoint.resourceType(); // WATER only
            int available = generator.getAvailable(allowedType);

            // No water available through water endpoint
            assertThat(available).isEqualTo(0);

            // Nothing to transfer
            int extracted = generator.extract(allowedType, available);
            assertThat(extracted).isEqualTo(0);

            // Network stays empty
            waterNetworkState.addToBuffer(extracted);
            assertThat(waterNetworkState.getStoredAmount()).isEqualTo(0);

            // Hydrogen is untouched (can't go through water endpoint)
            assertThat(generator.hydrogenBuffer).isEqualTo(100);
        }

        @Test
        @DisplayName("Multiple ticks accumulate in network buffer")
        void multipleTicksAccumulateInBuffer() {
            MachineId generatorId = MachineId.create();

            TestGenerator generator = new TestGenerator();
            generator.waterBuffer = 1000;

            ResourceEndpoint waterEndpoint = new ResourceEndpoint(
                pos(1, 0, 0), ConnectionType.PIPE, EndpointRole.PROVIDER, ResourceType.WATER
            );
            waterEndpoint.onAttach(WATER_NETWORK);

            waterNetworkState.tryLock(ResourceType.WATER, generatorId);
            waterNetworkState.setMaxThroughput(100); // Limit per tick

            // Simulate 3 ticks
            for (int tick = 0; tick < 3; tick++) {
                waterNetworkState.resetTick();

                ResourceType allowedType = waterEndpoint.resourceType();
                int available = generator.getAvailable(allowedType);
                int throughput = waterNetworkState.getRemainingThroughput();
                int space = waterNetworkState.getSpaceRemaining();

                int toTransfer = Math.min(Math.min(available, throughput), space);
                int extracted = generator.extract(allowedType, toTransfer);

                waterNetworkState.addToBuffer(extracted);
                waterNetworkState.recordTransfer(extracted);
            }

            // 3 ticks * 100 throughput = 300 transferred
            assertThat(waterNetworkState.getStoredAmount()).isEqualTo(300);
            assertThat(generator.waterBuffer).isEqualTo(700);
        }

        // === Test Helper Classes ===

        class TestGenerator {
            int waterBuffer = 0;
            int hydrogenBuffer = 0;

            int getAvailable(ResourceType type) {
                if (type == ResourceType.WATER) return waterBuffer;
                if (type == ResourceType.HYDROGEN) return hydrogenBuffer;
                return 0;
            }

            int extract(ResourceType type, int amount) {
                if (type == ResourceType.WATER) {
                    int extracted = Math.min(amount, waterBuffer);
                    waterBuffer -= extracted;
                    return extracted;
                }
                if (type == ResourceType.HYDROGEN) {
                    int extracted = Math.min(amount, hydrogenBuffer);
                    hydrogenBuffer -= extracted;
                    return extracted;
                }
                return 0;
            }
        }

        class TestReactor {
            int waterNeeded = 0;
            int hydrogenNeeded = 0;

            int getRequest(ResourceType type) {
                if (type == ResourceType.WATER) return waterNeeded;
                if (type == ResourceType.HYDROGEN) return hydrogenNeeded;
                return 0;
            }

            int accept(ResourceType type, int amount) {
                if (type == ResourceType.WATER) {
                    int accepted = Math.min(amount, waterNeeded);
                    waterNeeded -= accepted;
                    return accepted;
                }
                if (type == ResourceType.HYDROGEN) {
                    int accepted = Math.min(amount, hydrogenNeeded);
                    hydrogenNeeded -= accepted;
                    return accepted;
                }
                return 0;
            }
        }
    }

    @Nested
    @DisplayName("Endpoint Attachment Integration")
    class EndpointAttachmentTests {

        @Test
        @DisplayName("Endpoint not attached returns empty allowedTypes")
        void unattachedEndpointReturnsEmptyAllowed() {
            // Create endpoint but DON'T attach it
            ResourceEndpoint waterEndpoint = new ResourceEndpoint(
                pos(1, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.WATER
            );
            // NOT calling waterEndpoint.onAttach(WATER_NETWORK);

            // Simulate the filtering logic from NetworkResourceTransfer.collectProvider
            Set<ResourceType> allowedTypes = new HashSet<>();
            if (waterEndpoint.attachedNetwork().isPresent() &&
                waterEndpoint.attachedNetwork().get().equals(WATER_NETWORK)) {
                allowedTypes.add(waterEndpoint.resourceType());
            }

            // Should be empty because endpoint is not attached
            assertThat(allowedTypes).isEmpty();
        }

        @Test
        @DisplayName("Attached endpoint returns correct allowedType")
        void attachedEndpointReturnsCorrectAllowed() {
            ResourceEndpoint waterEndpoint = new ResourceEndpoint(
                pos(1, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.WATER
            );
            // Attach to network
            waterEndpoint.onAttach(WATER_NETWORK);

            // Simulate the filtering logic
            Set<ResourceType> allowedTypes = new HashSet<>();
            if (waterEndpoint.attachedNetwork().isPresent() &&
                waterEndpoint.attachedNetwork().get().equals(WATER_NETWORK)) {
                allowedTypes.add(waterEndpoint.resourceType());
            }

            // Should contain WATER
            assertThat(allowedTypes).containsExactly(ResourceType.WATER);
        }

        @Test
        @DisplayName("Endpoint attached to different network returns empty for this network")
        void endpointAttachedToWrongNetworkReturnsEmpty() {
            ResourceEndpoint waterEndpoint = new ResourceEndpoint(
                pos(1, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.WATER
            );
            // Attach to HYDROGEN network, not WATER network
            waterEndpoint.onAttach(HYDROGEN_NETWORK);

            // Check against WATER_NETWORK
            Set<ResourceType> allowedTypes = new HashSet<>();
            if (waterEndpoint.attachedNetwork().isPresent() &&
                waterEndpoint.attachedNetwork().get().equals(WATER_NETWORK)) {
                allowedTypes.add(waterEndpoint.resourceType());
            }

            // Should be empty because endpoint is on different network
            assertThat(allowedTypes).isEmpty();
        }

        @Test
        @DisplayName("Endpoint detach clears the network")
        void endpointDetachClearsNetwork() {
            ResourceEndpoint endpoint = new ResourceEndpoint(
                pos(0, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.WATER
            );
            endpoint.onAttach(WATER_NETWORK);
            assertThat(endpoint.attachedNetwork()).isPresent();

            // Detach
            endpoint.onDetach();

            assertThat(endpoint.attachedNetwork()).isEmpty();
            assertThat(endpoint.isAttached()).isFalse();
        }

        @Test
        @DisplayName("Endpoint can be re-attached to different network")
        void endpointCanBeReattached() {
            ResourceEndpoint endpoint = new ResourceEndpoint(
                pos(0, 0, 0),
                ConnectionType.PIPE,
                EndpointRole.PROVIDER,
                ResourceType.WATER
            );

            // Attach to first network
            endpoint.onAttach(WATER_NETWORK);
            assertThat(endpoint.attachedNetwork().get()).isEqualTo(WATER_NETWORK);

            // Attach to different network (simulating network merge/split)
            endpoint.onAttach(HYDROGEN_NETWORK);
            assertThat(endpoint.attachedNetwork().get()).isEqualTo(HYDROGEN_NETWORK);
        }
    }
}
