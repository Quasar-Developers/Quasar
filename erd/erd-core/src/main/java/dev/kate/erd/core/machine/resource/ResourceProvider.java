package dev.kate.erd.core.machine.resource;

import java.util.Map;

/**
 * Interface for machines that can provide resources to PIPE networks.
 *
 * <p>Providers announce what resources they have available, and the network
 * extracts from them when consumers request those resources.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * class MyGenerator implements ResourceProvider {
 *     @Override
 *     public Map<ResourceType, Integer> getAvailableResources() {
 *         return Map.of(ResourceType.HYDROGEN, buffer);
 *     }
 *
 *     @Override
 *     public int extractResource(ResourceType type, int maxAmount) {
 *         if (type != ResourceType.HYDROGEN) return 0;
 *         int extracted = Math.min(maxAmount, buffer);
 *         buffer -= extracted;
 *         return extracted;
 *     }
 * }
 * }</pre>
 */
public interface ResourceProvider {

    /**
     * Get all resources this provider can supply.
     * Called each tick during the COLLECT phase.
     *
     * @return map of resource type to available amount
     */
    Map<ResourceType, Integer> getAvailableResources();

    /**
     * Extract a resource from this provider.
     * Called during the EXECUTE phase after routing is determined.
     *
     * @param type the resource type to extract
     * @param maxAmount maximum amount to extract
     * @return actual amount extracted (may be less than requested)
     */
    int extractResource(ResourceType type, int maxAmount);

    /**
     * Get the primary resource type this provider outputs.
     * Used for network locking - the first provider determines network type.
     *
     * @return the primary resource type, or null if dynamic
     */
    default ResourceType getPrimaryResourceType() {
        Map<ResourceType, Integer> available = getAvailableResources();
        return available.keySet().stream().findFirst().orElse(null);
    }
}
