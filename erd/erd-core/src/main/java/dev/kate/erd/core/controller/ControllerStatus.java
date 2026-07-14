package dev.kate.erd.core.controller;

/**
 * Connection status of a controller.
 *
 * <p>Thread-safety: Enum values are inherently thread-safe.
 */
public enum ControllerStatus {
    /**
     * Controller is connected and operational.
     */
    CONNECTED,

    /**
     * Controller has no DATA network signal.
     */
    NO_SIGNAL,

    /**
     * Controller structure is invalid or being removed.
     */
    INVALID
}
