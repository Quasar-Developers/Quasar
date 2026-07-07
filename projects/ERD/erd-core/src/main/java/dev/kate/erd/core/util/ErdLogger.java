package dev.kate.erd.core.util;

/**
 * Logging abstraction for the ERD core module.
 *
 * <p>This interface allows the core module to log messages without
 * depending on Bukkit's logging infrastructure. The Bukkit adapter
 * module provides an implementation that delegates to the plugin logger.
 *
 * <p>Thread-safety: Implementations should be thread-safe.
 */
public interface ErdLogger {

    /**
     * Logs a debug-level message.
     *
     * @param message the message to log
     */
    void debug(String message);

    /**
     * Logs a debug-level message with formatting.
     *
     * @param format the format string
     * @param args the format arguments
     */
    default void debug(String format, Object... args) {
        debug(String.format(format, args));
    }

    /**
     * Logs an info-level message.
     *
     * @param message the message to log
     */
    void info(String message);

    /**
     * Logs an info-level message with formatting.
     *
     * @param format the format string
     * @param args the format arguments
     */
    default void info(String format, Object... args) {
        info(String.format(format, args));
    }

    /**
     * Logs a warning-level message.
     *
     * @param message the message to log
     */
    void warn(String message);

    /**
     * Logs a warning-level message with formatting.
     *
     * @param format the format string
     * @param args the format arguments
     */
    default void warn(String format, Object... args) {
        warn(String.format(format, args));
    }

    /**
     * Logs a warning-level message with an exception.
     *
     * @param message the message to log
     * @param throwable the exception to log
     */
    void warn(String message, Throwable throwable);

    /**
     * Logs an error-level message.
     *
     * @param message the message to log
     */
    void error(String message);

    /**
     * Logs an error-level message with formatting.
     *
     * @param format the format string
     * @param args the format arguments
     */
    default void error(String format, Object... args) {
        error(String.format(format, args));
    }

    /**
     * Logs an error-level message with an exception.
     *
     * @param message the message to log
     * @param throwable the exception to log
     */
    void error(String message, Throwable throwable);

    /**
     * Returns a no-op logger that discards all messages.
     *
     * @return a silent logger
     */
    static ErdLogger silent() {
        return SilentLogger.INSTANCE;
    }

    /**
     * Returns a logger that writes to System.out/err.
     *
     * @param name the logger name
     * @return a console logger
     */
    static ErdLogger console(String name) {
        return new ConsoleLogger(name);
    }
}

/**
 * A logger that discards all messages.
 */
final class SilentLogger implements ErdLogger {
    static final SilentLogger INSTANCE = new SilentLogger();

    private SilentLogger() {}

    @Override public void debug(String message) {}
    @Override public void info(String message) {}
    @Override public void warn(String message) {}
    @Override public void warn(String message, Throwable throwable) {}
    @Override public void error(String message) {}
    @Override public void error(String message, Throwable throwable) {}
}

/**
 * A logger that writes to the console.
 */
final class ConsoleLogger implements ErdLogger {
    private final String name;

    ConsoleLogger(String name) {
        this.name = name;
    }

    @Override
    public void debug(String message) {
        System.out.println("[" + name + "/DEBUG] " + message);
    }

    @Override
    public void info(String message) {
        System.out.println("[" + name + "/INFO] " + message);
    }

    @Override
    public void warn(String message) {
        System.out.println("[" + name + "/WARN] " + message);
    }

    @Override
    public void warn(String message, Throwable throwable) {
        System.out.println("[" + name + "/WARN] " + message);
        throwable.printStackTrace(System.out);
    }

    @Override
    public void error(String message) {
        System.err.println("[" + name + "/ERROR] " + message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        System.err.println("[" + name + "/ERROR] " + message);
        throwable.printStackTrace(System.err);
    }
}
