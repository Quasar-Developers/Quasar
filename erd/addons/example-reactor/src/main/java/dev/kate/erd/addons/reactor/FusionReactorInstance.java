package dev.kate.erd.addons.reactor;

import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.machine.BaseMachineInstance;
import dev.kate.erd.core.machine.MachineStatus;
import dev.kate.erd.core.machine.Structure;
import dev.kate.erd.core.machine.resource.ResourceConsumer;
import dev.kate.erd.core.machine.resource.ResourceProvider;
import dev.kate.erd.core.machine.resource.ResourceType;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ControllerId;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.machine.MachineStateful;
import dev.kate.erd.core.debug.MachineIntrospectable;
import dev.kate.erd.core.debug.DebugSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Instance of a Fusion Reactor with full internal state simulation.
 *
 * <p>Implements both {@link ResourceConsumer} (for hydrogen and water inputs)
 * and {@link ResourceProvider} (for helium and energy outputs).</p>
 *
 * <p>Supports upgradeable structures - capacity scales with structure size.</p>
 */
public class FusionReactorInstance extends BaseMachineInstance implements ResourceConsumer, ResourceProvider, MachineStateful, MachineIntrospectable {

    public static final double AMBIENT_TEMP = 20.0;
    public static final double FUSION_THRESHOLD = 100.0;
    public static final int BASE_MAX_HYDROGEN = 1000;
    public static final int BASE_MAX_WATER = 500;
    public static final int BASE_MAX_HELIUM = 200;
    public static final int BASE_MAX_ENERGY = 10000;

    // Computed from structure size
    private int maxHydrogen = BASE_MAX_HYDROGEN;
    private int maxWater = BASE_MAX_WATER;
    private int maxHelium = BASE_MAX_HELIUM;
    private int maxEnergy = BASE_MAX_ENERGY;

    private double temperature = AMBIENT_TEMP;
    private int hydrogenStored = 0;
    private int waterStored = 0;
    private int heliumBuffer = 0;
    private int energyBuffer = 0;

    private FusionReactorStatus reactorStatus = FusionReactorStatus.COLD;
    private boolean hasMeltedDown = false;

    private long totalHydrogenConsumed = 0;
    private long totalHeliumProduced = 0;
    private long totalEnergyProduced = 0;

    private static final String STATE_VERSION_KEY = "state_version";
    private static final int STATE_VERSION = 1;

    /**
     * Creates a new FusionReactorInstance with a Structure.
     */
    public FusionReactorInstance(MachineId id, FusionReactorDefinition definition, Structure structure) {
        super(id, definition, findAnchor(structure), structure);
        recalculateCapacities();
    }

    /**
     * Legacy constructor for backward compatibility.
     */
    public FusionReactorInstance(MachineId id, FusionReactorDefinition definition,
            BlockPos anchorPosition, Set<BlockPos> occupiedPositions, List<Endpoint> endpoints) {
        super(id, definition, anchorPosition, occupiedPositions, endpoints);
        recalculateCapacities();
    }

    private static BlockPos findAnchor(Structure structure) {
        // Calculate the geometric center of the structure (the core/controller block).
        // Set iteration order is non-deterministic, so we must compute the center
        // rather than relying on iterator().next().
        Set<BlockPos> positions = structure.positions();
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("Structure has no positions");
        }

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

    private void recalculateCapacities() {
        // Scale capacities based on structure size
        int blockCount = structure().metrics().blockCount();
        int baseBlocks = 27; // 3x3x3 default
        double scale = (double) blockCount / baseBlocks;

        maxHydrogen = (int) (BASE_MAX_HYDROGEN * scale);
        maxWater = (int) (BASE_MAX_WATER * scale);
        maxHelium = (int) (BASE_MAX_HELIUM * scale);
        maxEnergy = (int) (BASE_MAX_ENERGY * scale);
    }

