package dev.kate.erd.core.dataplane;

import dev.kate.erd.core.controller.ControllerInstance;
import dev.kate.erd.core.engine.NetworkEngine;
import dev.kate.erd.core.engine.NetworkEventListener;
import dev.kate.erd.core.machine.MachineInstance;
import dev.kate.erd.core.model.*;
import dev.kate.erd.core.topology.TopologyResult;
import dev.kate.erd.core.util.Clock;
import dev.kate.erd.core.util.ErdLogger;

import java.util.*;

/**
 * The DATA Control Plane manages mainframe leadership, machine/controller
 * registries, and bindings across all DATA networks.
 *
 * <p>For each DATA network, the control plane:
 * <ul>
 *   <li>Discovers mainframes and elects a leader</li>
 *   <li>Maintains registries of machines and controllers</li>
 *   <li>Manages bindings with constraint enforcement</li>
 *   <li>Handles network split/merge topology changes</li>
 * </ul>
 *
 * <p>If there is no available mainframe leader for a DATA network, all
 * bindings become inactive and controllers show "NO SIGNAL".
 *
 * <p>Thread-safety: NOT thread-safe. Use on processing thread only.
 */
public final class DataControlPlane implements NetworkEventListener {

    private final ErdLogger logger;
    private final Clock clock;
    private final NetworkEngine networkEngine;

    // Registry per DATA network
    private final Map<NetworkId, NetworkRegistry> registries = new HashMap<>();

    // Reverse lookups: entity -> network
    private final Map<ControllerId, NetworkId> controllerToNetwork = new HashMap<>();
    private final Map<MachineId, NetworkId> machineToNetwork = new HashMap<>();

    // Listeners for control plane events
    private final List<ControlPlaneListener> listeners = new ArrayList<>();

    /**
     * Creates a new DATA control plane.
     *
     * @param logger the logger
     * @param clock the clock
     * @param networkEngine the network engine (for DATA type queries)
     */
    public DataControlPlane(ErdLogger logger, Clock clock, NetworkEngine networkEngine) {
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.networkEngine = Objects.requireNonNull(networkEngine, "networkEngine must not be null");

        // Register as listener for DATA network topology changes
        networkEngine.addListener(this);
    }

    // ========== Controller/Machine Registration ==========

    /**
     * Registers a controller on its DATA network.
     * The controller is found by checking which DATA network its DATA endpoint
     * is attached to.
     *
     * @param controller the controller instance
     * @param dataNetworkId the DATA network ID the controller is on
     */
    public void registerController(ControllerInstance controller, NetworkId dataNetworkId) {
        Objects.requireNonNull(controller, "controller must not be null");
        Objects.requireNonNull(dataNetworkId, "dataNetworkId must not be null");

        ControllerId id = controller.id();

        // Get or create registry
        NetworkRegistry registry = registries.computeIfAbsent(
            dataNetworkId, NetworkRegistry::new);

        // Register controller
        boolean isMainframe = controller.definition().isMainframe();
        registry.registerController(controller, isMainframe);
        controllerToNetwork.put(id, dataNetworkId);

        // Notify controller of connection status
        if (registry.hasLeader()) {
            controller.onDataConnectionEstablished();
        } else {
            controller.onDataConnectionLost();
        }

        logger.info("Registered controller %s on DATA network %s (mainframe=%s)",
            id, dataNetworkId, isMainframe);

        // Notify listeners
        notifyControllerRegistered(controller, dataNetworkId);
    }

    /**
     * Unregisters a controller from the control plane.
     *
     * @param controllerId the controller ID
     */
    public void unregisterController(ControllerId controllerId) {
        NetworkId networkId = controllerToNetwork.remove(controllerId);
        if (networkId == null) return;

        NetworkRegistry registry = registries.get(networkId);
        if (registry != null) {
            registry.unregisterController(controllerId);

            // Clean up empty registry
            if (registry.allControllerIds().isEmpty() && registry.allMachineIds().isEmpty()) {
                registries.remove(networkId);
            }
        }

        logger.info("Unregistered controller %s from DATA network %s", controllerId, networkId);
    }

    /**
     * Registers a machine on its DATA network.
     *
     * @param machine the machine instance
     * @param dataNetworkId the DATA network ID
     */
    public void registerMachine(MachineInstance machine, NetworkId dataNetworkId) {
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(dataNetworkId, "dataNetworkId must not be null");

        MachineId id = machine.id();

        NetworkRegistry registry = registries.computeIfAbsent(
            dataNetworkId, NetworkRegistry::new);

        registry.registerMachine(machine);
        machineToNetwork.put(id, dataNetworkId);

        logger.info("Registered machine %s on DATA network %s", id, dataNetworkId);

        notifyMachineRegistered(machine, dataNetworkId);
    }

