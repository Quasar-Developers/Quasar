package dev.kate.erd.core.debug;

/**
 * Interface for machines that can provide debug information.
 */
public interface MachineIntrospectable {
    /**
     * Creates a debug snapshot for display.
     */
    DebugSnapshot createDebugSnapshot();

    /**
     * @return display name for debug UI
     */
    String debugDisplayName();

    /**
     * @return true if machine has critical issues
     */
    default boolean hasCriticalIssues() {
        return false;
    }
}