    @Override
    public void onStructureChanged(Structure oldStructure, Structure newStructure) {
        recalculateCapacities();
        // Clamp stored values to new maximums
        hydrogenStored = Math.min(hydrogenStored, maxHydrogen);
        waterStored = Math.min(waterStored, maxWater);
        heliumBuffer = Math.min(heliumBuffer, maxHelium);
        energyBuffer = Math.min(energyBuffer, maxEnergy);
    }

    public double getTemperature() { return temperature; }
    public int getHydrogenStored() { return hydrogenStored; }
    public int getWaterStored() { return waterStored; }
    public int getHeliumBuffer() { return heliumBuffer; }
    public int getEnergyBuffer() { return energyBuffer; }
    public FusionReactorStatus getReactorStatus() { return reactorStatus; }
    public boolean hasMeltedDown() { return hasMeltedDown; }
    public long getTotalHydrogenConsumed() { return totalHydrogenConsumed; }
    public long getTotalHeliumProduced() { return totalHeliumProduced; }
    public long getTotalEnergyProduced() { return totalEnergyProduced; }

    // Expose max capacities for external queries
    public int getMaxHydrogen() { return maxHydrogen; }
    public int getMaxWater() { return maxWater; }
    public int getMaxHelium() { return maxHelium; }
    public int getMaxEnergy() { return maxEnergy; }

    public int addHydrogen(int amount) {
        if (hasMeltedDown) return 0;
        int accepted = Math.min(amount, maxHydrogen - hydrogenStored);
        hydrogenStored += accepted;
        return accepted;
    }

    public int addWater(int amount) {
        if (hasMeltedDown) return 0;
        int accepted = Math.min(amount, maxWater - waterStored);
        waterStored += accepted;
        return accepted;
    }

    public int extractHelium(int maxAmount) {
        int extracted = Math.min(maxAmount, heliumBuffer);
        heliumBuffer -= extracted;
        return extracted;
    }

    public int extractEnergy(int maxAmount) {
        int extracted = Math.min(maxAmount, energyBuffer);
        energyBuffer -= extracted;
        return extracted;
    }

    @Override
    protected void doTick() {
        if (hasMeltedDown) return;

        // Heat from hydrogen
        if (hydrogenStored > 0) {
            temperature += hydrogenStored * 0.5;
        }

        // Cooling from water
        if (waterStored > 0 && temperature > AMBIENT_TEMP) {
            temperature -= Math.min(waterStored * 2.0, temperature - AMBIENT_TEMP);
            waterStored = Math.max(0, waterStored - 1);
        }

        // Natural cooling
        if (temperature > AMBIENT_TEMP) {
            temperature = Math.max(AMBIENT_TEMP, temperature - 0.1);
        }

        // Meltdown check
        if (temperature >= 1000.0) {
            triggerMeltdown();
            return;
        }

        // Fusion reaction
        if (temperature >= FUSION_THRESHOLD && hydrogenStored >= 2) {
            int reactions = Math.min(hydrogenStored / 2, 10);
            for (int i = 0; i < reactions; i++) {
                if (hydrogenStored < 2 || heliumBuffer >= maxHelium || energyBuffer >= maxEnergy) break;
                hydrogenStored -= 2;
                heliumBuffer += 1;
                energyBuffer += 100;
                totalHydrogenConsumed += 2;
                totalHeliumProduced += 1;
                totalEnergyProduced += 100;
            }
        }

        updateReactorStatus();
    }

    private void updateReactorStatus() {
        if (hasMeltedDown) {
            reactorStatus = FusionReactorStatus.MELTDOWN;
            setStatus(MachineStatus.ERROR);
        } else if (temperature < FUSION_THRESHOLD) {
            reactorStatus = (hydrogenStored == 0 && waterStored == 0) ? FusionReactorStatus.STARVED : FusionReactorStatus.COLD;
            setStatus(MachineStatus.IDLE);
        } else if (temperature < 500) {
            reactorStatus = FusionReactorStatus.OPTIMAL;
            setStatus(MachineStatus.RUNNING);
        } else if (temperature < 800) {
            reactorStatus = FusionReactorStatus.HOT;
            setStatus(MachineStatus.RUNNING);
        } else {
            reactorStatus = FusionReactorStatus.CRITICAL;
            setStatus(MachineStatus.RUNNING);
        }
    }