    /**
     * Unregisters a machine from the control plane.
     *
     * @param machineId the machine ID
     */
    public void unregisterMachine(MachineId machineId) {
        NetworkId networkId = machineToNetwork.remove(machineId);
        if (networkId == null) return;

        NetworkRegistry registry = registries.get(networkId);
        if (registry != null) {
            registry.unregisterMachine(machineId);

            if (registry.allControllerIds().isEmpty() && registry.allMachineIds().isEmpty()) {
                registries.remove(networkId);
            }
        }

        logger.info("Unregistered machine %s from DATA network %s", machineId, networkId);
    }

    // ========== Mainframe/Leader Operations ==========

    /**
     * Gets the current mainframe leader for a DATA network.
     *
     * @param networkId the network ID
     * @return the leader controller ID, or empty if no leader
     */
    public Optional<ControllerId> getLeader(NetworkId networkId) {
        NetworkRegistry registry = registries.get(networkId);
        return registry != null ? registry.currentLeader() : Optional.empty();
    }

    /**
     * Checks if a DATA network has an available leader.
     *
     * @param networkId the network ID
     * @return true if leader is available
     */
    public boolean hasLeader(NetworkId networkId) {
        NetworkRegistry registry = registries.get(networkId);
        return registry != null && registry.hasLeader();
    }

    /**
     * Marks a mainframe as available or unavailable.
     *
     * @param controllerId the mainframe controller ID
     * @param available whether available
     */
    public void setMainframeAvailable(ControllerId controllerId, boolean available) {
        NetworkId networkId = controllerToNetwork.get(controllerId);
        if (networkId == null) return;

        NetworkRegistry registry = registries.get(networkId);
        if (registry == null) return;

        boolean hadLeader = registry.hasLeader();
        registry.setMainframeAvailable(controllerId, available);
        boolean hasLeader = registry.hasLeader();

        // Notify controllers if leader status changed
        if (hadLeader != hasLeader) {
            notifyLeaderStatusChanged(networkId, hasLeader);
        }
    }

    // ========== Binding Operations ==========

    /**
     * Creates a binding between a controller and machine.
     * This is the explicit UX: player chooses machine and assigns controller.
     *
     * @param controllerId the controller to bind
     * @param machineId the machine to bind to
     * @return the binding result
     */
    public BindingOperationResult createBinding(ControllerId controllerId, MachineId machineId) {
        NetworkId controllerNetwork = controllerToNetwork.get(controllerId);
        NetworkId machineNetwork = machineToNetwork.get(machineId);

        if (controllerNetwork == null) {
            return new BindingOperationResult.Failure("Controller not registered");
        }
        if (machineNetwork == null) {
            return new BindingOperationResult.Failure("Machine not registered");
        }
        if (!controllerNetwork.equals(machineNetwork)) {
            return new BindingOperationResult.Failure(
                "Controller and machine are on different DATA networks");
        }

        NetworkRegistry registry = registries.get(controllerNetwork);
        if (registry == null) {
            return new BindingOperationResult.Failure("Network registry not found");
        }

        if (!registry.hasLeader()) {
            return new BindingOperationResult.Failure(
                "No mainframe leader available - bindings are inactive");
        }

        var result = registry.createBinding(controllerId, machineId, clock.nowMillis());

        if (result instanceof NetworkRegistry.BindingResult.Success success) {
            logger.info("Created binding: controller %s -> machine %s", controllerId, machineId);
            notifyBindingCreated(success.binding());
            return new BindingOperationResult.Success(success.binding());
        } else if (result instanceof NetworkRegistry.BindingResult.Failure failure) {
            return new BindingOperationResult.Failure(failure.reason());
        }

        return new BindingOperationResult.Failure("Unknown error");
    }

    /**
     * Removes a binding.
     *
     * @param bindingId the binding ID
     * @return true if removed
     */
    public boolean removeBinding(BindingId bindingId) {
        for (NetworkRegistry registry : registries.values()) {
            if (registry.removeBinding(bindingId)) {
                logger.info("Removed binding %s", bindingId);
                notifyBindingRemoved(bindingId);
                return true;
            }
        }
        return false;
    }

    /**
     * Gets all bindings for a machine.
     *
     * @param machineId the machine ID
     * @return list of bindings
     */
    public List<Binding> getBindingsForMachine(MachineId machineId) {
        NetworkId networkId = machineToNetwork.get(machineId);
        if (networkId == null) return List.of();

        NetworkRegistry registry = registries.get(networkId);
        return registry != null ? registry.getBindingsForMachine(machineId) : List.of();
    }

