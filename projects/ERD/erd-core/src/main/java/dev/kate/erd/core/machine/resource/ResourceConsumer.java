package dev.kate.erd.core.machine.resource;

import java.util.Map;

/**
 * Interface for machines that can consume resources from PIPE networks.
 *
 * <p>Consumers announce what resources they need, and the network
 * delivers to them from connected providers.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * class MyReactor implements ResourceConsumer {
 *     @Override
 *     public Map<ResourceType, Integer> getResourceRequests() {
 *         return Map.of(
 *             ResourceType.HYDROGEN, MAX_HYDROGEN - hydrogenStored,
 *             ResourceType.WATER, MAX_WATER - waterStored
 *         );
 *     }
 *
 *     @Override
 *     public int acceptResource(ResourceType type, int amount) {
 *         switch (type) {
 *             case HYDROGEN -> {
 *                 int accepted = Math.min(amount, MAX_HYDROGEN - hydrogenStored);
 *                 hydrogenStored += accepted;
 *                 return accepted;
 *             }
 *             case WATER -> {
 *                 int accepted = Math.min(amount, MAX_WATER - waterStored);
 *                 waterStored += accepted;
 *                 return accepted;
 *             }
 *             default -> { return 0; }
 *         }
 *     }
 * }
 * }</pre>
 */
public interface ResourceConsumer {

    /**
     * Get all resources this consumer currently needs.
     * Called each tick during the COLLECT phase.
     *
     * @return map of resource type to requested amount
     */
    Map<ResourceType, Integer> getResourceRequests();

    /**
     * Accept a resource delivery.
     * Called during the EXECUTE phase after routing is determined.
     *
     * @param type the resource type being delivered
     * @param amount amount to deliver
     * @return actual amount accepted (may be less than offered)
     */
    int acceptResource(ResourceType type, int amount);

    /**
     * Check if this consumer can accept a specific resource type.
     *
     * @param type the resource type to check
     * @return true if this consumer accepts this resource type
     */
    default boolean canAcceptResource(ResourceType type) {
        Map<ResourceType, Integer> requests = getResourceRequests();
        return requests.containsKey(type) && requests.get(type) > 0;
    }
}
