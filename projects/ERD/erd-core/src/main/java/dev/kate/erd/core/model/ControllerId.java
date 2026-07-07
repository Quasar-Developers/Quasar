package dev.kate.erd.core.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for a controller instance.
 *
 * <p>Controllers monitor and control machines (panels, control rooms, terminals).
 * A Mainframe is a special type of Controller with authority over the DATA network.
 * Each controller instance has a unique ControllerId that persists across restarts.
 *
 * <p>Thread-safety: This class is immutable and therefore thread-safe.
 *
 * @param id the underlying UUID
 */
public record ControllerId(UUID id) {

    /**
     * Constructs a ControllerId with validation.
     *
     * @param id the UUID, must not be null
     * @throws NullPointerException if id is null
     */
    public ControllerId {
        Objects.requireNonNull(id, "id must not be null");
    }

    /**
     * Creates a new random ControllerId.
     *
     * @return a new ControllerId with a random UUID
     */
    public static ControllerId create() {
        return new ControllerId(UUID.randomUUID());
    }

    /**
     * Parses a ControllerId from a string representation.
     *
     * @param value the string UUID
     * @return the parsed ControllerId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static ControllerId parse(String value) {
        return new ControllerId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
