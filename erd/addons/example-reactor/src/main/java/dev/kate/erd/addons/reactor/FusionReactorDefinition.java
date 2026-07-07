package dev.kate.erd.addons.reactor;

import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.endpoint.EndpointRole;
import dev.kate.erd.core.endpoint.ResourceEndpoint;
import dev.kate.erd.core.machine.*;
import dev.kate.erd.core.machine.resource.ResourceType;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.MachineId;

import java.util.*;

/**
 * Definition for a Fusion Reactor - a 3x3x3 multiblock structure.
 */
public class FusionReactorDefinition implements MachineDefinition<FusionReactorInstance> {

    public static final String TYPE_ID = "erd:fusion_reactor";
    public static final String DISPLAY_NAME = "Fusion Reactor";

    public static final String CORE_BLOCK = "minecraft:redstone_block";
    public static final String CASING_BLOCK = "minecraft:iron_block";

    // Terracotta blocks for directional endpoints
    public static final String WATER_INPUT_BLOCK = "minecraft:blue_terracotta";
    public static final String HYDROGEN_INPUT_BLOCK = "minecraft:light_blue_terracotta";
    public static final String HELIUM_OUTPUT_BLOCK = "minecraft:white_terracotta";
    public static final String ENERGY_OUTPUT_BLOCK = "minecraft:yellow_terracotta";

    public static final FusionReactorDefinition INSTANCE = new FusionReactorDefinition();

    private FusionReactorDefinition() {}

    @Override
    public String typeId() { return TYPE_ID; }

    @Override
    public String displayName() { return DISPLAY_NAME; }

    @Override
    public int maxControllers() { return 1; }

    @Override
    public String controllerBlockKey() { return CORE_BLOCK; }

    @Override
    public StructureBounds detectionBounds() {
        return new StructureBounds(-1, -1, -1, 1, 1, 1);
    }

    @Override
    public ValidationResult validate(StructureSnapshot snapshot) {
        BlockPos origin = snapshot.origin();
        Set<BlockPos> occupied = new HashSet<>();
        List<Endpoint> endpoints = new ArrayList<>();

        if (!snapshot.isBlockType(origin, CORE_BLOCK)) {
            return ValidationResult.invalid("Core must be " + CORE_BLOCK, Set.of(origin));
        }
        occupied.add(origin);

        // Track found endpoint blocks for validation
        Map<String, BlockPos> foundEndpoints = new HashMap<>();

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    BlockPos pos = origin.offset(dx, dy, dz);

                    // Check for endpoint blocks in the middle type (dy == 0) on cardinal directions
                    if (dy == 0 && ((dx == 0 && dz != 0) || (dz == 0 && dx != 0))) {
                        String blockType = null;
                        String endpointType = null;

                        if (snapshot.isBlockType(pos, WATER_INPUT_BLOCK)) {
                            blockType = WATER_INPUT_BLOCK;
                            endpointType = "water_input";
                        } else if (snapshot.isBlockType(pos, HYDROGEN_INPUT_BLOCK)) {
                            blockType = HYDROGEN_INPUT_BLOCK;
                            endpointType = "hydrogen_input";
                        } else if (snapshot.isBlockType(pos, HELIUM_OUTPUT_BLOCK)) {
                            blockType = HELIUM_OUTPUT_BLOCK;
                            endpointType = "helium_output";
                        } else if (snapshot.isBlockType(pos, ENERGY_OUTPUT_BLOCK)) {
                            blockType = ENERGY_OUTPUT_BLOCK;
                            endpointType = "energy_output";
                        } else if (snapshot.isBlockType(pos, CASING_BLOCK)) {
                            blockType = CASING_BLOCK;
                        } else {
                            return ValidationResult.invalid(
                                String.format("Expected endpoint or casing at offset (%d,%d,%d)", dx, dy, dz),
                                Set.of(pos));
                        }

                        if (endpointType != null) {
                            if (foundEndpoints.containsKey(endpointType)) {
                                return ValidationResult.invalid(
                                    String.format("Duplicate %s block found", endpointType.replace("_", " ")),
                                    Set.of(pos, foundEndpoints.get(endpointType)));
                            }
                            foundEndpoints.put(endpointType, pos);
                        }
                    } else {
                        // All other positions must be casing
                        if (!snapshot.isBlockType(pos, CASING_BLOCK)) {
                            return ValidationResult.invalid(
                                String.format("Expected %s at offset (%d,%d,%d)", CASING_BLOCK, dx, dy, dz),
                                Set.of(pos));
                        }
                    }
                    occupied.add(pos);
                }
            }
        }

        // Ensure all required endpoints are present
        if (!foundEndpoints.containsKey("water_input")) {
            return ValidationResult.invalid("Missing water input (blue terracotta)", Set.of(origin));
        }
        if (!foundEndpoints.containsKey("hydrogen_input")) {
            return ValidationResult.invalid("Missing hydrogen input (light blue terracotta)", Set.of(origin));
        }
        if (!foundEndpoints.containsKey("helium_output")) {
            return ValidationResult.invalid("Missing helium output (white terracotta)", Set.of(origin));
        }
        if (!foundEndpoints.containsKey("energy_output")) {
            return ValidationResult.invalid("Missing energy output (yellow terracotta)", Set.of(origin));
        }

        // Create resource-specific endpoints at detected positions
        endpoints.add(new ResourceEndpoint(foundEndpoints.get("water_input"), ConnectionType.PIPE, EndpointRole.CONSUMER, ResourceType.WATER));
        endpoints.add(new ResourceEndpoint(foundEndpoints.get("hydrogen_input"), ConnectionType.PIPE, EndpointRole.CONSUMER, ResourceType.HYDROGEN));
        endpoints.add(new ResourceEndpoint(foundEndpoints.get("helium_output"), ConnectionType.PIPE, EndpointRole.PROVIDER, ResourceType.HELIUM));
        endpoints.add(new ResourceEndpoint(foundEndpoints.get("energy_output"), ConnectionType.POWER, EndpointRole.PROVIDER, ResourceType.ENERGY));

        return ValidationResult.valid(occupied, endpoints);
    }


    @Override
    public FusionReactorInstance createInstance(MachineId id, Structure structure) {
        return new FusionReactorInstance(id, this, structure);
    }

    @Override
    public List<PortDefinition> portDefinitions() {
        // Port positions are dynamic based on terracotta placement
        // Providing example positions (all cardinal directions possible)
        BlockPos origin = new BlockPos(UUID.randomUUID(), 0, 0, 0);
        return List.of(
            new PortDefinition(origin.offset(0, 0, -1), ConnectionType.PIPE, EndpointRole.CONSUMER, Optional.of("Water Input (Blue Terracotta)")),
            new PortDefinition(origin.offset(0, 0, 1), ConnectionType.PIPE, EndpointRole.CONSUMER, Optional.of("Hydrogen Input (Light Blue Terracotta)")),
            new PortDefinition(origin.offset(1, 0, 0), ConnectionType.PIPE, EndpointRole.PROVIDER, Optional.of("Helium Output (White Terracotta)")),
            new PortDefinition(origin.offset(-1, 0, 0), ConnectionType.POWER, EndpointRole.PROVIDER, Optional.of("Energy Output (Yellow Terracotta)"))
        );
    }
}
