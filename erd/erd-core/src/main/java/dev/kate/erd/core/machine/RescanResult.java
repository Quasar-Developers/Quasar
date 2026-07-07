package dev.kate.erd.core.machine;

/**
 * Result of rescanning a machine or component structure.
 *
 * <p>Rescanning compares the current world state against the machine's definition
 * to determine if the structure has changed. This is used when blocks are placed
 * or broken near a machine.
 *
 * <p>Possible outcomes:
 * <ul>
 *   <li>{@link #UNCHANGED} — structure is identical to before</li>
 *   <li>{@link #RESIZED} — structure is still valid but has grown or shrunk</li>
 *   <li>{@link #INVALID} — structure is no longer valid and machine should be removed</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * RescanResult result = machine.rescan(newSnapshot);
 *
 * switch (result) {
 *     case UNCHANGED -> { / * no action needed * / }
 *     case RESIZED -> {
 *         // Machine already updated its structure internally
 *         // Notify spatial index, save to disk
 *         spatialIndex.updateMachine(machine);
 *         markDirty(machine);
 *     }
 *     case INVALID -> {
 *         instanceManager.removeMachine(machine);
 *     }
 * }
 * }</pre>
 */
public enum RescanResult {

    /**
     * Structure is unchanged from the previous state.
     * No action needed.
     */
    UNCHANGED,

    /**
     * Structure is still valid but has changed size/shape.
     * The machine has already updated its internal structure.
     * Caller should update spatial indices and persistence.
     */
    RESIZED,

    /**
     * Structure is no longer valid.
     * The machine should be removed from the registry.
     */
    INVALID
}

