package dev.kate.erd.core.machine.component;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for a machine component.
 *
 * <p>Component IDs are separate from machine IDs because components have their own
 * identity within their parent machine. This allows components to be tracked,
 * persisted, and referenced independently.
 *
 * <p>Thread-safety: This record is immutable and thread-safe.
 *
 * @param id the underlying UUID
 */
public record ComponentId(UUID id) {

    public ComponentId {
        Objects.requireNonNull(id, "id must not be null");
    }

    /**
     * Creates a new random ComponentId.
     *
     * @return a new ComponentId
     */
    public static ComponentId create() {
        return new ComponentId(UUID.randomUUID());
    }

    /**
     * Parses a ComponentId from a string.
     *
     * @param value the string representation
     * @return the parsed ComponentId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static ComponentId parse(String value) {
        return new ComponentId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}

