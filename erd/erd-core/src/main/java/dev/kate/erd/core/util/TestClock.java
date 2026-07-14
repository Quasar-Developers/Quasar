package dev.kate.erd.core.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A controllable clock implementation for testing.
 *
 * <p>Allows test code to manually advance time and verify
 * time-dependent behavior in a deterministic manner.
 *
 * <p>Thread-safety: This implementation is thread-safe using atomic operations.
 */
public final class TestClock implements Clock {

    private final AtomicLong currentTime;

    /**
     * Creates a TestClock starting at the specified time.
     *
     * @param initialTime the initial time in milliseconds
     */
    public TestClock(long initialTime) {
        this.currentTime = new AtomicLong(initialTime);
    }

    /**
     * Creates a TestClock starting at time 0.
     */
    public TestClock() {
        this(0L);
    }

    @Override
    public long nowMillis() {
        return currentTime.get();
    }

    /**
     * Sets the current time to a specific value.
     *
     * @param time the new current time
     */
    public void setTime(long time) {
        currentTime.set(time);
    }

    /**
     * Advances the clock by the specified amount.
     *
     * @param millis the number of milliseconds to advance
     * @return the new current time
     */
    public long advance(long millis) {
        return currentTime.addAndGet(millis);
    }

    /**
     * Returns the next unique time by incrementing the clock by 1ms.
     * Useful for creating ordered timestamps in tests.
     *
     * @return a unique incrementing time value
     */
    public long nextTime() {
        return currentTime.incrementAndGet();
    }
}
