package dev.kate.erd.core.util;

/**
 * Abstraction for time operations to enable deterministic testing.
 *
 * <p>The core system uses this interface instead of direct calls to
 * {@link System#currentTimeMillis()} to allow tests to control time
 * and verify time-dependent behavior like leader election ordering.
 *
 * <p>Thread-safety: Implementations should be thread-safe.
 */
@FunctionalInterface
public interface Clock {

    /**
     * Returns the current time in milliseconds since the epoch.
     *
     * @return the current time in milliseconds
     */
    long nowMillis();

    /**
     * Returns a Clock implementation that uses the system clock.
     *
     * @return a system time clock
     */
    static Clock system() {
        return System::currentTimeMillis;
    }

    /**
     * Returns a fixed Clock for testing that always returns the specified time.
     *
     * @param fixedTime the fixed time to return
     * @return a clock that always returns fixedTime
     */
    static Clock fixed(long fixedTime) {
        return () -> fixedTime;
    }
}