    /**
     * Gets all bindings for a controller.
     *
     * @param controllerId the controller ID
     * @return list of bindings
     */
    public List<Binding> getBindingsForController(ControllerId controllerId) {
        NetworkId networkId = controllerToNetwork.get(controllerId);
        if (networkId == null) return List.of();

        NetworkRegistry registry = registries.get(networkId);
        return registry != null ? registry.getBindingsForController(controllerId) : List.of();
    }

    // ========== Queries ==========

    /**
     * Gets the DATA network a controller is on.
     *
     * @param controllerId the controller ID
     * @return the network ID, or empty if not registered
     */
    public Optional<NetworkId> getControllerNetwork(ControllerId controllerId) {
        return Optional.ofNullable(controllerToNetwork.get(controllerId));
    }

    /**
     * Gets the DATA network a machine is on.
     *
     * @param machineId the machine ID
     * @return the network ID, or empty if not registered
     */
    public Optional<NetworkId> getMachineNetwork(MachineId machineId) {
        return Optional.ofNullable(machineToNetwork.get(machineId));
    }

    /**
     * Gets all machines registered on a DATA network.
     *
     * @param networkId the network ID
     * @return set of machine IDs
     */
    public Set<MachineId> getMachinesOnNetwork(NetworkId networkId) {
        NetworkRegistry registry = registries.get(networkId);
        return registry != null ? registry.allMachineIds() : Set.of();
    }

    /**
     * Gets all controllers registered on a DATA network.
     *
     * @param networkId the network ID
     * @return set of controller IDs
     */
    public Set<ControllerId> getControllersOnNetwork(NetworkId networkId) {
        NetworkRegistry registry = registries.get(networkId);
        return registry != null ? registry.allControllerIds() : Set.of();
    }

    /**
     * Gets the binding status for display purposes.
     *
     * @param controllerId the controller ID
     * @return the status
     */
    public ControllerBindingStatus getControllerStatus(ControllerId controllerId) {
        NetworkId networkId = controllerToNetwork.get(controllerId);
        if (networkId == null) {
            return ControllerBindingStatus.UNREGISTERED;
        }

        NetworkRegistry registry = registries.get(networkId);
        if (registry == null || !registry.hasLeader()) {
            return ControllerBindingStatus.NO_SIGNAL;
        }

        List<Binding> bindings = registry.getBindingsForController(controllerId);
        if (bindings.isEmpty()) {
            return ControllerBindingStatus.UNASSIGNED;
        }

        return ControllerBindingStatus.BOUND;
    }

    // ========== Network Topology Events ==========

    @Override
    public void onTopologyChanged(ConnectionType layer, TopologyResult result) {
        if (layer != ConnectionType.DATA) {
            return; // Only care about DATA type changes
        }

        switch (result) {
            case TopologyResult.NetworksMerged merged -> handleNetworkMerge(merged);
            case TopologyResult.NetworkSplit split -> handleNetworkSplit(split);
            case TopologyResult.NetworkDissolved dissolved -> handleNetworkDissolved(dissolved);
            default -> {} // Other events don't affect control plane
        }
    }

    private void handleNetworkMerge(TopologyResult.NetworksMerged merged) {
        NetworkId primary = merged.primaryNetworkId();

        NetworkRegistry primaryRegistry = registries.computeIfAbsent(
            primary, NetworkRegistry::new);

        for (NetworkId mergedId : merged.mergedNetworkIds()) {
            NetworkRegistry mergedRegistry = registries.remove(mergedId);
            if (mergedRegistry != null) {
                // Update reverse lookups
                for (ControllerId cid : mergedRegistry.allControllerIds()) {
                    controllerToNetwork.put(cid, primary);
                }
                for (MachineId mid : mergedRegistry.allMachineIds()) {
                    machineToNetwork.put(mid, primary);
                }

                // Merge registry
                primaryRegistry.mergeFrom(mergedRegistry);
            }
        }

        logger.info("Merged DATA networks %s into %s", merged.mergedNetworkIds(), primary);

        // Notify of leader status
        notifyLeaderStatusChanged(primary, primaryRegistry.hasLeader());
    }

