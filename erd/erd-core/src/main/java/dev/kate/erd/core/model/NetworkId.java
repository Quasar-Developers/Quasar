package dev.kate.erd.core.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for a network instance.
 *
 * <p>Each network (regardless of type type) has a unique NetworkId that
 * persists across server restarts and is used for referencing networks
 * in persistence and cross-network operations.
 *
 * <p>Thread-safety: This class is immutable and therefore thread-safe.
 *
 * @param id the underlying UUID
 */
public record NetworkId(UUID id) {

    /**
     * Constructs a NetworkId with validation.
     *
     * @param id the UUID, must not be null
     * @throws NullPointerException if id is null
     */
    public NetworkId {
        Objects.requireNonNull(id, "id must not be null");
    }

    /**
     * Creates a new random NetworkId.
     *
     * @return a new NetworkId with a random UUID
     */
    public static NetworkId create() {
        return new NetworkId(UUID.randomUUID());
    }

    /**
     * Parses a NetworkId from a string representation.
     *
     * @param value the string UUID
     * @return the parsed NetworkId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static NetworkId parse(String value) {
        return new NetworkId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
