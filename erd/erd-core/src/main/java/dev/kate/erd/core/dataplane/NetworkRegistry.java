package dev.kate.erd.core.dataplane;

import dev.kate.erd.core.controller.ControllerInstance;
import dev.kate.erd.core.machine.MachineInstance;
import dev.kate.erd.core.model.ControllerId;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.model.NetworkId;

import java.util.*;

/**
 * Represents the state of a single DATA network's control plane.
 *
 * <p>Each DATA network has its own registry containing mainframes, machines,
 * controllers, and bindings. The registry manages leader election and
 * binding constraints.
 *
 * <p>Thread-safety: NOT thread-safe. Use on processing thread only.
 */
public final class NetworkRegistry {

    private final NetworkId networkId;

    // Registered entities on this network
    private final Map<ControllerId, RegisteredController> controllers = new HashMap<>();
    private final Map<MachineId, RegisteredMachine> machines = new HashMap<>();

    // Mainframe tracking (subset of controllers that are mainframes)
    private final Set<ControllerId> mainframeIds = new HashSet<>();
    private ControllerId currentLeader = null;

    // Bindings stored against network
    private final Map<BindingId, Binding> bindings = new HashMap<>();
    private final Map<MachineId, Set<BindingId>> bindingsByMachine = new HashMap<>();
    private final Map<ControllerId, Set<BindingId>> bindingsByController = new HashMap<>();

    /**
     * Creates a new network registry.
     *
     * @param networkId the DATA network ID
     */
    public NetworkRegistry(NetworkId networkId) {
        this.networkId = Objects.requireNonNull(networkId, "networkId must not be null");
    }

    /**
     * @return the network ID
     */
    public NetworkId networkId() {
        return networkId;
    }

    // ========== Controller/Machine Registration ==========

    /**
     * Registers a controller on this network.
     *
     * @param controller the controller instance
     * @param isMainframe whether this controller is a mainframe
     */
    public void registerController(ControllerInstance controller, boolean isMainframe) {
        Objects.requireNonNull(controller, "controller must not be null");
        ControllerId id = controller.id();

        controllers.put(id, new RegisteredController(id, controller.createdAt(), isMainframe));

        if (isMainframe) {
            mainframeIds.add(id);
            reelectLeader();
        }
    }

    /**
     * Unregisters a controller from this network.
     *
     * @param controllerId the controller ID
     */
    public void unregisterController(ControllerId controllerId) {
        RegisteredController reg = controllers.remove(controllerId);
        if (reg == null) return;

        // Remove all bindings involving this controller
        Set<BindingId> controllerBindings = bindingsByController.remove(controllerId);
        if (controllerBindings != null) {
            for (BindingId bindingId : controllerBindings) {
                removeBindingInternal(bindingId);
            }
        }

        if (mainframeIds.remove(controllerId)) {
            if (Objects.equals(currentLeader, controllerId)) {
                currentLeader = null;
                reelectLeader();
            }
        }
    }

    /**
     * Registers a machine on this network.
     *
     * @param machine the machine instance
     */
    public void registerMachine(MachineInstance machine) {
        Objects.requireNonNull(machine, "machine must not be null");
        MachineId id = machine.id();

        machines.put(id, new RegisteredMachine(
            id,
            machine.definition().typeId(),
            machine.definition().maxControllers()
        ));
    }

    /**
     * Unregisters a machine from this network.
     *
     * @param machineId the machine ID
     */
    public void unregisterMachine(MachineId machineId) {
        machines.remove(machineId);

        // Remove all bindings involving this machine
        Set<BindingId> machineBindings = bindingsByMachine.remove(machineId);
        if (machineBindings != null) {
            for (BindingId bindingId : machineBindings) {
                removeBindingInternal(bindingId);
            }
        }
    }

    // ========== Leader Election ==========

    /**
     * @return the current mainframe leader, or empty if none
     */
    public Optional<ControllerId> currentLeader() {
        return Optional.ofNullable(currentLeader);
    }

    /**
     * @return true if there is an available mainframe leader
     */
    public boolean hasLeader() {
        return currentLeader != null;
    }

    /**
     * Marks a mainframe as available or unavailable.
     * Triggers leader re-election if needed.
     *
     * @param controllerId the mainframe ID
     * @param available whether the mainframe is available
     */
    public void setMainframeAvailable(ControllerId controllerId, boolean available) {
        RegisteredController reg = controllers.get(controllerId);
        if (reg == null || !reg.isMainframe) return;

        controllers.put(controllerId, reg.withAvailable(available));

        if (!available && Objects.equals(currentLeader, controllerId)) {
            currentLeader = null;
            reelectLeader();
        } else if (available) {
            // Always re-elect when a mainframe becomes available
            // This ensures earliest createdAt always wins
            reelectLeader();
        }
    }

