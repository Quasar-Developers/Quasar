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
 * Definition for the Fusion Reactor multiblock.
 *
 * <h2>Structure</h2>
 * <p>3x3x3 cube (hollow or filled, simplified for test).</p>
 * <ul>
 *   <li>Center: Core (minecraft:beacon)</li>
 *   <li>Sides: Casing (minecraft:iron_block)</li>
 *   <li>Ports: Glass (minecraft:glass) - 4 ports on the middle type sides</li>
 * </ul>
 */
public class FusionReactorDefinition implements MachineDefinition<FusionReactorInstance> {

    public static final String TYPE_ID = "erd:fusion_reactor";
    public static final String DISPLAY_NAME = "Fusion Reactor";
    public static final String CORE_BLOCK = "minecraft:beacon";
    public static final String CASING_BLOCK = "minecraft:iron_block";
    public static final String PORT_BLOCK = "minecraft:glass";

    public static final FusionReactorDefinition INSTANCE = new FusionReactorDefinition();

    private FusionReactorDefinition() {}

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
        return 1;
    }

    @Override
    public String controllerBlockKey() {
        return CORE_BLOCK;
    }

    @Override
    public StructureBounds detectionBounds() {
        return StructureBounds.symmetric(1); // 3x3x3 centered on core
    }

    @Override
    public ValidationResult validate(StructureSnapshot snapshot) {
        BlockPos origin = snapshot.origin();
        Set<BlockPos> occupied = new HashSet<>();
        List<Endpoint> endpoints = new ArrayList<>();

        // Check core
        if (!snapshot.isBlockType(origin, CORE_BLOCK)) {
            return ValidationResult.invalid("Missing core", Set.of(origin));
        }
        occupied.add(origin);

        // Check 3x3x3 structure
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    BlockPos pos = origin.offset(x, y, z);
                    StructureSnapshot.BlockData blockData = snapshot.getBlock(pos);
                    String block = blockData != null ? blockData.typeKey() : "";

                    // Middle type sides are ports
                    if (y == 0 && ((x == 0 && z != 0) || (z == 0 && x != 0))) {
                        if (!block.equals(PORT_BLOCK)) {
                            return ValidationResult.invalid("Expected port at " + x + "," + y + "," + z, Set.of(pos));
                        }
                        // Add endpoint
                        endpoints.add(new BaseEndpoint(pos, ConnectionType.PIPE, EndpointRole.CONSUMER)); // Simplified
                    } else {
                        // Everything else is casing
                        if (!block.equals(CASING_BLOCK)) {
                            return ValidationResult.invalid("Expected casing at " + x + "," + y + "," + z, Set.of(pos));
                        }
                    }
                    occupied.add(pos);
                }
            }
        }

        return ValidationResult.valid(occupied, endpoints);
    }

    @Override
    public FusionReactorInstance createInstance(MachineId id, Structure structure) {
        return new FusionReactorInstance(id, this, structure);
    }

    @Override
    public List<PortDefinition> portDefinitions() {
        return List.of(); // Dynamic ports based on structure
    }
}
