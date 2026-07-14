package dev.kate.erd.core.machine.component;

import dev.kate.erd.core.machine.MachineInstance;
import dev.kate.erd.core.machine.RescanResult;
import dev.kate.erd.core.machine.Structure;
import dev.kate.erd.core.machine.StructureSnapshot;
import dev.kate.erd.core.model.BlockPos;

/**
 * Represents an independently-upgradeable sub-machine attached to a parent machine.
 *
 * <p>Components allow complex machines to be built from modular parts that can each
 * be upgraded separately. For example, a reactor might have:
 * <ul>
 *   <li>A power laser component (upgradeable for more power)</li>
 *   <li>A cooling system component (upgradeable for better heat management)</li>
 *   <li>A fuel injector component (upgradeable for efficiency)</li>
 * </ul>
 *
 * <p>Each component has its own:
 * <ul>
 *   <li>Identity ({@link ComponentId})</li>
 *   <li>Structure (positions, endpoints, metrics)</li>
 *   <li>State (persisted independently)</li>
 *   <li>Upgrade path (can grow/shrink without affecting parent identity)</li>
 * </ul>
 *
 * <p>Components are attached at a specific {@link #attachmentPoint()} relative to
 * the parent machine's anchor. The component's structure is validated relative to
 * this attachment point.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Component is detected when blocks complete its pattern near a compatible machine</li>
 *   <li>Component is attached to parent via {@link MachineInstance#attachComponent}</li>
 *   <li>Component ticks are delegated from parent's tick</li>
 *   <li>Component can be rescanned/resized independently</li>
 *   <li>Component is detached when its structure becomes invalid</li>
 * </ol>
 *
 * <p>Thread-safety: Components are NOT thread-safe. Use on processing thread only.
 *
 * @see ComponentDefinition
 * @see MachineInstance#components()
 */
public interface MachineComponent {

    /**
     * @return the unique identifier for this component
     */
    ComponentId id();

    /**
     * @return the definition/type of this component
     */
    ComponentDefinition<?> definition();

    /**
     * @return the current structure of this component
     */
    Structure structure();

    /**
     * @return the attachment point (where this component connects to the parent)
     */
    BlockPos attachmentPoint();

    /**
     * @return the parent machine this component belongs to
     */
    MachineInstance parent();

    /**
     * Updates the component's structure after a resize/upgrade.
     *
     * <p>This method is called internally when {@link #rescan} detects a valid
     * structure change. It triggers {@link #onStructureChanged} for subclasses.
     *
     * @param newStructure the new structure
     * @throws IllegalArgumentException if newStructure is invalid for this component
     */
    void updateStructure(Structure newStructure);

    /**
     * Rescans the component structure against the current world state.
     *
     * @param snapshot the current structure snapshot
     * @return the rescan result indicating if structure changed
     */
    RescanResult rescan(StructureSnapshot snapshot);

    /**
     * Called each server tick to update component state.
     * Delegated from the parent machine's tick.
     */
    void tick();

    /**
     * Called when this component is attached to a parent machine.
     *
     * @param parent the parent machine
     */
    void onAttach(MachineInstance parent);

    /**
     * Called when this component is detached from its parent.
     */
    void onDetach();

    /**
     * Called when the component's structure has changed.
     *
     * @param oldStructure the previous structure
     * @param newStructure the new structure
     */
    void onStructureChanged(Structure oldStructure, Structure newStructure);
}

