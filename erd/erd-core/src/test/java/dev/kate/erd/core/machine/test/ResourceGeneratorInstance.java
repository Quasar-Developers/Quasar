package dev.kate.erd.core.machine.test;

import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.machine.BaseMachineInstance;
import dev.kate.erd.core.machine.MachineStatus;
import dev.kate.erd.core.machine.resource.ResourceProvider;
import dev.kate.erd.core.machine.resource.ResourceType;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.MachineId;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resource Generator - produces a single resource type infinitely.
 *
 * <p>Only produces ONE type at a time (hydrogen OR water).
 * When switching types, the buffer is cleared.
 *
 * <p>Implements {@link ResourceProvider} for the new pipe network system.
 */
public class ResourceGeneratorInstance extends BaseMachineInstance implements ResourceProvider {

    public enum OutputType {
        HYDROGEN,
        WATER
    }

    private OutputType outputType = OutputType.HYDROGEN;
    private int outputRate = 100;
    private int buffer = 0;

    public static final int MAX_BUFFER = 10000;

    private long totalProduced = 0;

    public ResourceGeneratorInstance(
            MachineId id,
            ResourceGeneratorDefinition definition,
            BlockPos anchorPosition,
            Set<BlockPos> occupiedPositions,
            List<Endpoint> endpoints) {
        super(id, definition, anchorPosition, occupiedPositions, endpoints);
        setStatus(MachineStatus.RUNNING);
    }

    public void setOutputType(OutputType type) {
        if (this.outputType != type) {
            this.outputType = type;
            this.buffer = 0;
        }
    }

    public OutputType getOutputType() { return outputType; }

    public void setOutputRate(int rate) { this.outputRate = Math.max(0, rate); }
    public int getOutputRate() { return outputRate; }

    public int getBuffer() { return buffer; }
    public int getHydrogenBuffer() { return outputType == OutputType.HYDROGEN ? buffer : 0; }
    public int getWaterBuffer() { return outputType == OutputType.WATER ? buffer : 0; }

    public int extract(int maxAmount) {
        int extracted = Math.min(maxAmount, buffer);
        buffer -= extracted;
        return extracted;
    }

    public int extractHydrogen(int maxAmount) {
        return outputType == OutputType.HYDROGEN ? extract(maxAmount) : 0;
    }

    public int extractWater(int maxAmount) {
        return outputType == OutputType.WATER ? extract(maxAmount) : 0;
    }

    public long getTotalProduced() { return totalProduced; }
    public long getTotalHydrogenProduced() { return outputType == OutputType.HYDROGEN ? totalProduced : 0; }
    public long getTotalWaterProduced() { return outputType == OutputType.WATER ? totalProduced : 0; }

    @Override
    protected void doTick() {
        int canProduce = Math.min(outputRate, MAX_BUFFER - buffer);
        buffer += canProduce;
        totalProduced += canProduce;
    }

    // === ResourceProvider Interface ===

    @Override
    public Map<ResourceType, Integer> getAvailableResources() {
        ResourceType type = toResourceType(outputType);
        return buffer > 0 ? Map.of(type, buffer) : Map.of();
    }

    @Override
    public int extractResource(ResourceType type, int maxAmount) {
        if (!type.equals(toResourceType(outputType))) return 0;
        return extract(maxAmount);
    }

    @Override
    public ResourceType getPrimaryResourceType() {
        return toResourceType(outputType);
    }

    /**
     * Convert from internal OutputType to ResourceType.
     */
    public static ResourceType toResourceType(OutputType type) {
        return switch (type) {
            case HYDROGEN -> ResourceType.HYDROGEN;
            case WATER -> ResourceType.WATER;
        };
    }

    /**
     * Convert from ResourceType to internal OutputType.
     */
    public static OutputType fromResourceType(ResourceType type) {
        if (type.equals(ResourceType.HYDROGEN)) return OutputType.HYDROGEN;
        if (type.equals(ResourceType.WATER)) return OutputType.WATER;
        throw new IllegalArgumentException("Unsupported resource type: " + type);
    }

    @Override
    public String toString() {
        return String.format("ResourceGenerator[type=%s, buffer=%d]", outputType, buffer);
    }
}