    private void handleNetworkSplit(TopologyResult.NetworkSplit split) {
        NetworkId originalId = split.originalNetworkId();
        NetworkRegistry originalRegistry = registries.get(originalId);

        if (originalRegistry == null) {
            return; // No registry for this network
        }

        // For each component, determine which entities belong to it
        // This requires checking which network each entity's endpoint is now on
        // For now, we'll need to query the engine for each entity's new network

        for (var component : split.resultingComponents()) {
            if (component.retainsOriginalId()) {
                continue; // Original registry stays
            }

            NetworkId newNetworkId = component.networkId();
            Set<BlockPos> componentPositions = component.positions();

            // Find controllers/machines whose DATA endpoints are in this component
            Set<ControllerId> controllerIdsInComponent = new HashSet<>();
            Set<MachineId> machineIdsInComponent = new HashSet<>();

            // Check each controller
            for (ControllerId cid : new HashSet<>(originalRegistry.allControllerIds())) {
                // Check if any of controller's positions are in this component
                // (simplified - in practice need to check actual endpoint positions)
                // For now, we'll use position-based check
            }

            // Create new registry with split entities
            if (!controllerIdsInComponent.isEmpty() || !machineIdsInComponent.isEmpty()) {
                NetworkRegistry newRegistry = originalRegistry.splitTo(
                    newNetworkId, controllerIdsInComponent, machineIdsInComponent);
                registries.put(newNetworkId, newRegistry);

                // Update reverse lookups
                for (ControllerId cid : controllerIdsInComponent) {
                    controllerToNetwork.put(cid, newNetworkId);
                }
                for (MachineId mid : machineIdsInComponent) {
                    machineToNetwork.put(mid, newNetworkId);
                }
            }
        }

        logger.info("Split DATA network %s into %d components",
            originalId, split.resultingComponents().size());
    }

    private void handleNetworkDissolved(TopologyResult.NetworkDissolved dissolved) {
        NetworkId networkId = dissolved.networkId();
        NetworkRegistry registry = registries.remove(networkId);

        if (registry != null) {
            // Clear reverse lookups
            for (ControllerId cid : registry.allControllerIds()) {
                controllerToNetwork.remove(cid);
            }
            for (MachineId mid : registry.allMachineIds()) {
                machineToNetwork.remove(mid);
            }

            // Notify controllers of lost connection
            notifyLeaderStatusChanged(networkId, false);
        }

        logger.info("Dissolved DATA network %s registry", networkId);
    }

    // ========== Listeners ==========

    /**
     * Registers a control plane event listener.
     *
     * @param listener the listener
     */
    public void addListener(ControlPlaneListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    /**
     * Removes a listener.
     *
     * @param listener the listener
     */
    public void removeListener(ControlPlaneListener listener) {
        listeners.remove(listener);
    }

    private void notifyControllerRegistered(ControllerInstance controller, NetworkId networkId) {
        for (ControlPlaneListener l : listeners) {
            try {
                l.onControllerRegistered(controller, networkId);
            } catch (Exception e) {
                logger.error("Error in control plane listener", e);
            }
        }
    }

    private void notifyMachineRegistered(MachineInstance machine, NetworkId networkId) {
        for (ControlPlaneListener l : listeners) {
            try {
                l.onMachineRegistered(machine, networkId);
            } catch (Exception e) {
                logger.error("Error in control plane listener", e);
            }
        }
    }

    private void notifyBindingCreated(Binding binding) {
        for (ControlPlaneListener l : listeners) {
            try {
                l.onBindingCreated(binding);
            } catch (Exception e) {
                logger.error("Error in control plane listener", e);
            }
        }
    }

    private void notifyBindingRemoved(BindingId bindingId) {
        for (ControlPlaneListener l : listeners) {
            try {
                l.onBindingRemoved(bindingId);
            } catch (Exception e) {
                logger.error("Error in control plane listener", e);
            }
        }
    }

    private void notifyLeaderStatusChanged(NetworkId networkId, boolean hasLeader) {
        for (ControlPlaneListener l : listeners) {
            try {
                l.onLeaderStatusChanged(networkId, hasLeader);
            } catch (Exception e) {
                logger.error("Error in control plane listener", e);
            }
        }
    }

    // ========== Result Types ==========

    /**
     * Result of a binding operation.
     */
    public sealed interface BindingOperationResult {
        record Success(Binding binding) implements BindingOperationResult {}
        record Failure(String reason) implements BindingOperationResult {}
    }

    /**
     * Status of a controller's binding state.
     */
    public enum ControllerBindingStatus {
        /** Controller is not registered on any DATA network */
        UNREGISTERED,
        /** Controller's DATA network has no mainframe leader */
        NO_SIGNAL,
        /** Controller is registered but has no machine bindings */
        UNASSIGNED,
        /** Controller is bound to one or more machines */
        BOUND
    }
}
