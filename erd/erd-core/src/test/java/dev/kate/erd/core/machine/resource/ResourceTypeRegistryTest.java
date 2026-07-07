package dev.kate.erd.core.machine.resource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceTypeRegistryTest {

    @Test
    void coreResources_areRegistered() {
        assertThat(ResourceType.get("water")).isPresent();
        assertThat(ResourceType.get("lava")).isPresent();
        assertThat(ResourceType.get("hydrogen")).isPresent();
    }

    @Test
    void register_customResource_success() {
        ResourceType custom = ResourceType.register("test:oil", "🛢", "Oil", false, true);

        assertThat(ResourceType.get("test:oil")).isPresent();
        assertThat(ResourceType.get("test:oil").get()).isEqualTo(custom);
        assertThat(custom.isLiquid()).isTrue();
        assertThat(custom.isGas()).isFalse();
    }

    @Test
    void register_duplicateId_throwsException() {
        assertThatThrownBy(() -> 
            ResourceType.register("water", "X", "Fake Water", false, true)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void values_returnsAllRegistered() {
        int initialCount = ResourceType.values().size();
        ResourceType.register("test:new", "N", "New", false, false);
        
        assertThat(ResourceType.values()).hasSize(initialCount + 1);
    }
}
