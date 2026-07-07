package dev.kate.erd.core.machine;

import dev.kate.erd.core.machine.component.ComponentId;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ChunkKey;
import dev.kate.erd.core.model.MachineId;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Snapshot of a machine's complete state for persistence.
 *
 * <p>MachineSnapshot captures everything needed to restore a machine:
 * <ul>
 *   <li>Identity (id, type, anchor position)</li>
 *   <li>Structure (all occupied positions, spanned chunks)</li>
 *   <li>Components (nested component snapshots)</li>
 *   <li>State (versioned, type-safe)</li>
 * </ul>
 *
 * <p>The full position list enables:
 * <ul>
 *   <li>Cross-chunk machine tracking</li>
 *   <li>Partial chunk load handling</li>
 *   <li>Structure validation on restore</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * MachineSnapshot snapshot = MachineSnapshot.builder()
 *     .id(machine.id())
 *     .typeId(machine.definition().typeId())
 *     .anchorPosition(machine.anchorPosition())
 *     .occupiedPositions(machine.structure().positions())
 *     .components(componentSnapshots)
 *     .stateVersion(codec.version())
 *     .state(codec.encode(machine.captureState()))
 *     .build();
 * }</pre>
 *
 * <p>Thread-safety: This record is immutable and thread-safe.
 *
 * @param id the machine's unique identifier
 * @param typeId the machine definition type ID
 * @param anchorPosition the anchor/controller block position
 * @param occupiedPositions all block positions occupied by this machine
 * @param spannedChunks all chunks this machine spans (derived from positions)
 * @param components snapshots of attached components
 * @param stateVersion the version of the state codec used
 * @param state the encoded state data
 */
public record MachineSnapshot(
        MachineId id,
        String typeId,
        BlockPos anchorPosition,
        Set<BlockPos> occupiedPositions,
        Set<ChunkKey> spannedChunks,
        List<ComponentSnapshot> components,
        int stateVersion,
        Map<String, Object> state
) {
    public MachineSnapshot {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(typeId, "typeId must not be null");
        Objects.requireNonNull(anchorPosition, "anchorPosition must not be null");
        occupiedPositions = occupiedPositions != null ? Set.copyOf(occupiedPositions) : Set.of(anchorPosition);
        spannedChunks = spannedChunks != null ? Set.copyOf(spannedChunks) : computeSpannedChunks(occupiedPositions);
        components = components != null ? List.copyOf(components) : List.of();
        state = state != null ? Map.copyOf(state) : Map.of();
    }

    /**
     * @return true if this machine spans multiple chunks
     */
    public boolean isMultiChunk() {
        return spannedChunks.size() > 1;
    }

    /**
     * @return true if this machine has attached components
     */
    public boolean hasComponents() {
        return !components.isEmpty();
    }

    /**
     * @return true if this machine has state data
     */
    public boolean hasState() {
        return !state.isEmpty();
    }

    /**
     * @return the chunk containing the anchor position
     */
    public ChunkKey anchorChunk() {
        return anchorPosition.toChunkKey();
    }

    /**
     * Checks if this machine spans the given chunk.
     *
     * @param chunk the chunk to check
     * @return true if any block is in the chunk
     */
    public boolean spansChunk(ChunkKey chunk) {
        return spannedChunks.contains(chunk);
    }

    /**
     * Gets all positions in a specific chunk.
     *
     * @param chunk the chunk
     * @return positions in that chunk
     */
    public Set<BlockPos> positionsInChunk(ChunkKey chunk) {
        return occupiedPositions.stream()
                .filter(pos -> pos.toChunkKey().equals(chunk))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<ChunkKey> computeSpannedChunks(Set<BlockPos> positions) {
        return positions.stream()
                .map(BlockPos::toChunkKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    // ========== Builder ==========

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for MachineSnapshot.
     */
    public static final class Builder {
        private MachineId id;
        private String typeId;
        private BlockPos anchorPosition;
        private Set<BlockPos> occupiedPositions;
        private List<ComponentSnapshot> components = List.of();
        private int stateVersion = 0;
        private Map<String, Object> state = Map.of();

        private Builder() {}

        public Builder id(MachineId id) {
            this.id = id;
            return this;
        }

        public Builder typeId(String typeId) {
            this.typeId = typeId;
            return this;
        }

        public Builder anchorPosition(BlockPos anchorPosition) {
            this.anchorPosition = anchorPosition;
            return this;
        }

        public Builder occupiedPositions(Set<BlockPos> occupiedPositions) {
            this.occupiedPositions = occupiedPositions;
            return this;
        }

        public Builder components(List<ComponentSnapshot> components) {
            this.components = components;
            return this;
        }

        public Builder stateVersion(int stateVersion) {
            this.stateVersion = stateVersion;
            return this;
        }

        public Builder state(Map<String, Object> state) {
            this.state = state;
            return this;
        }

        public MachineSnapshot build() {
            return new MachineSnapshot(
                    id, typeId, anchorPosition, occupiedPositions, null,
                    components, stateVersion, state
            );
        }
    }

    // ========== Component Snapshot ==========

    /**
     * Snapshot of a component's state for persistence.
     *
     * <p>Components are persisted as part of their parent machine's snapshot.
     *
     * @param id the component's unique identifier
     * @param componentTypeId the component definition type ID
     * @param attachmentPoint where the component connects to its parent
     * @param occupiedPositions all block positions (for cross-chunk awareness)
     * @param stateVersion the version of the state codec used
     * @param state the encoded state data
     */
    public record ComponentSnapshot(
            ComponentId id,
            String componentTypeId,
            BlockPos attachmentPoint,
            Set<BlockPos> occupiedPositions,
            int stateVersion,
            Map<String, Object> state
    ) {
        public ComponentSnapshot {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(componentTypeId, "componentTypeId must not be null");
            Objects.requireNonNull(attachmentPoint, "attachmentPoint must not be null");
            occupiedPositions = occupiedPositions != null ? Set.copyOf(occupiedPositions) : Set.of();
            state = state != null ? Map.copyOf(state) : Map.of();
        }

        /**
         * Creates a snapshot with no state.
         */
        public ComponentSnapshot(ComponentId id, String componentTypeId,
                                  BlockPos attachmentPoint, Set<BlockPos> occupiedPositions) {
            this(id, componentTypeId, attachmentPoint, occupiedPositions, 0, Map.of());
        }

        /**
         * @return true if this snapshot has state data
         */
        public boolean hasState() {
            return !state.isEmpty();
        }
    }
}
