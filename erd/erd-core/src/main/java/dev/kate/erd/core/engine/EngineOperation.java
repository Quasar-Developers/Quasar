package dev.kate.erd.core.engine;

import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ChunkKey;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.NetworkId;
import dev.kate.erd.core.model.PipeFamily;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Represents an operation to be executed on the NetworkEngine.
 *
 * <p>All engine mutations are represented as operations that are queued
 * and executed serially. This ensures thread-safe state changes and
 * enables async computation with version-checked application.
 *
 * <p>Thread-safety: Operations are immutable value objects.
 */
public sealed interface EngineOperation {

    /**
     * Add a segment at a position for a specific type.
     *
     * @param type the network type
     * @param position the segment position
     * @param callback optional callback with result
     */
    record AddSegment(
            ConnectionType type,
            BlockPos position,
            Consumer<OperationResult> callback
    ) implements EngineOperation {
        public AddSegment {
            Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(position, "position must not be null");
        }

        public AddSegment(ConnectionType layer, BlockPos position) {
            this(layer, position, null);
        }
    }

    /**
     * Remove a segment at a position for a specific type.
     *
     * @param type the network type
     * @param position the segment position
     * @param callback optional callback with result
     */
    record RemoveSegment(
            ConnectionType type,
            BlockPos position,
            Consumer<OperationResult> callback
    ) implements EngineOperation {
        public RemoveSegment {
            Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(position, "position must not be null");
        }

        public RemoveSegment(ConnectionType layer, BlockPos position) {
            this(layer, position, null);
        }
    }

    /**
     * Mark a chunk as loaded for all layers.
     *
     * @param chunk the chunk key
     */
    record ChunkLoaded(ChunkKey chunk) implements EngineOperation {
        public ChunkLoaded {
            Objects.requireNonNull(chunk, "chunk must not be null");
        }
    }

    /**
     * Mark a chunk as unloaded for all layers.
     *
     * @param chunk the chunk key
     */
    record ChunkUnloaded(ChunkKey chunk) implements EngineOperation {
        public ChunkUnloaded {
            Objects.requireNonNull(chunk, "chunk must not be null");
        }
    }

    /**
     * Set the pipe family for a PIPE network.
     *
     * @param networkId the network ID
     * @param family the family to set
     * @param callback optional callback with result
     */
    record SetPipeFamily(
            NetworkId networkId,
            PipeFamily family,
            Consumer<OperationResult> callback
    ) implements EngineOperation {
        public SetPipeFamily {
            Objects.requireNonNull(networkId, "networkId must not be null");
            Objects.requireNonNull(family, "family must not be null");
        }

        public SetPipeFamily(NetworkId networkId, PipeFamily family) {
            this(networkId, family, null);
        }
    }

    /**
     * Apply an async computation result with version checking.
     *
     * @param type the network type
     * @param expectedVersion the version at computation start
     * @param computation the computation to apply
     * @param callback optional callback with result
     */
    record ApplyAsyncResult(
            ConnectionType type,
            long expectedVersion,
            AsyncComputation computation,
            Consumer<OperationResult> callback
    ) implements EngineOperation {
        public ApplyAsyncResult {
            Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(computation, "computation must not be null");
        }
    }

    /**
     * Batch operation containing multiple operations to execute atomically.
     *
     * @param operations the operations to execute
     */
    record Batch(java.util.List<EngineOperation> operations) implements EngineOperation {
        public Batch {
            operations = java.util.List.copyOf(operations);
        }
    }
}

/**
 * Result of an engine operation.
 */
sealed interface OperationResult {

    /**
     * Operation completed successfully.
     */
    record Success(Object result) implements OperationResult {}

    /**
     * Operation failed due to version mismatch (async result was stale).
     */
    record VersionMismatch(long expected, long actual) implements OperationResult {}

    /**
     * Operation failed with an error.
     */
    record Error(String message, Throwable cause) implements OperationResult {
        public Error(String message) {
            this(message, null);
        }
    }
}

/**
 * Marker interface for async computation results.
 * Implementations carry the computed data to be applied.
 */
interface AsyncComputation {
    /**
     * Apply this computation result to the type state.
     *
     * @param state the type state to modify
     */
    void apply(ConnectionState state);
}
