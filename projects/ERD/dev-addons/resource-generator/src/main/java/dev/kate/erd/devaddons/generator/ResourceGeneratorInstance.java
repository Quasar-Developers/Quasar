package dev.kate.erd.devaddons.generator;

import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.machine.BaseMachineInstance;
import dev.kate.erd.core.machine.MachineStatus;
import dev.kate.erd.core.machine.resource.ResourceProvider;
import dev.kate.erd.core.machine.resource.ResourceType;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.MachineId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resource Generator - produces multiple resource types infinitely.
 *
 * <p>Produces both hydrogen and water simultaneously, allowing connected
 * networks to extract whichever resource they need.</p>
 *
 * <p>Implements {@link ResourceProvider} for the new pipe network system.
 */
public class ResourceGeneratorInstance extends BaseMachineInstance implements ResourceProvider {

    private int outputRate = 100;
    private int hydrogenBuffer = 0;
    private int waterBuffer = 0;

    public static final int MAX_BUFFER = 10000;

    private long totalHydrogenProduced = 0;
    private long totalWaterProduced = 0;

    public ResourceGeneratorInstance(
            MachineId id,
            ResourceGeneratorDefinition definition,
            BlockPos anchorPosition,
            Set<BlockPos> occupiedPositions,
            List<Endpoint> endpoints) {
        super(id, definition, anchorPosition, occupiedPositions, endpoints);
        setStatus(MachineStatus.RUNNING);
    }

    public void setOutputRate(int rate) { this.outputRate = Math.max(0, rate); }
    public int getOutputRate() { return outputRate; }

    public int getHydrogenBuffer() { return hydrogenBuffer; }
    public int getWaterBuffer() { return waterBuffer; }

    public int extractHydrogen(int maxAmount) {
        int extracted = Math.min(maxAmount, hydrogenBuffer);
        hydrogenBuffer -= extracted;
        return extracted;
    }

    public int extractWater(int maxAmount) {
        int extracted = Math.min(maxAmount, waterBuffer);
        waterBuffer -= extracted;
        return extracted;
    }

    public long getTotalHydrogenProduced() { return totalHydrogenProduced; }
    public long getTotalWaterProduced() { return totalWaterProduced; }

    @Override
    protected void doTick() {
        // Produce hydrogen
        int canProduceH2 = Math.min(outputRate, MAX_BUFFER - hydrogenBuffer);
        hydrogenBuffer += canProduceH2;
        totalHydrogenProduced += canProduceH2;

        // Produce water
        int canProduceH2O = Math.min(outputRate, MAX_BUFFER - waterBuffer);
        waterBuffer += canProduceH2O;
        totalWaterProduced += canProduceH2O;
    }

    // === ResourceProvider Interface ===

    @Override
    public Map<ResourceType, Integer> getAvailableResources() {
        Map<ResourceType, Integer> available = new HashMap<>();
        if (hydrogenBuffer > 0) {
            available.put(ResourceType.HYDROGEN, hydrogenBuffer);
        }
        if (waterBuffer > 0) {
            available.put(ResourceType.WATER, waterBuffer);
        }
        return available;
    }

    @Override
    public int extractResource(ResourceType type, int maxAmount) {
        if (type.equals(ResourceType.HYDROGEN)) {
            return extractHydrogen(maxAmount);
        } else if (type.equals(ResourceType.WATER)) {
            return extractWater(maxAmount);
        }
        return 0;
    }

    @Override
    public ResourceType getPrimaryResourceType() {
        // Return the resource with more buffer, or hydrogen by default
        if (waterBuffer > hydrogenBuffer) {
            return ResourceType.WATER;
        }
        return ResourceType.HYDROGEN;
    }

    @Override
    public String toString() {
        return String.format("ResourceGenerator[H2=%d, H2O=%d]", hydrogenBuffer, waterBuffer);
    }
}