    /**
     * Performs leader election among available mainframes.
     * Earliest createdAt wins. If current leader is unavailable, failover.
     */
    private void reelectLeader() {
        currentLeader = mainframeIds.stream()
            .map(controllers::get)
            .filter(Objects::nonNull)
            .filter(RegisteredController::available)
            .min(Comparator.comparingLong(RegisteredController::createdAt))
            .map(RegisteredController::id)
            .orElse(null);
    }

    // ========== Bindings ==========

    /**
     * Creates a binding between a controller and machine.
     *
     * @param controllerId the controller
     * @param machineId the machine
     * @param createdAt creation timestamp
     * @return the binding result
     */
    public BindingResult createBinding(ControllerId controllerId, MachineId machineId, long createdAt) {
        // Validate controller exists
        RegisteredController controller = controllers.get(controllerId);
        if (controller == null) {
            return new BindingResult.Failure("Controller not registered on this network");
        }

        // Validate machine exists
        RegisteredMachine machine = machines.get(machineId);
        if (machine == null) {
            return new BindingResult.Failure("Machine not registered on this network");
        }

        // Check machine's maxControllers limit
        Set<BindingId> existingMachineBindings = bindingsByMachine.getOrDefault(machineId, Set.of());
        if (existingMachineBindings.size() >= machine.maxControllers()) {
            return new BindingResult.Failure(
                "Machine already has maximum controllers: " + machine.maxControllers());
        }

        // Check controller's maxMachines limit
        // (Need to look up controller definition, but we store maxMachines in RegisteredController)
        // For now, we'll need the controller definition - get it from the controller
        // Actually, we should store maxMachines in RegisteredController

        // Check if binding already exists
        boolean alreadyBound = existingMachineBindings.stream()
            .map(bindings::get)
            .filter(Objects::nonNull)
            .anyMatch(b -> b.controllerId().equals(controllerId));
        if (alreadyBound) {
            return new BindingResult.Failure("Controller already bound to this machine");
        }

        // Create binding
        Binding binding = Binding.create(networkId, controllerId, machineId, createdAt);
        bindings.put(binding.id(), binding);
        bindingsByMachine.computeIfAbsent(machineId, k -> new HashSet<>()).add(binding.id());
        bindingsByController.computeIfAbsent(controllerId, k -> new HashSet<>()).add(binding.id());

        return new BindingResult.Success(binding);
    }

    /**
     * Removes a binding.
     *
     * @param bindingId the binding to remove
     * @return true if removed
     */
    public boolean removeBinding(BindingId bindingId) {
        return removeBindingInternal(bindingId);
    }

    private boolean removeBindingInternal(BindingId bindingId) {
        Binding binding = bindings.remove(bindingId);
        if (binding == null) return false;

        Set<BindingId> machineBindings = bindingsByMachine.get(binding.machineId());
        if (machineBindings != null) {
            machineBindings.remove(bindingId);
        }

        Set<BindingId> controllerBindings = bindingsByController.get(binding.controllerId());
        if (controllerBindings != null) {
            controllerBindings.remove(bindingId);
        }

        return true;
    }

