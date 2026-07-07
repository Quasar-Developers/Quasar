package dev.kate.erd.core.machine.resource;

/**
 * Interface for blocks/machines that can modify resource flow in PIPE networks.
 *
 * <p>Flow modifiers are applied during the ROUTE phase to adjust
 * transfer amounts, block certain resources, or redirect flow.</p>
 *
 * <h2>Examples</h2>
 * <ul>
 *   <li><b>Valve:</b> Can block or limit flow when closed</li>
 *   <li><b>Pump:</b> Can boost flow rate beyond normal limits</li>
 *   <li><b>Filter:</b> Can block specific resource types</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * class Valve implements FlowModifier {
 *     private boolean open = true;
 *
 *     @Override
 *     public int modifyFlowRate(ResourceType type, int proposedRate) {
 *         return open ? proposedRate : 0;  // Block when closed
 *     }
 * }
 * }</pre>
 */
public interface FlowModifier {

    /**
     * Modify the flow rate for a specific resource type.
     * Called during the ROUTE phase for each transfer.
     *
     * @param type the resource type being transferred
     * @param proposedRate the rate proposed by the network
     * @return the modified rate (0 to block, higher to boost)
     */
    int modifyFlowRate(ResourceType type, int proposedRate);

    /**
     * Check if this modifier is currently active.
     * Inactive modifiers are skipped during routing.
     *
     * @return true if this modifier should affect flow
     */
    default boolean isActive() {
        return true;
    }

    /**
     * Get priority for this modifier (lower = applied first).
     * Default is 100. Valves should be lower (e.g., 50) to apply before pumps.
     *
     * @return priority value
     */
    default int getPriority() {
        return 100;
    }
}