    private void triggerMeltdown() {
        hasMeltedDown = true;
        reactorStatus = FusionReactorStatus.MELTDOWN;
        setStatus(MachineStatus.ERROR);
        hydrogenStored = waterStored = heliumBuffer = energyBuffer = 0;
        System.err.println("!!! FUSION REACTOR MELTDOWN at " + anchorPosition() + " !!!");
    }

    @Override
    protected void onAllControlLinksLost() {
        super.onAllControlLinksLost();
        reactorStatus = FusionReactorStatus.OFFLINE;
    }

    @Override
    public void onControlLinkEstablished(ControllerId controllerId) {
        super.onControlLinkEstablished(controllerId);
        if (!hasMeltedDown) updateReactorStatus();
    }

    public String getDetailedStatus() {
        return String.format("Reactor[temp=%.1f°C, status=%s, H2=%d, H2O=%d, He=%d, E=%d]",
            temperature, reactorStatus, hydrogenStored, waterStored, heliumBuffer, energyBuffer);
    }

    // === ResourceConsumer Interface (Inputs: Hydrogen, Water) ===

    @Override
    public Map<ResourceType, Integer> getResourceRequests() {
        if (hasMeltedDown) return Map.of();

        Map<ResourceType, Integer> requests = new HashMap<>();

        int hydrogenNeeded = maxHydrogen - hydrogenStored;
        int waterNeeded = maxWater - waterStored;

        if (hydrogenNeeded > 0) {
            requests.put(ResourceType.HYDROGEN, hydrogenNeeded);
        }
        if (waterNeeded > 0) {
            requests.put(ResourceType.WATER, waterNeeded);
        }

        return requests;
    }

    @Override
    public int acceptResource(ResourceType type, int amount) {
        if (hasMeltedDown) return 0;

        if (type.equals(ResourceType.HYDROGEN)) {
            return addHydrogen(amount);
        } else if (type.equals(ResourceType.WATER)) {
            return addWater(amount);
        }
        return 0;
    }

    @Override
    public boolean canAcceptResource(ResourceType type) {
        if (hasMeltedDown) return false;
        return type.equals(ResourceType.HYDROGEN) || type.equals(ResourceType.WATER);
    }

    // === ResourceProvider Interface (Outputs: Helium, Energy) ===

    @Override
    public Map<ResourceType, Integer> getAvailableResources() {
        if (hasMeltedDown) return Map.of();

        Map<ResourceType, Integer> available = new HashMap<>();

        if (heliumBuffer > 0) {
            available.put(ResourceType.HELIUM, heliumBuffer);
        }
        if (energyBuffer > 0) {
            available.put(ResourceType.ENERGY, energyBuffer);
        }

        return available;
    }

    @Override
    public int extractResource(ResourceType type, int maxAmount) {
        if (hasMeltedDown) return 0;

        if (type.equals(ResourceType.HELIUM)) {
            return extractHelium(maxAmount);
        } else if (type.equals(ResourceType.ENERGY)) {
            return extractEnergy(maxAmount);
        }
        return 0;
    }

    @Override
    public ResourceType getPrimaryResourceType() {
        // Reactor outputs helium as primary (energy is on POWER network)
        return ResourceType.HELIUM;
    }

