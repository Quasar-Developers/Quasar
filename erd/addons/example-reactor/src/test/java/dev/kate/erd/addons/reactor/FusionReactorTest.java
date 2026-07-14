package dev.kate.erd.addons.reactor;

import dev.kate.erd.core.machine.MachineStatus;
import dev.kate.erd.core.machine.StructureSnapshot;
import dev.kate.erd.core.machine.ValidationResult;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ControllerId;
import dev.kate.erd.core.model.MachineId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the Fusion Reactor addon machine.
 */
class FusionReactorTest {
    private static final UUID WORLD_ID = UUID.randomUUID();
    private FusionReactorInstance reactor;
    private ResourceGeneratorInstance generator;

    // Mock class for ResourceGeneratorInstance since it's not available in this module
    interface ResourceGeneratorInstance {
        enum OutputType { HYDROGEN, WATER }
        void setOutputType(OutputType type);
        void setOutputRate(int rate);
        void tick();
        int extractHydrogen(int amount);
        int extractWater(int amount);
    }

    @BeforeEach
    void setUp() {
        // Create valid structure snapshot for reactor
        BlockPos origin = new BlockPos(WORLD_ID, 0, 0, 0);
        StructureSnapshot snapshot = createValidReactorStructure(origin);
        
        ValidationResult result = FusionReactorDefinition.INSTANCE.validate(snapshot);
        if (result instanceof ValidationResult.Valid valid) {
            reactor = FusionReactorDefinition.INSTANCE.createInstance(
                MachineId.create(), valid.structure());
        } else {
            throw new IllegalStateException("Invalid structure in test setup: " + result);
        }

        // Mock generator
        generator = mock(ResourceGeneratorInstance.class);

        // Simulate controller connection
        reactor.onControlLinkEstablished(ControllerId.create());
    }

