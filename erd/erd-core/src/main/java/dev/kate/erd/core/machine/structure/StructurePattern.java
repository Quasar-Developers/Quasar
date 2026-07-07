package dev.kate.erd.core.machine.structure;

import dev.kate.erd.core.endpoint.BaseEndpoint;
import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.endpoint.EndpointRole;
import dev.kate.erd.core.machine.MachineDefinition;
import dev.kate.erd.core.machine.StructureSnapshot;
import dev.kate.erd.core.machine.ValidationResult;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;

import java.util.*;

/**
 * Declarative ASCII-art style structure pattern for machine definitions.
 *
 * <p>This class provides a clean, readable way to define multiblock structures
 * using string-based type definitions. It handles:
 * <ul>
 *   <li>Pattern validation against structure snapshots</li>
 *   <li>Automatic rotation support (N/E/S/W orientations)</li>
 *   <li>Block type alternatives</li>
 *   <li>Endpoint/port discovery</li>
 *   <li>Detection bounds calculation</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * StructurePattern pattern = StructurePattern.builder()
 *     .type("XXX")
 *     .type("X@X")  // @ = anchor/controller
 *     .type("XXX")
 *     .nextLayer()
 *     .type("PPP")
 *     .type("P P")  // spaces = air/empty
 *     .type("PPP")
 *     .key('X', "minecraft:iron_block")
 *     .key('@', "minecraft:redstone_block")
 *     .key('P', "minecraft:copper_block")
 *     .endpoint('P', ConnectionType.PIPE, EndpointRole.CONSUMER)
 *     .allowRotation(true)
 *     .build();
 * }</pre>
 *
 * <p>Thread-safety: This class is immutable and thread-safe after construction.
 */
public final class StructurePattern {

    private final List<String[][]> layers;
    private final Map<Character, String> blockKeys;
    private final Map<Character, Set<String>> alternatives;
    private final Map<Character, EndpointConfig> endpointConfigs;
    private final boolean allowRotation;
    private final Character anchorChar;
    private final MachineDefinition.StructureBounds bounds;

    private StructurePattern(
            List<String[][]> layers,
            Map<Character, String> blockKeys,
            Map<Character, Set<String>> alternatives,
            Map<Character, EndpointConfig> endpointConfigs,
            boolean allowRotation,
            Character anchorChar) {
        this.layers = List.copyOf(layers);
        this.blockKeys = Map.copyOf(blockKeys);
        this.alternatives = Map.copyOf(alternatives);
        this.endpointConfigs = Map.copyOf(endpointConfigs);
        this.allowRotation = allowRotation;
        this.anchorChar = anchorChar;
        this.bounds = calculateBounds();
    }

    /**
     * Validates a structure snapshot against this pattern.
     *
     * @param snapshot the structure to validate
     * @return validation result with success/failure details
     */
    public ValidationResult validate(StructureSnapshot snapshot) {
        // Try all rotations if allowed
        List<StructureRotation> rotationsToTry = allowRotation
                ? List.of(StructureRotation.NORTH, StructureRotation.EAST,
                         StructureRotation.SOUTH, StructureRotation.WEST)
                : List.of(StructureRotation.NORTH);

        for (StructureRotation rotation : rotationsToTry) {
            ValidationResult result = validateWithRotation(snapshot, rotation);
            if (result instanceof ValidationResult.Valid) {
                return result;
            }
        }

        // All rotations failed
        return ValidationResult.invalid("Structure does not match pattern in any orientation");
    }

    private ValidationResult validateWithRotation(
            StructureSnapshot snapshot, StructureRotation rotation) {

        BlockPos origin = snapshot.origin();
        Set<BlockPos> occupiedPositions = new HashSet<>();
        List<Endpoint> endpoints = new ArrayList<>();

        int anchorX = findAnchorX();
        int anchorZ = findAnchorZ();

        // Validate each type
        for (int layerY = 0; layerY < layers.size(); layerY++) {
            String[][] layer = layers.get(layerY);

            for (int z = 0; z < layer.length; z++) {
                String[] row = layer[z];
                for (int x = 0; x < row.length; x++) {
                    char patternChar = row[x].charAt(0);

                    // Skip spaces (air)
                    if (patternChar == ' ') {
                        continue;
                    }

                    // Get relative position and apply rotation
                    int relX = x - anchorX;
                    int relZ = z - anchorZ;

                    BlockPos rotatedPos = rotation.rotate(origin, relX, layerY, relZ);

                    // Check if block matches
                    StructureSnapshot.BlockData blockData = snapshot.getBlock(rotatedPos);
                    if (blockData == null) {
                        return ValidationResult.invalid(
                                "Missing block at " + rotatedPos,
                                Set.of(rotatedPos));
                    }

                    // Validate block type
                    if (!isValidBlockType(patternChar, blockData.typeKey())) {
                        return ValidationResult.invalid(
                                "Invalid block at " + rotatedPos +
                                ". Expected type for key '" + patternChar +
                                "', found: " + blockData.typeKey(),
                                Set.of(rotatedPos));
                    }

                    occupiedPositions.add(rotatedPos);

                    // Check if this position has an endpoint
                    EndpointConfig endpointConfig = endpointConfigs.get(patternChar);
                    if (endpointConfig != null) {
                        endpoints.add(new BaseEndpoint(
                                rotatedPos,
                                endpointConfig.layer(),
                                endpointConfig.role()));
                    }
                }
            }
        }

        return ValidationResult.valid(occupiedPositions, endpoints);
    }

