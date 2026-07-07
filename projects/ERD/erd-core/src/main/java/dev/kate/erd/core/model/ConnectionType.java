package dev.kate.erd.core.model;

/**
 * Enumerates the different connection types supported by the ERD system.
 *
 * <p>Each type represents a distinct kind of infrastructure network:
 * <ul>
 *   <li>{@link #POWER} - Electrical power distribution (segments)</li>
 *   <li>{@link #PIPE} - Fluid and gas transportation (pipes)</li>
 *   <li>{@link #DATA} - Data communication and control (links)</li>
 * </ul>
 *
 * <p>The physical block mapping (e.g., RED_MUSHROOM_BLOCK for POWER) is handled
 * by the Bukkit adapter module, not in the core.
 *
 * <p>Thread-safety: Enum values are inherently thread-safe.
 */
public enum ConnectionType {
    /**
     * Power connection for electrical energy distribution.
     * Physically represented as segments. Bukkit adapter maps this to RED_MUSHROOM_BLOCK.
     */
    POWER,

    /**
     * Pipe connection for fluid and gas transportation.
     * Physically represented as pipes. Bukkit adapter maps this to BROWN_MUSHROOM_BLOCK.
     *
     * <p>PIPE networks support a "family" locking mechanism where
     * the network is locked to either FLUID or GAS on first use.
     */
    PIPE,

    /**
     * Data connection for communication and control signals.
     * Physically represented as data links. Bukkit adapter maps this to MUSHROOM_STEM.
     *
     * <p>DATA networks support the control plane with mainframe
     * leadership, machine/controller registries, and bindings.
     */
    DATA
}

