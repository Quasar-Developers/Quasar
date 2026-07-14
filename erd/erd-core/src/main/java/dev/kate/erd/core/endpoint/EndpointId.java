package dev.kate.erd.core.endpoint;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for an endpoint instance.
 *
 * <p>Thread-safety: This class is immutable and therefore thread-safe.
 *
 * @param id the underlying UUID
 */
public record EndpointId(UUID id) {

    /**
     * Constructs an EndpointId with validation.
     *
     * @param id the UUID, must not be null
     * @throws NullPointerException if id is null
     */
    public EndpointId {
        Objects.requireNonNull(id, "id must not be null");
    }

    /**
     * Creates a new random EndpointId.
     *
     * @return a new EndpointId with a random UUID
     */
    public static EndpointId create() {
        return new EndpointId(UUID.randomUUID());
    }

    /**
     * Parses an EndpointId from a string representation.
     *
     * @param value the string UUID
     * @return the parsed EndpointId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static EndpointId parse(String value) {
        return new EndpointId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
