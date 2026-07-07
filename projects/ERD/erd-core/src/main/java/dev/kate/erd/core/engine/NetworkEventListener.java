package dev.kate.erd.core.engine;

import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.topology.TopologyResult;

/**
 * Listener for network topology change events.
 *
 * <p>Implementations can react to network creation, merging, splitting,
 * and dissolution across all layers.
 *
 * <p>Thread-safety: Listeners are called on the processing thread.
 * Implementations should not block or perform heavy computation.
 */
@FunctionalInterface
public interface NetworkEventListener {

    /**
     * Called when a topology change occurs in any type.
     *
     * @param layer the affected type
     * @param result the topology change result
     */
    void onTopologyChanged(ConnectionType layer, TopologyResult result);
}
