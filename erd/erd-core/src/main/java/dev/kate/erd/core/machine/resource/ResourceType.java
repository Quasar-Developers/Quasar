package dev.kate.erd.core.machine.resource;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Types of resources that can flow through PIPE networks.
 *
 * <p>This class is now a registry-based system to allow addons to register
 * their own resource types.
 */
public final class ResourceType {

    private static final Map<String, ResourceType> REGISTRY = new ConcurrentHashMap<>();

    // Core resources
    public static final ResourceType HYDROGEN = register("hydrogen", "H₂", "Hydrogen", true, false);
    public static final ResourceType WATER = register("water", "H₂O", "Water", false, true);
    public static final ResourceType LAVA = register("lava", "🌋", "Lava", false, true);
    public static final ResourceType HELIUM = register("helium", "He", "Helium", true, false);
    public static final ResourceType ENERGY = register("energy", "⚡", "Energy", false, false); // For POWER networks
    public static final ResourceType STEAM = register("steam", "💨", "Steam", true, false);
    public static final ResourceType COOLANT = register("coolant", "❄", "Coolant", false, true);

    private final String id;
    private final String symbol;
    private final String displayName;
    private final boolean isGas;
    private final boolean isLiquid;

    private ResourceType(String id, String symbol, String displayName, boolean isGas, boolean isLiquid) {
        this.id = id;
        this.symbol = symbol;
        this.displayName = displayName;
        this.isGas = isGas;
        this.isLiquid = isLiquid;
    }

    /**
     * Registers a new resource type.
     *
     * @param id unique identifier (e.g., "myaddon:oil")
     * @param symbol short symbol (e.g., "🛢")
     * @param displayName human-readable name
     * @param isGas true if gas
     * @param isLiquid true if liquid
     * @return the registered resource type
     * @throws IllegalArgumentException if ID is already registered
     */
    public static ResourceType register(String id, String symbol, String displayName, boolean isGas, boolean isLiquid) {
        Objects.requireNonNull(id, "id must not be null");
        if (REGISTRY.containsKey(id)) {
            throw new IllegalArgumentException("Resource type already registered: " + id);
        }
        ResourceType type = new ResourceType(id, symbol, displayName, isGas, isLiquid);
        REGISTRY.put(id, type);
        return type;
    }

    /**
     * Gets a resource type by ID.
     *
     * @param id the resource ID
     * @return the resource type, or empty if not found
     */
    public static Optional<ResourceType> get(String id) {
        return Optional.ofNullable(REGISTRY.get(id));
    }

    /**
     * @return all registered resource types
     */
    public static Collection<ResourceType> values() {
        return REGISTRY.values();
    }

    public String id() { return id; }
    public String symbol() { return symbol; }
    public String displayName() { return displayName; }
    public boolean isGas() { return isGas; }
    public boolean isLiquid() { return isLiquid; }

    @Override
    public String toString() {
        return symbol + " " + displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceType that = (ResourceType) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
