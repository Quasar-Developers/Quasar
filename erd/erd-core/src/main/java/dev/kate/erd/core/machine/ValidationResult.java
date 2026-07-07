package dev.kate.erd.core.machine;

import dev.kate.erd.core.model.BlockPos;

import java.util.Objects;
import java.util.Set;

/**
 * Result of validating a structure against a machine or component definition.
 *
 * <p>This is a sealed hierarchy with two possible outcomes:
 * <ul>
 *   <li>{@link Valid} — structure matches the definition, contains the resulting {@link Structure}</li>
 *   <li>{@link Invalid} — structure does not match, contains reason and problem positions</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * ValidationResult result = definition.validate(snapshot);
 *
 * switch (result) {
 *     case ValidationResult.Valid valid -> {
 *         Structure structure = valid.structure();
 *         // Create machine instance with this structure
 *     }
 *     case ValidationResult.Invalid invalid -> {
 *         logger.warn("Validation failed: " + invalid.reason());
 *         // Highlight problem positions to player
 *     }
 * }
 * }</pre>
 *
 * <p>Thread-safety: All implementations are immutable and thread-safe.
 */
public sealed interface ValidationResult {

    /**
     * Validation succeeded. The structure matches the definition.
     *
     * @param structure the validated structure with positions, endpoints, and metrics
     */
    record Valid(Structure structure) implements ValidationResult {
        public Valid {
            Objects.requireNonNull(structure, "structure must not be null");
        }

        /**
         * Convenience method to get positions from the structure.
         *
         * @return the occupied positions
         */
        public Set<BlockPos> positions() {
            return structure.positions();
        }
    }

    /**
     * Validation failed. The structure does not match the definition.
     *
     * @param reason human-readable explanation of why validation failed
     * @param problemPositions positions that caused the failure (may be empty)
     */
    record Invalid(String reason, Set<BlockPos> problemPositions) implements ValidationResult {
        public Invalid {
            Objects.requireNonNull(reason, "reason must not be null");
            problemPositions = problemPositions != null ? Set.copyOf(problemPositions) : Set.of();
        }

        /**
         * Creates an Invalid result with no specific problem positions.
         *
         * @param reason the failure reason
         */
        public Invalid(String reason) {
            this(reason, Set.of());
        }
    }

    // ========== Factory Methods ==========

    /**
     * Creates a Valid result from a Structure.
     *
     * @param structure the validated structure
     * @return a Valid result
     */
    static Valid valid(Structure structure) {
        return new Valid(structure);
    }

    /**
     * Creates a Valid result from positions and endpoints.
     *
     * @param positions the occupied positions
     * @param endpoints the detected endpoints
     * @return a Valid result
     */
    static Valid valid(Set<BlockPos> positions, java.util.List<dev.kate.erd.core.endpoint.Endpoint> endpoints) {
        return new Valid(Structure.of(positions, endpoints));
    }

    /**
     * Creates a Valid result from positions, endpoints, and tier.
     *
     * @param positions the occupied positions
     * @param endpoints the detected endpoints
     * @param tier the structure tier
     * @return a Valid result
     */
    static Valid valid(Set<BlockPos> positions, java.util.List<dev.kate.erd.core.endpoint.Endpoint> endpoints, int tier) {
        return new Valid(Structure.of(positions, endpoints, tier));
    }

    /**
     * Creates an Invalid result.
     *
     * @param reason the failure reason
     * @return an Invalid result
     */
    static Invalid invalid(String reason) {
        return new Invalid(reason);
    }

    /**
     * Creates an Invalid result with problem positions.
     *
     * @param reason the failure reason
     * @param problemPositions the problem positions
     * @return an Invalid result
     */
    static Invalid invalid(String reason, Set<BlockPos> problemPositions) {
        return new Invalid(reason, problemPositions);
    }
}

