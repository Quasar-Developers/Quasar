package dev.kate.erd.core.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for a machine instance.
 *
 * <p>Machines are physical processing multiblocks (reactors, electrolyzers, etc.).
 * Each machine instance has a unique MachineId that persists across restarts.
 *
 * <p>Thread-safety: This class is immutable and therefore thread-safe.
 *
 * @param id the underlying UUID
 */
public record MachineId(UUID id) {

    /**
     * Constructs a MachineId with validation.
     *
     * @param id the UUID, must not be null
     * @throws NullPointerException if id is null
     */
    public MachineId {
        Objects.requireNonNull(id, "id must not be null");
    }

    /**
     * Creates a new random MachineId.
     *
     * @return a new MachineId with a random UUID
     */
    public static MachineId create() {
        return new MachineId(UUID.randomUUID());
    }

    /**
     * Parses a MachineId from a string representation.
     *
     * @param value the string UUID
     * @return the parsed MachineId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static MachineId parse(String value) {
        return new MachineId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