    @Override
    public Map<String, Object> saveState() {
        Map<String, Object> state = new HashMap<>();
        state.put(STATE_VERSION_KEY, STATE_VERSION);

        state.put("temperature", temperature);
        state.put("hydrogenStored", hydrogenStored);
        state.put("waterStored", waterStored);
        state.put("heliumBuffer", heliumBuffer);
        state.put("energyBuffer", energyBuffer);

        state.put("reactorStatus", reactorStatus.name());
        state.put("hasMeltedDown", hasMeltedDown);

        state.put("totalHydrogenConsumed", totalHydrogenConsumed);
        state.put("totalHeliumProduced", totalHeliumProduced);
        state.put("totalEnergyProduced", totalEnergyProduced);

        return state;
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        if (state == null || state.isEmpty()) return;

        // NOTE: Gson deserializes numbers as Double by default.
        temperature = readDouble(state, "temperature", temperature);
        hydrogenStored = clamp(readInt(state, "hydrogenStored", hydrogenStored), 0, maxHydrogen);
        waterStored = clamp(readInt(state, "waterStored", waterStored), 0, maxWater);
        heliumBuffer = clamp(readInt(state, "heliumBuffer", heliumBuffer), 0, maxHelium);
        energyBuffer = clamp(readInt(state, "energyBuffer", energyBuffer), 0, maxEnergy);

        hasMeltedDown = readBoolean(state, "hasMeltedDown", hasMeltedDown);

        String statusName = readString(state, "reactorStatus", null);
        if (statusName != null) {
            try {
                reactorStatus = FusionReactorStatus.valueOf(statusName);
            } catch (IllegalArgumentException ignored) {
                // unknown enum value from older/newer version, keep current
            }
        }

        totalHydrogenConsumed = readLong(state, "totalHydrogenConsumed", totalHydrogenConsumed);
        totalHeliumProduced = readLong(state, "totalHeliumProduced", totalHeliumProduced);
        totalEnergyProduced = readLong(state, "totalEnergyProduced", totalEnergyProduced);

        // Re-derive machine status from reactor state
        if (!hasMeltedDown) {
            updateReactorStatus();
        } else {
            setStatus(MachineStatus.ERROR);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double readDouble(Map<String, Object> state, String key, double def) {
        Object v = state.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static int readInt(Map<String, Object> state, String key, int def) {
        Object v = state.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static long readLong(Map<String, Object> state, String key, long def) {
        Object v = state.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static boolean readBoolean(Map<String, Object> state, String key, boolean def) {
        Object v = state.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return def;
    }

    private static String readString(Map<String, Object> state, String key, String def) {
        Object v = state.get(key);
        return v instanceof String s ? s : def;
    }

    // === MachineIntrospectable Interface ===

    @Override
    public DebugSnapshot createDebugSnapshot() {
        DebugSnapshot.Builder builder = DebugSnapshot.builder();

        // Status Section
        builder.section("Status");

        DebugSnapshot.Severity statusSeverity = switch (reactorStatus) {
            case OPTIMAL -> DebugSnapshot.Severity.SUCCESS;
            case COLD, WARMING_UP, COOLING -> DebugSnapshot.Severity.NEUTRAL;
            case HOT -> DebugSnapshot.Severity.WARNING;
            case CRITICAL, MELTDOWN -> DebugSnapshot.Severity.ERROR;
            case STARVED, OFFLINE -> DebugSnapshot.Severity.INFO;
        };
        builder.status(reactorStatus.name(), statusSeverity);

        // Temperature metric with severity based on thresholds
        DebugSnapshot.Severity tempSeverity;
        if (temperature >= 1000.0) {
            tempSeverity = DebugSnapshot.Severity.ERROR;
        } else if (temperature >= 800.0) {
            tempSeverity = DebugSnapshot.Severity.ERROR;
        } else if (temperature >= 700.0) {
            tempSeverity = DebugSnapshot.Severity.WARNING;
        } else if (temperature >= FUSION_THRESHOLD && temperature < 500.0) {
            tempSeverity = DebugSnapshot.Severity.SUCCESS;
        } else {
            tempSeverity = DebugSnapshot.Severity.NEUTRAL;
        }
        builder.metric("Temperature", temperature, 1000.0, tempSeverity);

        // Input Resources Section
        builder.section("Input Resources");
        builder.progressBar("Hydrogen", hydrogenStored, maxHydrogen);
        builder.progressBar("Water", waterStored, maxWater);

        // Add requests if not at max
        int hydrogenNeeded = maxHydrogen - hydrogenStored;
        int waterNeeded = maxWater - waterStored;
        if (hydrogenNeeded > 0) {
            builder.keyValue("H₂ Needed", String.valueOf(hydrogenNeeded));
        }
        if (waterNeeded > 0) {
            builder.keyValue("H₂O Needed", String.valueOf(waterNeeded));
        }

        // Output Resources Section
        builder.section("Output Resources");
        builder.progressBar("Helium", heliumBuffer, maxHelium);
        builder.progressBar("Energy", energyBuffer, maxEnergy);

        // Show available amounts
        if (heliumBuffer > 0) {
            builder.keyValue("He Available", String.valueOf(heliumBuffer));
        }
        if (energyBuffer > 0) {
            builder.keyValue("Energy Available", String.valueOf(energyBuffer));
        }

        // Performance Section
        builder.section("Performance");

        // Operating efficiency
        if (temperature >= FUSION_THRESHOLD && !hasMeltedDown) {
            String efficiency = temperature < 500.0 ? "Optimal" :
                               temperature < 800.0 ? "High" : "Critical";
            DebugSnapshot.Severity effSeverity = temperature < 500.0 ? DebugSnapshot.Severity.SUCCESS :
                                                 temperature < 800.0 ? DebugSnapshot.Severity.WARNING :
                                                 DebugSnapshot.Severity.ERROR;
            builder.status(efficiency, effSeverity);
        } else if (!hasMeltedDown) {
            builder.status("Below Fusion Threshold", DebugSnapshot.Severity.INFO);
        }

        // Fusion threshold indicator
        builder.metric("Ignition Progress", temperature, FUSION_THRESHOLD,
            temperature >= FUSION_THRESHOLD ? DebugSnapshot.Severity.SUCCESS : DebugSnapshot.Severity.NEUTRAL);

        // Lifetime Statistics Section
        builder.section("Lifetime Stats");
        builder.property("H₂ Consumed", formatLargeNumber(totalHydrogenConsumed));
        builder.property("He Produced", formatLargeNumber(totalHeliumProduced));
        builder.property("Energy Produced", formatLargeNumber(totalEnergyProduced));

        // Calculate efficiency ratio if any hydrogen consumed
        if (totalHydrogenConsumed > 0) {
            double energyPerHydrogen = (double) totalEnergyProduced / totalHydrogenConsumed;
            builder.property("Energy/H₂", String.format("%.1f", energyPerHydrogen));
        }

        // Warnings Section
        if (hasMeltedDown) {
            builder.section("Critical Alerts");
            builder.warning("⚠ REACTOR MELTDOWN - TOTAL DESTRUCTION");
            builder.warning("Reactor is permanently destroyed");
        } else if (temperature >= 800.0) {
            builder.section("Critical Alerts");
            builder.warning("⚠ MELTDOWN IMMINENT - " + String.format("%.0f°C", temperature));
            builder.warning("Increase water supply immediately!");
        } else if (temperature >= 700.0) {
            builder.section("Warnings");
            builder.warning("Temperature approaching critical levels");
            builder.warning("Consider adding more cooling");
        } else if (hydrogenStored == 0 && waterStored == 0) {
            builder.section("Warnings");
            builder.warning("Reactor starved - no fuel or coolant");
        }

        // Add thresholds info
        builder.section("Thresholds");
        builder.property("Fusion Ignition", String.format("%.0f°C", FUSION_THRESHOLD));
        builder.property("Optimal Range", "100°C - 500°C");
        builder.property("Danger Zone", "≥ 700°C");
        builder.property("Meltdown", "≥ 1000°C");

        return builder.build();
    }

    @Override
    public String debugDisplayName() {
        return "Fusion Reactor";
    }

    @Override
    public boolean hasCriticalIssues() {
        return hasMeltedDown || temperature >= 800.0;
    }

    /**
     * Format large numbers with K, M, B suffixes for readability.
     */
    private String formatLargeNumber(long value) {
        if (value >= 1_000_000_000) {
            return String.format("%.2fB", value / 1_000_000_000.0);
        } else if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000.0);
        } else if (value >= 1_000) {
            return String.format("%.2fK", value / 1_000.0);
        } else {
            return String.valueOf(value);
        }
    }
}
