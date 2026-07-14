package dev.kate.erd.core.machine.test;

import dev.kate.erd.core.endpoint.BaseEndpoint;
import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.endpoint.EndpointRole;
import dev.kate.erd.core.machine.*;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.MachineId;

import java.util.*;

/**
 * Definition for a Resource Generator - a simple 1x1x1 block machine.
 *
 * <h2>Structure</h2>
 * <p>Single block: Diamond Block (minecraft:diamond_block)</p>
 *
 * <h2>Behavior</h2>
 * <p>Produces infinite amounts of hydrogen and water for testing purposes.
 * Has one PIPE output that can provide either resource.</p>
 *
 * <h2>Usage</h2>
 * <p>Place a diamond block, connect to pipe network. Use commands or
 * the instance methods to configure output type and rate.</p>
 */
public class ResourceGeneratorDefinition implements MachineDefinition<ResourceGeneratorInstance> {

    public static final String TYPE_ID = "erd:resource_generator";
    public static final String DISPLAY_NAME = "Resource Generator (Test)";
    public static final String BLOCK_KEY = "minecraft:diamond_block";

    public static final ResourceGeneratorDefinition INSTANCE = new ResourceGeneratorDefinition();

    private ResourceGeneratorDefinition() {}

    @Override
    public String typeId() {
        return TYPE_ID;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public int maxControllers() {
        return 5; // Test machine, can have multiple controllers
    }

    @Override
    public String controllerBlockKey() {
        return BLOCK_KEY;
    }

    @Override
    public StructureBounds detectionBounds() {
        return StructureBounds.singleBlock();
    }

    @Override
    public ValidationResult validate(StructureSnapshot snapshot) {
        BlockPos origin = snapshot.origin();

        if (!snapshot.isBlockType(origin, BLOCK_KEY)) {
            return ValidationResult.invalid(
                "Expected " + BLOCK_KEY,
                Set.of(origin)
            );
        }

        // Create output endpoints on all 6 sides
        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.add(new BaseEndpoint(origin, ConnectionType.PIPE, EndpointRole.PROVIDER));

        return ValidationResult.valid(Set.of(origin), endpoints);
    }

    @Override
    public ResourceGeneratorInstance createInstance(MachineId id, Structure structure) {
        return new ResourceGeneratorInstance(
            id,
            this,
            structure.positions().iterator().next(), // anchor
            structure.positions(),
            structure.endpoints()
        );
    }

    @Override
    public List<PortDefinition> portDefinitions() {
        BlockPos dummy = new BlockPos(UUID.randomUUID(), 0, 0, 0);
        return List.of(
            new PortDefinition(dummy, ConnectionType.PIPE, EndpointRole.PROVIDER, Optional.of("Resource Output"))
        );
    }
}
