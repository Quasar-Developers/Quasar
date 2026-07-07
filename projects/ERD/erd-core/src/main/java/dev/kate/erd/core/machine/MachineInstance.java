package dev.kate.erd.core.machine;

import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.machine.component.ComponentId;
import dev.kate.erd.core.machine.component.MachineComponent;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ChunkKey;
import dev.kate.erd.core.model.MachineId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Represents an active machine instance in the world.
 *
 * <p>Machine instances are created from validated structures and track
 * runtime state like endpoints, control bindings, and operational status.
 *
 * <p>Machines support upgradeable structures — the structure can grow or shrink
 * while maintaining the same identity. Use {@link #rescan(StructureSnapshot)} to
 * check for structure changes and {@link #updateStructure(Structure)} to apply them.
 *
 * <p>Machines can have attached components (sub-machines) that can be independently
 * upgraded. Components share the parent's lifecycle but have their own structure
 * and state.
 *
 * <p>Thread-safety: Instances are NOT thread-safe. Mutations should occur
 * on the main/processing thread.
 *
 * @see Structure
 * @see MachineComponent
 */
public interface MachineInstance {

    /**
     * @return the unique identifier for this instance
     */
    MachineId id();

    /**
     * @return the definition/type of this machine
     */
    MachineDefinition<?> definition();

    /**
     * @return the anchor/controller block position
     */
    BlockPos anchorPosition();

    // ========== Structure ==========

    /**
     * @return the current structure of this machine
     */
    Structure structure();

    /**
     * @return all positions occupied by this machine (delegates to structure)
     */
    default Set<BlockPos> occupiedPositions() {
        return structure().positions();
    }

    /**
     * @return all endpoints/ports on this machine (delegates to structure)
     */
    default List<Endpoint> endpoints() {
        return structure().endpoints();
    }

    /**
     * @return all chunks this machine spans (delegates to structure)
     */
    default Set<ChunkKey> spannedChunks() {
        return structure().spannedChunks();
    }

    /**
     * Updates the machine's structure after a resize/upgrade.
     *
     * <p>This method is called internally when {@link #rescan} detects a valid
     * structure change. It triggers {@link #onStructureChanged} for subclasses.
     *
     * @param newStructure the new structure
     * @throws IllegalArgumentException if newStructure is invalid for this machine
     */
    void updateStructure(Structure newStructure);

    /**
     * Rescans the machine structure against the current world state.
     *
     * <p>This replaces the old {@code revalidate} method with richer return info.
     *
     * @param snapshot the current structure snapshot
     * @return the rescan result indicating if structure changed
     */
    RescanResult rescan(StructureSnapshot snapshot);

    /**
     * Called when the machine's structure has changed.
     *
     * @param oldStructure the previous structure
     * @param newStructure the new structure
     */
    void onStructureChanged(Structure oldStructure, Structure newStructure);

    // ========== Components ==========

    /**
     * @return all attached components (immutable view)
     */
    List<MachineComponent> components();

    /**
     * Gets a component by ID.
     *
     * @param id the component ID
     * @return the component, or empty if not found
     */
    Optional<MachineComponent> getComponent(ComponentId id);

    /**
     * Attaches a component to this machine.
     *
     * @param component the component to attach
     * @throws IllegalArgumentException if component is incompatible or already attached
     */
    void attachComponent(MachineComponent component);

    /**
     * Detaches a component from this machine.
     *
     * @param id the component ID
     * @return the detached component, or empty if not found
     */
    Optional<MachineComponent> detachComponent(ComponentId id);

    /**
     * Called when a component's structure changes.
     *
     * @param component the component that changed
     * @param oldStructure the previous structure
     * @param newStructure the new structure
     */
    void onComponentStructureChanged(MachineComponent component, Structure oldStructure, Structure newStructure);

    /**
     * @return all positions occupied by this machine AND its components
     */
    default Set<BlockPos> allOccupiedPositions() {
        Set<BlockPos> positions = new java.util.HashSet<>(occupiedPositions());
        for (MachineComponent component : components()) {
            positions.addAll(component.structure().positions());
        }
        return Set.copyOf(positions);
    }

    /**
     * @return all chunks spanned by this machine AND its components
     */
    default Set<ChunkKey> allSpannedChunks() {
        Set<ChunkKey> chunks = new java.util.HashSet<>(spannedChunks());
        for (MachineComponent component : components()) {
            chunks.addAll(component.structure().spannedChunks());
        }
        return Set.copyOf(chunks);
    }

    // ========== Status ==========

    /**
     * @return the current operational status
     */
    MachineStatus status();

    // ========== Lifecycle ==========

    /**
     * Called each server tick to update machine state.
     * Also ticks all attached components.
     */
    void tick();

    /**
     * Called when control link to a controller is established.
     *
     * @param controllerId the controller that linked
     */
    void onControlLinkEstablished(dev.kate.erd.core.model.ControllerId controllerId);

    /**
     * Called when control link to a controller is lost.
     * Machine behavior on loss of control is type-specific.
     *
     * @param controllerId the controller that unlinked
     */
    void onControlLinkLost(dev.kate.erd.core.model.ControllerId controllerId);

    /**
     * Called when the machine is being removed/destroyed.
     * Also detaches all components.
     */
    void onRemove();
}