    /**
     * Gets all bindings for a machine.
     *
     * @param machineId the machine ID
     * @return list of bindings
     */
    public List<Binding> getBindingsForMachine(MachineId machineId) {
        Set<BindingId> ids = bindingsByMachine.get(machineId);
        if (ids == null) return List.of();

        return ids.stream()
            .map(bindings::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Gets all bindings for a controller.
     *
     * @param controllerId the controller ID
     * @return list of bindings
     */
    public List<Binding> getBindingsForController(ControllerId controllerId) {
        Set<BindingId> ids = bindingsByController.get(controllerId);
        if (ids == null) return List.of();

        return ids.stream()
            .map(bindings::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * @return all bindings in this network
     */
    public Collection<Binding> allBindings() {
        return Collections.unmodifiableCollection(bindings.values());
    }

    // ========== Queries ==========

    /**
     * @return all registered controller IDs
     */
    public Set<ControllerId> allControllerIds() {
        return Collections.unmodifiableSet(controllers.keySet());
    }

    /**
     * @return all registered machine IDs
     */
    public Set<MachineId> allMachineIds() {
        return Collections.unmodifiableSet(machines.keySet());
    }

    /**
     * @return all mainframe IDs
     */
    public Set<ControllerId> allMainframeIds() {
        return Collections.unmodifiableSet(mainframeIds);
    }

    /**
     * Checks if a controller is registered.
     */
    public boolean hasController(ControllerId id) {
        return controllers.containsKey(id);
    }

    /**
     * Checks if a machine is registered.
     */
    public boolean hasMachine(MachineId id) {
        return machines.containsKey(id);
    }

    // ========== Merge/Split Support ==========

    /**
     * Merges another registry into this one.
     * Used during network merge operations.
     *
     * @param other the registry to merge from
     */
    public void mergeFrom(NetworkRegistry other) {
        // Merge controllers
        for (var entry : other.controllers.entrySet()) {
            RegisteredController existing = controllers.get(entry.getKey());
            if (existing == null) {
                controllers.put(entry.getKey(), entry.getValue());
                if (entry.getValue().isMainframe) {
                    mainframeIds.add(entry.getKey());
                }
            }
        }

        // Merge machines
        for (var entry : other.machines.entrySet()) {
            machines.putIfAbsent(entry.getKey(), entry.getValue());
        }

        // Merge bindings (update network ID)
        for (Binding binding : other.bindings.values()) {
            Binding updated = binding.withNetworkId(this.networkId);
            bindings.put(updated.id(), updated);
            bindingsByMachine.computeIfAbsent(updated.machineId(), k -> new HashSet<>())
                .add(updated.id());
            bindingsByController.computeIfAbsent(updated.controllerId(), k -> new HashSet<>())
                .add(updated.id());
        }

        // Re-elect leader with combined mainframes
        reelectLeader();
    }

    /**
     * Creates a subset registry for a network split.
     *
     * @param newNetworkId the ID for the new network
     * @param controllerIds controllers that belong to the new network
     * @param machineIds machines that belong to the new network
     * @return the new registry
     */
    public NetworkRegistry splitTo(
            NetworkId newNetworkId,
            Set<ControllerId> controllerIds,
            Set<MachineId> machineIds) {

        NetworkRegistry newRegistry = new NetworkRegistry(newNetworkId);

        // Move controllers
        for (ControllerId id : controllerIds) {
            RegisteredController reg = controllers.remove(id);
            if (reg != null) {
                newRegistry.controllers.put(id, reg);
                if (reg.isMainframe) {
                    mainframeIds.remove(id);
                    newRegistry.mainframeIds.add(id);
                }
            }
        }

        // Move machines
        for (MachineId id : machineIds) {
            RegisteredMachine reg = machines.remove(id);
            if (reg != null) {
                newRegistry.machines.put(id, reg);
            }
        }

        // Handle bindings: move those where both parties are in new network,
        // remove those that span networks
        List<BindingId> toRemove = new ArrayList<>();
        for (var entry : bindings.entrySet()) {
            Binding binding = entry.getValue();
            boolean controllerInNew = controllerIds.contains(binding.controllerId());
            boolean machineInNew = machineIds.contains(binding.machineId());

            if (controllerInNew && machineInNew) {
                // Move to new registry
                Binding updated = binding.withNetworkId(newNetworkId);
                newRegistry.bindings.put(updated.id(), updated);
                newRegistry.bindingsByMachine
                    .computeIfAbsent(updated.machineId(), k -> new HashSet<>())
                    .add(updated.id());
                newRegistry.bindingsByController
                    .computeIfAbsent(updated.controllerId(), k -> new HashSet<>())
                    .add(updated.id());
                toRemove.add(entry.getKey());
            } else if (controllerInNew || machineInNew) {
                // Binding spans networks - becomes inactive, remove from both
                toRemove.add(entry.getKey());
            }
            // else: both stay in original network, no action
        }

        for (BindingId id : toRemove) {
            removeBindingInternal(id);
        }

        // Re-elect leaders in both registries
        reelectLeader();
        newRegistry.reelectLeader();

        return newRegistry;
    }

    // ========== Internal Records ==========

    private record RegisteredController(
            ControllerId id,
            long createdAt,
            boolean isMainframe,
            boolean available
    ) {
        RegisteredController(ControllerId id, long createdAt, boolean isMainframe) {
            this(id, createdAt, isMainframe, true);
        }

        RegisteredController withAvailable(boolean available) {
            return new RegisteredController(id, createdAt, isMainframe, available);
        }
    }

    private record RegisteredMachine(
            MachineId id,
            String typeId,
            int maxControllers
    ) {}

    /**
     * Result of a binding operation.
     */
    public sealed interface BindingResult {
        record Success(Binding binding) implements BindingResult {}
        record Failure(String reason) implements BindingResult {}
    }
}
