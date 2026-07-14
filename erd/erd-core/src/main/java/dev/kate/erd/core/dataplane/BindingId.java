package dev.kate.erd.core.dataplane;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for a binding.
 *
 * <p>Thread-safety: This class is immutable and therefore thread-safe.
 *
 * @param id the underlying UUID
 */
public record BindingId(UUID id) {

    /**
     * Constructs a BindingId with validation.
     *
     * @param id the UUID, must not be null
     * @throws NullPointerException if id is null
     */
    public BindingId {
        Objects.requireNonNull(id, "id must not be null");
    }

    /**
     * Creates a new random BindingId.
     *
     * @return a new BindingId with a random UUID
     */
    public static BindingId create() {
        return new BindingId(UUID.randomUUID());
    }

    /**
     * Parses a BindingId from a string representation.
     *
     * @param value the string UUID
     * @return the parsed BindingId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static BindingId parse(String value) {
        return new BindingId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
