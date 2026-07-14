package dev.kate.erd.core.machine;

import dev.kate.erd.core.controller.ControllerDefinition;
import dev.kate.erd.core.controller.ControllerInstance;
import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ControllerId;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.util.Clock;
import dev.kate.erd.core.util.ErdLogger;

import java.util.*;

/**
 * Unified registry and manager for machines and controllers.
 *
 * <p>This manager tracks active machines and controllers, maintains spatial
 * indices for fast lookups, and handles detection/validation lifecycle.
 *
 * <p>Thread-safety: NOT thread-safe. Use on processing thread only.
 */
public final class InstanceManager {

    private final ErdLogger logger;
    private final Clock clock;

    // Definition registries
    private final Map<String, MachineDefinition<?>> machineDefinitions = new HashMap<>();
    private final Map<String, ControllerDefinition<?>> controllerDefinitions = new HashMap<>();

    // Instance registries
    private final Map<MachineId, MachineInstance> machines = new HashMap<>();
    private final Map<ControllerId, ControllerInstance> controllers = new HashMap<>();

    // Spatial indices - O(1) lookups
    private final Map<BlockPos, MachineInstance> occupiedByMachine = new HashMap<>();
    private final Map<BlockPos, ControllerInstance> occupiedByController = new HashMap<>();
    private final Map<BlockPos, Endpoint> endpointsByPosition = new HashMap<>();
    private final Map<Endpoint, MachineInstance> endpointToMachine = new HashMap<>();

    /**
     * Creates a new instance manager.
     *
     * @param logger the logger
     * @param clock the clock for timestamps
     */
    public InstanceManager(ErdLogger logger, Clock clock) {
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    // ========== Definition Registration ==========

    /**
     * Registers a machine definition.
     *
     * @param definition the definition to register
     * @throws IllegalArgumentException if type ID already registered
     */
    public void registerMachineDefinition(MachineDefinition<?> definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        String typeId = definition.typeId();

        if (machineDefinitions.containsKey(typeId)) {
            throw new IllegalArgumentException("Machine type already registered: " + typeId);
        }

        machineDefinitions.put(typeId, definition);
        logger.info("Registered machine definition: %s", typeId);
    }

    /**
     * Registers a controller definition.
     *
     * @param definition the definition to register
     * @throws IllegalArgumentException if type ID already registered
     */
    public void registerControllerDefinition(ControllerDefinition<?> definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        String typeId = definition.typeId();

        if (controllerDefinitions.containsKey(typeId)) {
            throw new IllegalArgumentException("Controller type already registered: " + typeId);
        }

        controllerDefinitions.put(typeId, definition);
        logger.info("Registered controller definition: %s", typeId);
    }

    // ========== Detection ==========


    // ========== Instance Management ==========

    /**
     * Registers a machine instance.
     *
     * @param instance the instance to register
     */
    public void registerMachine(MachineInstance instance) {
        Objects.requireNonNull(instance, "instance must not be null");

        machines.put(instance.id(), instance);

        for (BlockPos pos : instance.occupiedPositions()) {
            occupiedByMachine.put(pos, instance);
        }

        for (Endpoint endpoint : instance.endpoints()) {
            endpointsByPosition.put(endpoint.position(), endpoint);
            endpointToMachine.put(endpoint, instance);
        }

        // Check if there's a pending restore state for this machine
        Map<String, Object> restoreState = pendingRestoreStates.remove(instance.id());
        if (restoreState != null && instance instanceof MachineStateful stateful) {
            stateful.restoreState(restoreState);
            logger.debug("Restored state for machine instance %s", instance.id());
        }

        logger.debug("Registered machine instance %s", instance.id());
    }

    /**
     * Registers a controller instance.
     *
     * @param instance the instance to register
     */
    public void registerController(ControllerInstance instance) {
        Objects.requireNonNull(instance, "instance must not be null");

        controllers.put(instance.id(), instance);

        for (BlockPos pos : instance.occupiedPositions()) {
            occupiedByController.put(pos, instance);
        }

        for (Endpoint endpoint : instance.endpoints()) {
            endpointsByPosition.put(endpoint.position(), endpoint);
        }

        logger.debug("Registered controller instance %s", instance.id());
    }

    /**
     * Removes a machine instance.
     *
     * @param instance the instance to remove
     */
    public void removeMachine(MachineInstance instance) {
        Objects.requireNonNull(instance, "instance must not be null");

        instance.onRemove();
        machines.remove(instance.id());

        for (BlockPos pos : instance.occupiedPositions()) {
            occupiedByMachine.remove(pos);
        }

        for (Endpoint endpoint : instance.endpoints()) {
            endpointsByPosition.remove(endpoint.position());
            endpointToMachine.remove(endpoint);
        }

        logger.debug("Removed machine instance %s", instance.id());
    }

    /**
     * Removes a controller instance.
     *
     * @param instance the instance to remove
     */
    public void removeController(ControllerInstance instance) {
        Objects.requireNonNull(instance, "instance must not be null");

        instance.onRemove();
        controllers.remove(instance.id());

        for (BlockPos pos : instance.occupiedPositions()) {
            occupiedByController.remove(pos);
        }

        for (Endpoint endpoint : instance.endpoints()) {
            endpointsByPosition.remove(endpoint.position());
        }

        logger.debug("Removed controller instance %s", instance.id());
    }

    // ========== Queries ==========

    /**
     * Gets a machine by ID.
     *
     * @param id the machine ID
     * @return the machine, or empty if not found
     */
    public Optional<MachineInstance> getMachine(MachineId id) {
        return Optional.ofNullable(machines.get(id));
    }


    /**
     * Gets the machine occupying a position.
     *
     * @param pos the position
     * @return the machine, or empty if none
     */
    public Optional<MachineInstance> getMachineAt(BlockPos pos) {
        return Optional.ofNullable(occupiedByMachine.get(pos));
    }

    /**
     * Gets the controller occupying a position.
     *
     * @param pos the position
     * @return the controller, or empty if none
     */
    public Optional<ControllerInstance> getControllerAt(BlockPos pos) {
        return Optional.ofNullable(occupiedByController.get(pos));
    }

    /**
     * Gets an endpoint at a position.
     *
     * @param pos the position
     * @return the endpoint, or empty if none
     */
    public Optional<Endpoint> getEndpointAt(BlockPos pos) {
        return Optional.ofNullable(endpointsByPosition.get(pos));
    }

    /**
     * Gets the machine that owns an endpoint.
     *
     * @param endpoint the endpoint
     * @return the machine, or empty if none (e.g., endpoint belongs to a controller)
     */
    public Optional<MachineInstance> getMachineForEndpoint(Endpoint endpoint) {
        return Optional.ofNullable(endpointToMachine.get(endpoint));
    }

    /**
     * Checks if a position is occupied by any machine or controller.
     *
     * @param pos the position
     * @return true if occupied
     */
    public boolean isOccupied(BlockPos pos) {
        return occupiedByMachine.containsKey(pos) || occupiedByController.containsKey(pos);
    }

    /**
     * @return all active machines
     */
    public Collection<MachineInstance> allMachines() {
        return Collections.unmodifiableCollection(machines.values());
    }

    /**
     * @return all active controllers
     */
    public Collection<ControllerInstance> allControllers() {
        return Collections.unmodifiableCollection(controllers.values());
    }

    /**
     * Gets all mainframe controllers.
     *
     * @return mainframe instances
     */
    public List<ControllerInstance> allMainframes() {
        return controllers.values().stream()
            .filter(c -> c.definition().isMainframe())
            .toList();
    }

    /**
     * @return count of active machines
     */
    public int machineCount() {
        return machines.size();
    }

    /**
     * @return count of active controllers
     */
    public int controllerCount() {
        return controllers.size();
    }

    // ========== Tick ==========

    /**
     * Ticks all machines and controllers.
     */
    public void tick() {
        for (MachineInstance machine : machines.values()) {
            try {
                machine.tick();
            } catch (Exception e) {
                logger.error("Error ticking machine " + machine.id(), e);
            }
        }

        for (ControllerInstance controller : controllers.values()) {
            try {
                controller.tick();
            } catch (Exception e) {
                logger.error("Error ticking controller " + controller.id(), e);
            }
        }
    }

    // ========== Internal ==========

    private boolean anyPositionOccupied(Set<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (occupiedByMachine.containsKey(pos) || occupiedByController.containsKey(pos)) {
                return true;
            }
        }
        return false;
    }

