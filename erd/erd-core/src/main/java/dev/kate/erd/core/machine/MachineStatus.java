package dev.kate.erd.core.machine;

/**
 * Operational status of a machine.
 *
 * <p>Thread-safety: Enum values are inherently thread-safe.
 */
public enum MachineStatus {
    /**
     * Machine is operational and running normally.
     */
    RUNNING,

    /**
     * Machine is operational but idle (no work to do).
     */
    IDLE,

    /**
     * Machine is paused by controller command.
     */
    PAUSED,

    /**
     * Machine has no controller/DATA connection.
     */
    BLIND,

    /**
     * Machine has an error condition.
     */
    ERROR,

    /**
     * Machine structure is invalid or being removed.
     */
    INVALID
}
