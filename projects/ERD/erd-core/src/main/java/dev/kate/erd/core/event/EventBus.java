package dev.kate.erd.core.event;

import dev.kate.erd.core.util.ErdLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Central event bus for the ERD system.
 */
public final class EventBus {

    private final ErdLogger logger;
    private final Map<Class<? extends ErdEvent>, List<Consumer<? extends ErdEvent>>> listeners = new ConcurrentHashMap<>();

    public EventBus(ErdLogger logger) {
        this.logger = Objects.requireNonNull(logger);
    }

    @SuppressWarnings("unchecked")
    public <T extends ErdEvent> void register(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }

    @SuppressWarnings("unchecked")
    public void dispatch(ErdEvent event) {
        var eventListeners = listeners.get(event.getClass());
        if (eventListeners != null) {
            for (var listener : eventListeners) {
                try {
                    ((Consumer<ErdEvent>) listener).accept(event);
                } catch (Exception e) {
                    logger.error("Error handling event: %s", e.getMessage());
                }
            }
        }
    }
}
