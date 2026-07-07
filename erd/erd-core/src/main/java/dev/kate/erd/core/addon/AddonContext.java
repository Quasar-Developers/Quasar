package dev.kate.erd.core.addon;

import dev.kate.erd.core.controller.ControllerDefinition;
import dev.kate.erd.core.machine.MachineDefinition;
import dev.kate.erd.core.machine.MachineSnapshot;
import dev.kate.erd.core.machine.resource.ResourceType;
import dev.kate.erd.core.util.ErdLogger;

import java.util.List;

/**
 * Context provided to addons during loading.
 */
public interface AddonContext {
    void registerMachine(MachineDefinition<?> definition);
    void registerController(ControllerDefinition<?> definition);
    
    /**
     * Registers a new resource type.
     *
     * @param id unique identifier (e.g., "myaddon:oil")
     * @param symbol short symbol (e.g., "🛢")
     * @param displayName human-readable name
     * @param isGas true if gas
     * @param isLiquid true if liquid
     * @return the registered resource type
     */
    ResourceType registerResource(String id, String symbol, String displayName, boolean isGas, boolean isLiquid);

    ErdLogger getLogger();
    String getPluginDataFolder();

    default List<MachineSnapshot> snapshotMachines() {
        return List.of();
    }

    default void restoreMachines(List<MachineSnapshot> snapshots) {}

    default void redetectMachinesFromMarkers() {}
}
