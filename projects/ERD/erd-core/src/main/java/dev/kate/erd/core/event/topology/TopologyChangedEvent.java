package dev.kate.erd.core.event.topology;

import dev.kate.erd.core.event.ErdEvent;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.topology.TopologyResult;

/**
 * Event fired when network topology changes.
 */
public record TopologyChangedEvent(
    ConnectionType layer,
    TopologyResult result
) implements ErdEvent {}
