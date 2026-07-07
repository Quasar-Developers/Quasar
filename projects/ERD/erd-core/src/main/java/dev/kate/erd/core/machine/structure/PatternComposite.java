package dev.kate.erd.core.machine.structure;

import dev.kate.erd.core.endpoint.EndpointRole;
import dev.kate.erd.core.model.ConnectionType;

import java.util.*;

/**
 * A reusable pattern component that can be composed into multiple StructurePatterns.
 *
 * <p>PatternComposite allows you to define common structural elements once and
 * reuse them across multiple machine definitions. This is useful for shared
 * components like frames, casings, or support structures.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Define a reusable iron frame
 * PatternComposite ironFrame = PatternComposite.builder()
 *     .type("XXX")
 *     .type("XXX")
 *     .type("XXX")
 *     .key('X', "minecraft:iron_block")
 *     .build();
 *
 * // Use in multiple patterns
 * StructurePattern reactor = StructurePattern.builder()
 *     .composite(ironFrame)
 *     .nextLayer()
 *     .type("P@P")
 *     .key('@', "minecraft:redstone_block")
 *     .key('P', "minecraft:copper_block")
 *     .build();
 * }</pre>
 *
 * <p>Thread-safety: This class is immutable and thread-safe after construction.
 */
public final class PatternComposite {

    private final List<String[][]> layers;
    private final Map<Character, String> blockKeys;
    private final Map<Character, Set<String>> alternatives;
    private final Map<Character, StructurePattern.EndpointConfig> endpointConfigs;

    private PatternComposite(
            List<String[][]> layers,
            Map<Character, String> blockKeys,
            Map<Character, Set<String>> alternatives,
            Map<Character, StructurePattern.EndpointConfig> endpointConfigs) {
        this.layers = List.copyOf(layers);
        this.blockKeys = Map.copyOf(blockKeys);
        this.alternatives = Map.copyOf(alternatives);
        this.endpointConfigs = Map.copyOf(endpointConfigs);
    }

    /**
     * @return the layers in this composite
     */
    public List<String[][]> getLayers() {
        return layers;
    }

    /**
     * @return the block key mappings
     */
    public Map<Character, String> getBlockKeys() {
        return blockKeys;
    }

    /**
     * @return the block type alternatives
     */
    public Map<Character, Set<String>> getAlternatives() {
        return alternatives;
    }

    /**
     * @return the endpoint configurations
     */
    public Map<Character, StructurePattern.EndpointConfig> getEndpointConfigs() {
        return endpointConfigs;
    }

    /**
     * Creates a new builder for constructing pattern composites.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating PatternComposite instances.
     */
    public static final class Builder {
        private final List<String> currentLayer = new ArrayList<>();
        private final List<String[][]> completedLayers = new ArrayList<>();
        private final Map<Character, String> blockKeys = new HashMap<>();
        private final Map<Character, Set<String>> alternatives = new HashMap<>();
        private final Map<Character, StructurePattern.EndpointConfig> endpointConfigs = new HashMap<>();

        private Builder() {}

        /**
         * Adds a row to the current type.
         *
         * @param row the row string
         * @return this builder
         */
        public Builder layer(String row) {
            currentLayer.add(Objects.requireNonNull(row, "row must not be null"));
            return this;
        }

        /**
         * Completes the current type and starts a new one.
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
         * @param key the character key
         * @param blockType the block type
         * @return this builder
         */
        public Builder key(char key, String blockType) {
            if (key == ' ') {
                throw new IllegalArgumentException("Space character is reserved for air");
            }
            if (key == '@') {
                throw new IllegalArgumentException("Anchor character '@' cannot be used in composites");
            }
            blockKeys.put(key, Objects.requireNonNull(blockType, "blockType must not be null"));
            return this;
        }

        /**
         * Defines alternative block types for a key.
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
            endpointConfigs.put(key, new StructurePattern.EndpointConfig(layer, role));
            return this;
        }

        /**
         * Builds the pattern composite.
         *
         * @return the immutable pattern composite
         * @throws IllegalStateException if the composite is invalid
         */
        public PatternComposite build() {
            // Complete any pending type
            if (!currentLayer.isEmpty()) {
                completedLayers.add(convertLayerToArray(currentLayer));
                currentLayer.clear();
            }

            if (completedLayers.isEmpty()) {
                throw new IllegalStateException("Composite must have at least one type");
            }

            // Validate that all characters in layers have key mappings
            validateKeys();

            return new PatternComposite(
                    completedLayers,
                    blockKeys,
                    alternatives,
                    endpointConfigs);
        }

        private String[][] convertLayerToArray(List<String> layerRows) {
            if (layerRows.isEmpty()) {
                throw new IllegalStateException("Layer cannot be empty");
            }

            int width = layerRows.get(0).length();
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
                            "Character '" + c + "' is used in composite but has no key mapping");
                }
            }
        }
    }
}

