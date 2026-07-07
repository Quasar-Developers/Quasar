package dev.kate.erd.core.machine.test;

import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.machine.*;
import dev.kate.erd.core.machine.component.ComponentId;
import dev.kate.erd.core.machine.component.MachineComponent;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ControllerId;
import dev.kate.erd.core.model.MachineId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class FusionReactorInstance implements MachineInstance {
    private final MachineId id;
    private final MachineDefinition<?> definition;
    private final BlockPos anchorPosition;
    private Structure structure;
    private final List<MachineComponent> components = new ArrayList<>();

    /** Creates a FusionReactorInstance from a validated Structure. */
    public FusionReactorInstance(MachineId id, MachineDefinition<?> definition, Structure structure) {
        this.id = id;
        this.definition = definition;
        this.anchorPosition = findAnchor(structure);
        this.structure = structure;
    }

    public FusionReactorInstance(MachineId id, MachineDefinition<?> definition, BlockPos anchorPosition, Set<BlockPos> occupiedPositions, List<Endpoint> endpoints) {
        this.id = id;
        this.definition = definition;
        this.anchorPosition = anchorPosition;
        this.structure = Structure.of(occupiedPositions, endpoints);
    }

    private static BlockPos findAnchor(Structure structure) {
        // If structure is empty (e.g. in tests), return a dummy position
        if (structure.positions().isEmpty()) {
            return new BlockPos(UUID.randomUUID(), 0, 0, 0);
        }

        // Calculate the geometric center of the structure (the core/controller block).
        // Set iteration order is non-deterministic, so we must compute the center
        // rather than relying on iterator().next().
        Set<BlockPos> positions = structure.positions();

        UUID worldId = null;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : positions) {
            worldId = pos.worldId();
            minX = Math.min(minX, pos.x());
            maxX = Math.max(maxX, pos.x());
            minY = Math.min(minY, pos.y());
            maxY = Math.max(maxY, pos.y());
            minZ = Math.min(minZ, pos.z());
            maxZ = Math.max(maxZ, pos.z());
        }

        BlockPos center = new BlockPos(worldId, (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
        if (positions.contains(center)) {
            return center;
        }

        return positions.iterator().next();
    }

    @Override
    public MachineId id() {
        return id;
    }

    @Override
    public MachineDefinition<?> definition() {
        return definition;
    }

    @Override
    public BlockPos anchorPosition() {
        return anchorPosition;
    }

    @Override
    public Structure structure() {
        return structure;
    }

    @Override
    public void updateStructure(Structure newStructure) {
        Structure old = this.structure;
        this.structure = newStructure;
        onStructureChanged(old, newStructure);
    }

    @Override
    public RescanResult rescan(StructureSnapshot snapshot) {
        ValidationResult result = definition.validate(snapshot);
        if (result instanceof ValidationResult.Invalid) {
            return RescanResult.INVALID;
        }
        ValidationResult.Valid valid = (ValidationResult.Valid) result;
        if (valid.structure().positions().equals(structure.positions())) {
            return RescanResult.UNCHANGED;
        }
        updateStructure(valid.structure());
        return RescanResult.RESIZED;
    }

    @Override
    public void onStructureChanged(Structure oldStructure, Structure newStructure) {
        // No-op for test
    }

    @Override
    public List<MachineComponent> components() {
        return List.copyOf(components);
    }

    @Override
    public Optional<MachineComponent> getComponent(ComponentId id) {
        return components.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    @Override
    public void attachComponent(MachineComponent component) {
        components.add(component);
        component.onAttach(this);
    }

    @Override
    public Optional<MachineComponent> detachComponent(ComponentId id) {
        Optional<MachineComponent> found = getComponent(id);
        found.ifPresent(c -> {
            components.remove(c);
            c.onDetach();
        });
        return found;
    }

    @Override
    public void onComponentStructureChanged(MachineComponent component, Structure oldStructure, Structure newStructure) {
        // No-op for test
    }

    @Override
    public MachineStatus status() {
        return MachineStatus.IDLE;
    }

    @Override
    public void tick() {
    }

    @Override
    public void onControlLinkEstablished(ControllerId controllerId) {
    }

    @Override
    public void onControlLinkLost(ControllerId controllerId) {
    }


    @Override
    public void onRemove() {
    }
}
