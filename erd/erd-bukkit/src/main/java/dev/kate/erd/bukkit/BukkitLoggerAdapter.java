package dev.kate.erd.bukkit;

import dev.kate.erd.core.util.ErdLogger;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adapts Java's Logger to the core ErdLogger interface.
 *
 * <p>This allows the core module to log messages through the Bukkit
 * plugin logger without depending on Bukkit directly.
 */
public final class BukkitLoggerAdapter implements ErdLogger {

    private final Logger logger;
    private boolean debugEnabled = false;

    /**
     * Creates a new adapter.
     *
     * @param logger the Bukkit plugin logger
     */
    public BukkitLoggerAdapter(Logger logger) {
        this.logger = logger;
    }

    /**
     * Enables or disables debug logging.
     *
     * @param enabled whether debug is enabled
     */
    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    @Override
    public void debug(String message) {
        if (debugEnabled) {
            logger.info("[DEBUG] " + message);
        }
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warning(message);
    }

    @Override
    public void warn(String message, Throwable throwable) {
        logger.log(Level.WARNING, message, throwable);
    }

    @Override
    public void error(String message) {
        logger.severe(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.log(Level.SEVERE, message, throwable);
    }
}
