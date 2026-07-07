package dev.kate.erd.devaddons.generator;
import dev.kate.erd.core.addon.ERDAddon;
import dev.kate.erd.core.addon.AddonContext;
import dev.kate.erd.core.addon.AddonInfo;
/**
 * Development addon providing infinite resource generation for testing.
 * 
 * <p>This is a DEV tool - not intended for production gameplay.</p>
 */
public class GeneratorAddon implements ERDAddon {
    @Override
    public AddonInfo getInfo() {
        return AddonInfo.builder()
            .id("resource_generator")
            .name("Resource Generator")
            .version("1.0.0-DEV")
            .author("ERD Team")
            .description("Infinite resource generator for testing")
            .build();
    }
    @Override
    public void onLoad(AddonContext context) {
        context.registerMachine(ResourceGeneratorDefinition.INSTANCE);
        context.getLogger().info("Resource Generator (DEV) registered");
    }
}
