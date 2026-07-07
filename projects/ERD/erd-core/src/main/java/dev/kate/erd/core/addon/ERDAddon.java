package dev.kate.erd.core.addon;
/**
 * Interface for ERD addons that contribute machines, controllers, or other content.
 * 
 * <p>Addons are loaded from the {@code plugins/ERD/addons/} and {@code plugins/ERD/dev-addons/}
 * directories and can register custom machine definitions, controller definitions, and other
 * content into the ERD system.</p>
 * 
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Addon JAR is loaded from addons folder</li>
 *   <li>{@link #onLoad(AddonContext)} called - register content here</li>
 *   <li>{@link #onEnable()} called - initialize systems</li>
 *   <li>Plugin runs normally</li>
 *   <li>{@link #onDisable()} called - cleanup</li>
 * </ol>
 * 
 * <h2>Example</h2>
 * <pre>{@code
 * public class ReactorAddon implements ERDAddon {
 *     @Override
 *     public AddonInfo getInfo() {
 *         return AddonInfo.builder()
 *             .id("fusion_reactor")
 *             .name("Fusion Reactor")
 *             .version("1.0.0")
 *             .author("ERD Team")
 *             .description("Advanced fusion reactor for power generation")
 *             .build();
 *     }
 *     
 *     @Override
 *     public void onLoad(AddonContext context) {
 *         context.registerMachine(FusionReactorDefinition.INSTANCE);
 *     }
 * }
 * }</pre>
 */
public interface ERDAddon {
    /**
     * Get addon metadata.
     * 
     * @return addon information
     */
    AddonInfo getInfo();
    /**
     * Called when the addon is loaded.
     * Register machines, controllers, and other content here.
     * 
     * @param context the addon context for registration
     */
    void onLoad(AddonContext context);
    /**
     * Called when the addon is enabled (after all addons are loaded).
     * Initialize systems, register event listeners, etc.
     */
    default void onEnable() {
        // Optional override
    }
    /**
     * Called when the addon is disabled (plugin shutdown).
     * Clean up resources here.
     */
    default void onDisable() {
        // Optional override
    }
}
