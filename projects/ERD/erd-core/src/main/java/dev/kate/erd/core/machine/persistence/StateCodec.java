package dev.kate.erd.core.machine.persistence;

import java.util.Map;

/**
 * Type-safe codec for serializing and deserializing machine/component state.
 *
 * <p>Unlike the raw {@code Map<String, Object>} approach, StateCodec provides:
 * <ul>
 *   <li>Type safety — compile-time checking of state structure</li>
 *   <li>Versioning — automatic migration of old data formats</li>
 *   <li>Documentation — state structure is explicit in the type</li>
 *   <li>Validation — decode can reject malformed data</li>
 * </ul>
 *
 * <p>Example implementation:
 * <pre>{@code
 * public record ReactorState(
 *     double temperature,
 *     int hydrogenStored,
 *     int waterStored,
 *     boolean hasMeltedDown
 * ) {}
 *
 * public class ReactorStateCodec implements StateCodec<ReactorState> {
 *
 *     @Override
 *     public int version() {
 *         return 2; // Bump when format changes
 *     }
 *
 *     @Override
 *     public Map<String, Object> encode(ReactorState state) {
 *         return Map.of(
 *             "temperature", state.temperature(),
 *             "hydrogen", state.hydrogenStored(),
 *             "water", state.waterStored(),
 *             "meltdown", state.hasMeltedDown()
 *         );
 *     }
 *
 *     @Override
 *     public ReactorState decode(Map<String, Object> data, int dataVersion) {
 *         // Handle migration from older versions
 *         if (dataVersion < 2) {
 *             // Version 1 didn't have meltdown field
 *             return new ReactorState(
 *                 getDouble(data, "temperature", 20.0),
 *                 getInt(data, "hydrogen", 0),
 *                 getInt(data, "water", 0),
 *                 false // Default for migrated data
 *             );
 *         }
 *
 *         return new ReactorState(
 *             getDouble(data, "temperature", 20.0),
 *             getInt(data, "hydrogen", 0),
 *             getInt(data, "water", 0),
 *             getBoolean(data, "meltdown", false)
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p>Thread-safety: Codecs should be stateless and thread-safe.
 *
 * @param <S> the state type this codec handles
 */
public interface StateCodec<S> {

    /**
     * Returns the current version of the state format.
     *
     * <p>Increment this when the state structure changes in a way that
     * requires migration logic in {@link #decode}.
     *
     * @return the current version number (must be >= 1)
     */
    int version();

    /**
     * Encodes state to a serializable map.
     *
     * <p>The map values should be JSON-compatible types:
     * <ul>
     *   <li>Primitives: String, Number, Boolean</li>
     *   <li>Collections: List, Map</li>
     *   <li>null</li>
     * </ul>
     *
     * @param state the state to encode
     * @return the encoded map
     */
    Map<String, Object> encode(S state);

    /**
     * Decodes state from a serialized map.
     *
     * <p>The {@code dataVersion} parameter indicates which version of the format
     * was used to encode the data. Use this to apply migration logic for old data.
     *
     * @param data the encoded data
     * @param dataVersion the version the data was encoded with
     * @return the decoded state
     * @throws IllegalArgumentException if the data is malformed
     */
    S decode(Map<String, Object> data, int dataVersion);

    /**
     * Creates a default state instance.
     *
     * <p>Used when no persisted state exists (new machine) or when
     * migration fails and a fallback is needed.
     *
     * @return the default state
     */
    S defaultState();

    // ========== Helper Methods ==========

    /**
     * Safely gets a String value from the map.
     */
    default String getString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        return value instanceof String s ? s : defaultValue;
    }

    /**
     * Safely gets an int value from the map.
     */
    default int getInt(Map<String, Object> data, String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }

    /**
     * Safely gets a long value from the map.
     */
    default long getLong(Map<String, Object> data, String key, long defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        return defaultValue;
    }

    /**
     * Safely gets a double value from the map.
     */
    default double getDouble(Map<String, Object> data, String key, double defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return defaultValue;
    }

    /**
     * Safely gets a boolean value from the map.
     */
    default boolean getBoolean(Map<String, Object> data, String key, boolean defaultValue) {
        Object value = data.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        return defaultValue;
    }

    /**
     * Safely gets a List value from the map.
     */
    @SuppressWarnings("unchecked")
    default <T> java.util.List<T> getList(Map<String, Object> data, String key, java.util.List<T> defaultValue) {
        Object value = data.get(key);
        if (value instanceof java.util.List<?> list) {
            return (java.util.List<T>) list;
        }
        return defaultValue;
    }

    /**
     * Safely gets a Map value from the map.
     */
    @SuppressWarnings("unchecked")
    default <K, V> Map<K, V> getMap(Map<String, Object> data, String key, Map<K, V> defaultValue) {
        Object value = data.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<K, V>) map;
        }
        return defaultValue;
    }
}

