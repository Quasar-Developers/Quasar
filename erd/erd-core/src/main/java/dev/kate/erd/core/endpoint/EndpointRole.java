package dev.kate.erd.core.endpoint;

/**
 * Defines the behavioral role of an endpoint in network interactions.
 *
 * <p>The role determines how an endpoint participates in resource flow
 * within its network type.
 *
 * <p>Thread-safety: Enum values are inherently thread-safe.
 */
public enum EndpointRole {
    /**
     * Provides resources to the network (generators, pumps, data sources).
     */
    PROVIDER,

    /**
     * Consumes resources from the network (machines, drains, data sinks).
     */
    CONSUMER,

    /**
     * Stores resources and can both provide and consume (batteries, tanks).
     */
    STORAGE,

    /**
     * Bidirectional port that can both send and receive (data transceivers).
     */
    BIDIRECTIONAL,

    /**
     * Passive endpoint that neither produces nor consumes, but may observe
     * or relay (monitors, displays, passthrough connections).
     */
    PASSIVE
}
