package dev.kate.erd.core.machine.persistence;

import java.util.Map;

/**
 * Interface for machines and components that have persistent state.
 *
 * <p>This interface replaces the old {@code MachineStateful} interface with
 * a type-safe, versioned approach using {@link StateCodec}.
 *
 * <p>Example implementation:
 * <pre>{@code
 * public class FusionReactorInstance extends BaseMachineInstance
 *         implements Stateful<ReactorState> {
 *
 *     private static final ReactorStateCodec CODEC = new ReactorStateCodec();
 *
 *     private double temperature = 20.0;
 *     private int hydrogenStored = 0;
 *     // ... other fields
 *
 *     @Override
 *     public StateCodec<ReactorState> stateCodec() {
 *         return CODEC;
 *     }
 *
 *     @Override
 *     public ReactorState captureState() {
 *         return new ReactorState(temperature, hydrogenStored, waterStored, hasMeltedDown);
 *     }
 *
 *     @Override
 *     public void applyState(ReactorState state) {
 *         this.temperature = state.temperature();
 *         this.hydrogenStored = state.hydrogenStored();
 *         this.waterStored = state.waterStored();
 *         this.hasMeltedDown = state.hasMeltedDown();
 *     }
 * }
 * }</pre>
 *
 * <p>The persistence type uses these methods:
 * <ol>
 *   <li>On save: {@code codec.encode(captureState())} → persisted map</li>
 *   <li>On load: {@code applyState(codec.decode(persistedMap, version))}</li>
 * </ol>
 *
 * @param <S> the state type
 * @see StateCodec
 */
public interface Stateful<S> {

    /**
     * Returns the codec for serializing this entity's state.
     *
     * @return the state codec
     */
    StateCodec<S> stateCodec();

    /**
     * Captures the current state for persistence.
     *
     * @return the current state
     */
    S captureState();

    /**
     * Applies a loaded state to this entity.
     *
     * @param state the state to apply
     */
    void applyState(S state);

    // ========== Convenience Methods ==========

    /**
     * Encodes the current state to a persistable map.
     *
     * @return the encoded state with version info
     */
    default EncodedState encodeState() {
        StateCodec<S> codec = stateCodec();
        Map<String, Object> data = codec.encode(captureState());
        return new EncodedState(codec.version(), data);
    }

    /**
     * Decodes and applies state from a persisted map.
     *
     * @param encoded the encoded state
     */
    default void decodeAndApplyState(EncodedState encoded) {
        StateCodec<S> codec = stateCodec();
        S state = codec.decode(encoded.data(), encoded.version());
        applyState(state);
    }

    /**
     * Encoded state with version information.
     *
     * @param version the codec version used to encode
     * @param data the encoded data
     */
    record EncodedState(int version, Map<String, Object> data) {
        public EncodedState {
            data = data != null ? Map.copyOf(data) : Map.of();
        }

        /**
         * Creates an empty encoded state.
         */
        public static EncodedState empty() {
            return new EncodedState(0, Map.of());
        }

        /**
         * @return true if this state has no data
         */
        public boolean isEmpty() {
            return data.isEmpty();
        }
    }
}