    private StructureSnapshot createValidReactorStructure(BlockPos origin) {
        var builder = StructureSnapshot.builder().origin(origin);
        // Add core
        builder.addBlock(origin, FusionReactorDefinition.CORE_BLOCK);

        // Add all surrounding blocks
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos pos = origin.offset(dx, dy, dz);
                    String blockType = FusionReactorDefinition.CASING_BLOCK;

                    // Middle type - assign specific terracotta blocks for endpoints
                    if (dy == 0) {
                        if (dx == 0 && dz == -1) {
                            blockType = FusionReactorDefinition.WATER_INPUT_BLOCK; // North: Water input
                        } else if (dx == 0 && dz == 1) {
                            blockType = FusionReactorDefinition.HYDROGEN_INPUT_BLOCK; // South: Hydrogen input
                        } else if (dx == 1 && dz == 0) {
                            blockType = FusionReactorDefinition.ENERGY_OUTPUT_BLOCK; // East: Energy output
                        } else if (dx == -1 && dz == 0) {
                            blockType = FusionReactorDefinition.HELIUM_OUTPUT_BLOCK; // West: Helium output
                        }
                    }

                    builder.addBlock(pos, blockType);
                }
            }
        }
        return builder.build();
    }

    // === Initial State Tests ===
    @Test
    void initialState_isColdOrStarved() {
        // Initial status depends on whether we have resources
        assertThat(reactor.getReactorStatus()).isIn(
            FusionReactorStatus.COLD,
            FusionReactorStatus.STARVED
        );
        assertThat(reactor.getTemperature()).isEqualTo(FusionReactorInstance.AMBIENT_TEMP);
        assertThat(reactor.getHydrogenStored()).isZero();
        assertThat(reactor.getWaterStored()).isZero();
        assertThat(reactor.hasMeltedDown()).isFalse();
    }

    @Test
    void reactor_has4Endpoints() {
        assertThat(reactor.endpoints()).hasSize(4);
    }

    // === Fuel Input Tests ===
    @Test
    void addHydrogen_increasesStorage() {
        int accepted = reactor.addHydrogen(100);
        assertThat(accepted).isEqualTo(100);
        assertThat(reactor.getHydrogenStored()).isEqualTo(100);
    }

    @Test
    void addHydrogen_respectsMaxCapacity() {
        int accepted = reactor.addHydrogen(2000);
        assertThat(accepted).isEqualTo(FusionReactorInstance.BASE_MAX_HYDROGEN);
        assertThat(reactor.getHydrogenStored()).isEqualTo(FusionReactorInstance.BASE_MAX_HYDROGEN);
    }

    @Test
    void addWater_increasesStorage() {
        int accepted = reactor.addWater(100);
        assertThat(accepted).isEqualTo(100);
        assertThat(reactor.getWaterStored()).isEqualTo(100);
    }

    // === Heat Generation Tests ===
    @Test
    void hydrogen_generatesHeat() {
        reactor.addHydrogen(100);
        double initialTemp = reactor.getTemperature();
        reactor.tick();
        assertThat(reactor.getTemperature()).isGreaterThan(initialTemp);
    }

    @Test
    void moreHydrogen_generatesMoreHeat() {
        // Reactor with 100 hydrogen
        reactor.addHydrogen(100);
        reactor.tick();
        double tempWith100 = reactor.getTemperature();

        // Fresh reactor with 500 hydrogen
        setUp();
        reactor.addHydrogen(500);
        reactor.tick();
        double tempWith500 = reactor.getTemperature();

        assertThat(tempWith500).isGreaterThan(tempWith100);
    }

    // === Cooling Tests ===
    @Test
    void water_coolsReactor() {
        // Heat up reactor
        reactor.addHydrogen(200);
        for (int i = 0; i < 10; i++) reactor.tick();
        double hotTemp = reactor.getTemperature();

        // Add water and tick
        reactor.addWater(100);
        reactor.tick();
        assertThat(reactor.getTemperature()).isLessThan(hotTemp);
    }

    // === Fusion Reaction Tests ===
    @Test
    void fusion_producesHeliumAndEnergy_whenHotEnough() {
        // Heat up to fusion temperature
        reactor.addHydrogen(300);
        // Run until hot enough for fusion
        while (reactor.getTemperature() < FusionReactorInstance.FUSION_THRESHOLD) {
            reactor.tick();
        }

        // Add more hydrogen and tick
        reactor.addHydrogen(100);
        int initialHydrogen = reactor.getHydrogenStored();
        reactor.tick();

        // Should have produced helium and energy, consumed hydrogen
        assertThat(reactor.getHeliumBuffer()).isGreaterThan(0);
        assertThat(reactor.getEnergyBuffer()).isGreaterThan(0);
        assertThat(reactor.getHydrogenStored()).isLessThan(initialHydrogen);
    }

    @Test
    void moreHydrogen_enablesMoreFusion() {
        // Add hydrogen and let reactor run
        reactor.addHydrogen(500);

        // Run until we've consumed some hydrogen or melted down
        int maxTicks = 100;
        for (int i = 0; i < maxTicks && !reactor.hasMeltedDown() && reactor.getHydrogenStored() > 100; i++) {
            reactor.tick();
        }

        // Should have consumed hydrogen and produced outputs (tracked in totals)
        // Note: totals include everything produced during heating
        assertThat(reactor.getTotalHydrogenConsumed()).isGreaterThan(0);
    }

    // === Meltdown Tests ===
    @Test
    void meltdown_occursAbove1000C() {
        // Add lots of hydrogen and no water
        reactor.addHydrogen(FusionReactorInstance.BASE_MAX_HYDROGEN);

        // Tick until meltdown or max iterations
        int maxTicks = 1000;
        for (int i = 0; i < maxTicks && !reactor.hasMeltedDown(); i++) {
            reactor.tick();
        }

        assertThat(reactor.hasMeltedDown()).isTrue();
        assertThat(reactor.getReactorStatus()).isEqualTo(FusionReactorStatus.MELTDOWN);
        assertThat(reactor.status()).isEqualTo(MachineStatus.ERROR);
    }

    @Test
    void meltdown_clearsAllBuffers() {
        reactor.addHydrogen(500);
        reactor.addWater(100);
        heatReactorTo(500);

        // Force meltdown by adding more hydrogen without cooling
        reactor.addHydrogen(FusionReactorInstance.BASE_MAX_HYDROGEN);
        while (!reactor.hasMeltedDown()) {
            reactor.tick();
        }

        assertThat(reactor.getHydrogenStored()).isZero();
        assertThat(reactor.getWaterStored()).isZero();
        assertThat(reactor.getHeliumBuffer()).isZero();
        assertThat(reactor.getEnergyBuffer()).isZero();
    }

    @Test
    void meltdown_preventsAllOperations() {
        // Force meltdown
        reactor.addHydrogen(FusionReactorInstance.BASE_MAX_HYDROGEN);
        while (!reactor.hasMeltedDown()) reactor.tick();

        // Try to add more resources
        int accepted = reactor.addHydrogen(100);
        assertThat(accepted).isZero();
        accepted = reactor.addWater(100);
        assertThat(accepted).isZero();
    }

    // === Status Progression Tests ===
    @Test
    void statusProgression_coldToOptimal() {
        // Initial state is STARVED when no resources
        assertThat(reactor.getReactorStatus()).isIn(
            FusionReactorStatus.COLD,
            FusionReactorStatus.STARVED
        );

        reactor.addHydrogen(100);
        reactor.tick();

        // Should be warming up or still cold
        assertThat(reactor.getReactorStatus()).isIn(
            FusionReactorStatus.WARMING_UP,
            FusionReactorStatus.COLD,
            FusionReactorStatus.STARVED
        );

        // Heat to optimal
        heatReactorTo(300);
        assertThat(reactor.getReactorStatus()).isIn(
            FusionReactorStatus.OPTIMAL,
            FusionReactorStatus.HOT
        );
    }

    @Test
    void status_criticalNearMeltdown() {
        heatReactorTo(900);
        assertThat(reactor.getReactorStatus()).isEqualTo(FusionReactorStatus.CRITICAL);
    }

    // === Integration with Generator ===
    @Test
    void generator_canSupplyHydrogen() {
        when(generator.extractHydrogen(anyInt())).thenReturn(50);

        generator.setOutputType(ResourceGeneratorInstance.OutputType.HYDROGEN);
        generator.setOutputRate(100);

        // Generate resources
        for (int i = 0; i < 10; i++) generator.tick();

        // Transfer to reactor using ResourceProvider interface
        int extracted = generator.extractHydrogen(50);
        int h2Transferred = reactor.addHydrogen(extracted);

        assertThat(h2Transferred).isEqualTo(50);
        assertThat(reactor.getHydrogenStored()).isEqualTo(50);
    }

    @Test
    void generator_canSupplyWater() {
        when(generator.extractWater(anyInt())).thenReturn(30);

        generator.setOutputType(ResourceGeneratorInstance.OutputType.WATER);
        generator.setOutputRate(100);

        // Generate resources
        for (int i = 0; i < 10; i++) generator.tick();

        // Transfer to reactor using ResourceProvider interface
        int extracted = generator.extractWater(30);
        int h2oTransferred = reactor.addWater(extracted);

        assertThat(h2oTransferred).isEqualTo(30);
        assertThat(reactor.getWaterStored()).isEqualTo(30);
    }

    @Test
    void safeOperation_withBalancedInputs() {
        // Create two generators - one for each resource
        ResourceGeneratorInstance h2Gen = mock(ResourceGeneratorInstance.class);
        ResourceGeneratorInstance waterGen = mock(ResourceGeneratorInstance.class);

        when(h2Gen.extractHydrogen(anyInt())).thenReturn(5);
        when(waterGen.extractWater(anyInt())).thenReturn(20);

        h2Gen.setOutputType(ResourceGeneratorInstance.OutputType.HYDROGEN);
        h2Gen.setOutputRate(50);
        waterGen.setOutputType(ResourceGeneratorInstance.OutputType.WATER);
        waterGen.setOutputRate(100);

        // Pre-fill generator buffers
        for (int i = 0; i < 20; i++) {
            h2Gen.tick();
            waterGen.tick();
        }

        // Simulate controlled operation - always add water first
        for (int i = 0; i < 50; i++) {
            h2Gen.tick();
            waterGen.tick();

            // Add water first for cooling
            reactor.addWater(waterGen.extractWater(20));

            // Then small hydrogen bursts
            reactor.addHydrogen(h2Gen.extractHydrogen(5));
            reactor.tick();

            // Safety check - add more water if getting hot
            if (reactor.getTemperature() > 500) {
                reactor.addWater(waterGen.extractWater(50));
            }
        }

        // Should not have melted down with careful operation
        assertThat(reactor.hasMeltedDown()).isFalse();
    }

    @Test
    void dangerousOperation_tooMuchHydrogen() {
        when(generator.extractHydrogen(anyInt())).thenReturn(100);

        generator.setOutputType(ResourceGeneratorInstance.OutputType.HYDROGEN);
        generator.setOutputRate(100);

        // Dump lots of hydrogen without water
        for (int i = 0; i < 20; i++) {
            generator.tick();
            reactor.addHydrogen(generator.extractHydrogen(100));
            reactor.tick();
            if (reactor.hasMeltedDown()) break;
        }

        // Should have melted down from uncontrolled hydrogen
        assertThat(reactor.hasMeltedDown()).isTrue();
    }

    // === Helper Methods ===
    private void heatReactorTo(double targetTemp) {
        while (reactor.getTemperature() < targetTemp && !reactor.hasMeltedDown()) {
            if (reactor.getHydrogenStored() < 50) {
                reactor.addHydrogen(100);
            }
            reactor.tick();
        }
    }

    private ResourceGeneratorInstance createGenerator() {
        return mock(ResourceGeneratorInstance.class);
    }
}
