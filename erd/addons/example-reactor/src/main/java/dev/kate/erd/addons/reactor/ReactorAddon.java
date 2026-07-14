package dev.kate.erd.addons.reactor;
import dev.kate.erd.core.addon.ERDAddon;
import dev.kate.erd.core.addon.AddonContext;
import dev.kate.erd.core.addon.AddonInfo;
/**
 * Example addon demonstrating a complex multi-block machine.
 * 
 * <p>The Fusion Reactor is a 3x3x3 structure that performs nuclear fusion,
 * consuming hydrogen and water while producing helium and energy.</p>
 */
public class ReactorAddon implements ERDAddon {
    @Override
    public AddonInfo getInfo() {
        return AddonInfo.builder()
            .id("fusion_reactor")
            .name("Fusion Reactor")
            .version("1.0.0")
            .author("ERD Team")
            .description("Advanced fusion reactor for power generation")
            .build();
    }
    @Override
    public void onLoad(AddonContext context) {
        // Register the fusion reactor machine
        context.registerMachine(FusionReactorDefinition.INSTANCE);
        context.getLogger().info("Fusion Reactor machine registered");
    }
    @Override
    public void onEnable() {
        // Optional: Initialize any runtime systems
    }
    @Override
    public void onDisable() {
        // Optional: Cleanup
    }
}
