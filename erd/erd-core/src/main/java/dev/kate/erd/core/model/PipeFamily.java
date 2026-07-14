package dev.kate.erd.core.model;

/**
 * Represents the family type for PIPE networks.
 *
 * <p>PIPE networks are locked to a specific family on first meaningful use.
 * A network configured for FLUID cannot transport GAS and vice versa.
 * This is the initial policy; the design allows for future extension
 * to support multiplexing.
 *
 * <p>Thread-safety: Enum values are inherently thread-safe.
 */
public enum PipeFamily {
    /**
     * Network transports liquid fluids (water, lava, custom fluids).
     */
    FLUID,

    /**
     * Network transports gases (steam, oxygen, custom gases).
     */
    GAS,

    /**
     * Network has not yet been assigned a family.
     * This is the initial state before first use.
     */
    UNASSIGNED
}