    private boolean isValidBlockType(char patternChar, String blockType) {
        String primaryType = blockKeys.get(patternChar);
        if (primaryType == null) {
            return false;
        }

        if (primaryType.equals(blockType)) {
            return true;
        }

        // Check alternatives
        Set<String> alts = alternatives.get(patternChar);
        return alts != null && alts.contains(blockType);
    }

    private int findAnchorX() {
        if (anchorChar == null) return 0;

        for (String[][] layer : layers) {
            for (String[] row : layer) {
                for (int x = 0; x < row.length; x++) {
                    if (row[x].charAt(0) == anchorChar) {
                        return x;
                    }
                }
            }
        }
        return 0;
    }

    private int findAnchorZ() {
        if (anchorChar == null) return 0;

        for (String[][] layer : layers) {
            for (int z = 0; z < layer.length; z++) {
                String[] row = layer[z];
                for (String s : row) {
                    if (s.charAt(0) == anchorChar) {
                        return z;
                    }
                }
            }
        }
        return 0;
    }

    private MachineDefinition.StructureBounds calculateBounds() {
        int minX = 0, maxX = 0, minY = 0, maxY = 0, minZ = 0, maxZ = 0;

        int anchorX = findAnchorX();
        int anchorZ = findAnchorZ();

        for (int layerY = 0; layerY < layers.size(); layerY++) {
            String[][] layer = layers.get(layerY);

            for (int z = 0; z < layer.length; z++) {
                String[] row = layer[z];
                for (int x = 0; x < row.length; x++) {
                    if (row[x].charAt(0) != ' ') {
                        int relX = x - anchorX;
                        int relZ = z - anchorZ;

                        minX = Math.min(minX, relX);
                        maxX = Math.max(maxX, relX);
                        maxY = Math.max(maxY, layerY);
                        minZ = Math.min(minZ, relZ);
                        maxZ = Math.max(maxZ, relZ);
                    }
                }
            }
        }

        return new MachineDefinition.StructureBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * @return the detection bounds for this pattern
     */
    public MachineDefinition.StructureBounds getDetectionBounds() {
        return bounds;
    }

    /**
     * Creates a new builder for constructing structure patterns.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating StructurePattern instances.
     */
    public static final class Builder {
        private final List<String> currentLayer = new ArrayList<>();
        private final List<String[][]> completedLayers = new ArrayList<>();
        private final Map<Character, String> blockKeys = new HashMap<>();
        private final Map<Character, Set<String>> alternatives = new HashMap<>();
        private final Map<Character, EndpointConfig> endpointConfigs = new HashMap<>();
        private boolean allowRotation = false;
        private Character anchorChar = null;

        private Builder() {}

        /**
         * Adds a row to the current type.
         * Rows are added from north (-Z) to south (+Z).
         *
         * @param row the row string (characters correspond to keys)
         * @return this builder
         */
        public Builder layer(String row) {
            currentLayer.add(Objects.requireNonNull(row, "row must not be null"));
            return this;
        }

        /**
         * Completes the current type and starts a new one.
         * Layers stack vertically from bottom (Y=0) to top (Y+).
         *
         * @return this builder
         */
        public Builder nextLayer() {
            if (!currentLayer.isEmpty()) {
                completedLayers.add(convertLayerToArray(currentLayer));
                currentLayer.clear();
            }
            return this;
        }

        /**
         * Maps a character key to a block type.
         *
         * @param key the character key used in type strings
         * @param blockType the block type (e.g., "minecraft:iron_block")
         * @return this builder
         */
        public Builder key(char key, String blockType) {
            if (key == ' ') {
                throw new IllegalArgumentException("Space character is reserved for air");
            }
            blockKeys.put(key, Objects.requireNonNull(blockType, "blockType must not be null"));

            // Auto-detect anchor character (@)
            if (key == '@') {
                anchorChar = '@';
            }

            return this;
        }

        /**
         * Defines alternative block types for a key.
         * Any of these blocks will be accepted in place of the primary block.
         *
         * @param key the character key
         * @param alternativeTypes alternative block types
         * @return this builder
         */
        public Builder alternatives(char key, String... alternativeTypes) {
            if (!blockKeys.containsKey(key)) {
                throw new IllegalArgumentException("Key '" + key + "' must be defined with key() before adding alternatives");
            }

            Set<String> altSet = alternatives.computeIfAbsent(key, k -> new HashSet<>());
            altSet.addAll(Arrays.asList(alternativeTypes));
            return this;
        }

        /**
         * Defines an endpoint at positions matching the given key.
         *
         * @param key the character key
         * @param layer the network type
         * @param role the endpoint role
         * @return this builder
         */
        public Builder endpoint(char key, ConnectionType layer, EndpointRole role) {
            endpointConfigs.put(key, new EndpointConfig(layer, role));
            return this;
        }

        /**
         * Enables or disables rotation support.
         *
         * @param allow true to allow rotation matching
         * @return this builder
         */
        public Builder allowRotation(boolean allow) {
            this.allowRotation = allow;
            return this;
        }

        /**
         * Adds a pre-built PatternComposite to this pattern.
         *
         * @param composite the composite to include
         * @return this builder
         */
        public Builder composite(PatternComposite composite) {
            // Add all layers from composite
            completedLayers.addAll(composite.getLayers());

            // Merge keys
            blockKeys.putAll(composite.getBlockKeys());

            // Merge alternatives
            for (Map.Entry<Character, Set<String>> entry : composite.getAlternatives().entrySet()) {
                alternatives.computeIfAbsent(entry.getKey(), k -> new HashSet<>())
                        .addAll(entry.getValue());
            }

            // Merge endpoints
            endpointConfigs.putAll(composite.getEndpointConfigs());

            return this;
        }

        /**
         * Builds the structure pattern.
         *
         * @return the immutable structure pattern
         * @throws IllegalStateException if the pattern is invalid
         */
        public StructurePattern build() {
            // Complete any pending type
            if (!currentLayer.isEmpty()) {
                completedLayers.add(convertLayerToArray(currentLayer));
                currentLayer.clear();
            }

            if (completedLayers.isEmpty()) {
                throw new IllegalStateException("Pattern must have at least one type");
            }

            // Validate that all characters in layers have key mappings
            validateKeys();

            return new StructurePattern(
                    completedLayers,
                    blockKeys,
                    alternatives,
                    endpointConfigs,
                    allowRotation,
                    anchorChar);
        }

        private String[][] convertLayerToArray(List<String> layerRows) {
            if (layerRows.isEmpty()) {
                throw new IllegalStateException("Layer cannot be empty");
            }

            int width = layerRows.getFirst().length();
            String[][] array = new String[layerRows.size()][];

            for (int i = 0; i < layerRows.size(); i++) {
                String row = layerRows.get(i);
                if (row.length() != width) {
                    throw new IllegalStateException(
                            "All rows in a type must have the same width. " +
                            "Expected " + width + " but row " + i + " has " + row.length());
                }

                array[i] = new String[row.length()];
                for (int j = 0; j < row.length(); j++) {
                    array[i][j] = String.valueOf(row.charAt(j));
                }
            }

            return array;
        }

        private void validateKeys() {
            Set<Character> usedChars = new HashSet<>();

            for (String[][] layer : completedLayers) {
                for (String[] row : layer) {
                    for (String cell : row) {
                        char c = cell.charAt(0);
                        if (c != ' ') {
                            usedChars.add(c);
                        }
                    }
                }
            }

            for (char c : usedChars) {
                if (!blockKeys.containsKey(c)) {
                    throw new IllegalStateException(
                            "Character '" + c + "' is used in pattern but has no key mapping");
                }
            }
        }
    }

    /**
     * Configuration for an endpoint at a pattern position.
     *
     * @param layer the network type
     * @param role the endpoint role
     */
    record EndpointConfig(ConnectionType layer, EndpointRole role) {
        EndpointConfig {
            Objects.requireNonNull(layer, "type must not be null");
            Objects.requireNonNull(role, "role must not be null");
        }
    }
}

