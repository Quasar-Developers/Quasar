package dev.kate.erd.bukkit.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.kate.erd.core.machine.MachineSnapshot;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.util.ErdLogger;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * JSON file-backed persistence for machine anchors + runtime state.
 *
 * <p>This replaces in-world markers with a plugin-owned file.
 */
public final class JsonMachineStateStore {

    private static final Gson GSON = new GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .create();

    private final ErdLogger logger;
    private final Path file;

    public JsonMachineStateStore(Path file, ErdLogger logger) {
        this.file = Objects.requireNonNull(file, "file must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

    public synchronized void saveAll(List<Entry> entries) {
        Objects.requireNonNull(entries, "entries must not be null");
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(entries, w);
            }
        } catch (Exception e) {
            logger.error("Failed to save machines to %s: %s", file, e.getMessage());
        }
    }

    public synchronized List<Entry> loadAll() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Entry[] arr = GSON.fromJson(r, Entry[].class);
            if (arr == null) return List.of();
            List<Entry> out = new ArrayList<>(arr.length);
            for (Entry e : arr) {
                if (e != null) out.add(e);
            }
            return out;
        } catch (Exception e) {
            logger.error("Failed to load machines from %s: %s", file, e.getMessage());
            return List.of();
        }
    }

    /** Persisted machine record. */
    public record Entry(
        String machineId,
        String typeId,
        String worldId,
        int x,
        int y,
        int z,
        int stateVersion,
        Map<String, Object> state
    ) {
        public MachineSnapshot toSnapshot() {
            BlockPos anchor = new BlockPos(UUID.fromString(worldId), x, y, z);
            return MachineSnapshot.builder()
                .id(MachineId.parse(machineId))
                .typeId(typeId)
                .anchorPosition(anchor)
                .occupiedPositions(java.util.Set.of(anchor))
                .stateVersion(stateVersion)
                .state(state)
                .build();
        }
        public BlockPos toAnchorPos() {
            return new BlockPos(UUID.fromString(worldId), x, y, z);
        }
        public static Entry from(BlockPos anchor, MachineSnapshot snapshot) {
            return new Entry(
                snapshot.id().toString(),
                snapshot.typeId(),
                anchor.worldId().toString(),
                anchor.x(),
                anchor.y(),
                anchor.z(),
                snapshot.stateVersion(),
                snapshot.state()
            );
        }
    }
}
