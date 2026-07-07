package dev.kate.erd.core.machine;

import java.util.Map;

public interface MachineStateful extends MachineInstance {
    default Map<String, Object> saveState() {
        return Map.of();
    }
    default void restoreState(Map<String, Object> state) {}
}