    // ========== Snapshot/Restore for Addon Reload ==========

    // Pending restore states for addon reload
    private final Map<MachineId, Map<String, Object>> pendingRestoreStates = new HashMap<>();

    /**
     * Creates snapshots of all machines for addon reload.
     * Also unregisters all machines and queues their states for restoration.
     */
    public List<MachineSnapshot> snapshotAllMachines() {
        List<MachineSnapshot> snapshots = new ArrayList<>();
        for (MachineInstance machine : machines.values()) {
            Map<String, Object> state = Map.of();
            int stateVersion = 0;
            if (machine instanceof MachineStateful stateful) {
                state = stateful.saveState();
            }

            // Build component snapshots
            List<MachineSnapshot.ComponentSnapshot> componentSnapshots = new ArrayList<>();
            for (var component : machine.components()) {
                Map<String, Object> componentState = Map.of();
                int componentStateVersion = 0;
                if (component instanceof MachineStateful stateful) {
                    componentState = stateful.saveState();
                }
                componentSnapshots.add(new MachineSnapshot.ComponentSnapshot(
                    component.id(),
                    component.definition().componentTypeId(),
                    component.attachmentPoint(),
                    component.structure().positions(),
                    componentStateVersion,
                    componentState
                ));
            }

            snapshots.add(MachineSnapshot.builder()
                .id(machine.id())
                .typeId(machine.definition().typeId())
                .anchorPosition(machine.anchorPosition())
                .occupiedPositions(machine.structure().positions())
                .components(componentSnapshots)
                .stateVersion(stateVersion)
                .state(state)
                .build()
            );

            // Queue state for restoration when machine is re-registered
            if (!state.isEmpty()) {
                pendingRestoreStates.put(machine.id(), state);
            }
        }

        // Clear all machines and controllers since addons are being reloaded
        machines.clear();
        occupiedByMachine.clear();
        endpointsByPosition.clear();
        endpointToMachine.clear();
        controllers.clear();
        occupiedByController.clear();

        return snapshots;
    }

    /**
     * Queues machine state for restore when machine is re-detected.
     */
    public void restoreFromSnapshots(List<MachineSnapshot> snapshots) {
        for (MachineSnapshot snap : snapshots) {
            pendingRestoreStates.put(snap.id(), snap.state());
        }
    }
}
