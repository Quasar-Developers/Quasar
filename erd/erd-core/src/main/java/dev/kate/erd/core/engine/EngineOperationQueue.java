package dev.kate.erd.core.engine;

import dev.kate.erd.core.util.ErdLogger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Queue for serializing engine operations.
 *
 * <p>All mutations to the NetworkEngine must go through this queue to ensure
 * thread-safe state changes. Operations are enqueued from any thread and
 * processed on the designated execution thread (typically the main server thread).
 *
 * <p>Thread-safety: The enqueue method is thread-safe. The process methods
 * should only be called from a single designated thread.
 */
public final class EngineOperationQueue {

    private final Deque<EngineOperation> queue = new ArrayDeque<>();
    private final ErdLogger logger;
    private final Object lock = new Object();

    /**
     * Creates a new operation queue.
     *
     * @param logger the logger to use
     */
    public EngineOperationQueue(ErdLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

    /**
     * Enqueues an operation for later processing.
     * This method is thread-safe.
     *
     * @param operation the operation to enqueue
     */
    public void enqueue(EngineOperation operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        synchronized (lock) {
            queue.addLast(operation);
        }
        logger.debug("Enqueued operation: %s", operation.getClass().getSimpleName());
    }

    /**
     * Polls the next operation from the queue.
     * This method should only be called from the processing thread.
     *
     * @return the next operation, or null if queue is empty
     */
    public EngineOperation poll() {
        synchronized (lock) {
            return queue.pollFirst();
        }
    }

    /**
     * Checks if the queue has pending operations.
     *
     * @return true if there are operations to process
     */
    public boolean hasPending() {
        synchronized (lock) {
            return !queue.isEmpty();
        }
    }

    /**
     * Returns the current queue size.
     *
     * @return number of pending operations
     */
    public int size() {
        synchronized (lock) {
            return queue.size();
        }
    }

    /**
     * Clears all pending operations.
     * Use with caution - typically only for shutdown.
     */
    public void clear() {
        synchronized (lock) {
            queue.clear();
        }
        logger.debug("Operation queue cleared");
    }
}
