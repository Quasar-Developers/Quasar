package dev.kate.erd.core.event;

/**
 * Base interface for all ERD events.
 */
public interface ErdEvent {
    /**
     * @return the timestamp when this event was created
     */
    default long timestamp() {
        return System.currentTimeMillis();
    }
}
