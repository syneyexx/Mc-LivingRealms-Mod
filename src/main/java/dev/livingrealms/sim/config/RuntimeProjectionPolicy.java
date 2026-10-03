package dev.livingrealms.sim.config;

import dev.livingrealms.sim.logistics.projection.CaravanProjectionConfig;
import dev.livingrealms.sim.materialization.MaterializationConfig;
import java.util.Objects;

/**
 * Single source of truth for translating the persisted singleplayer simulation profile into
 * physical projection budgets. Keeping this pure makes the runtime policy headless-testable.
 */
public final class RuntimeProjectionPolicy {
    private RuntimeProjectionPolicy() {}

    public static MaterializationConfig wildlife(SimulationConfig config) {
        Objects.requireNonNull(config, "config");
        int perGroup = Math.max(1, Math.min(32, Math.max(1, config.maxPhysicalWildlife())));
        return new MaterializationConfig(
                config.physicalRadiusBlocks(),
                config.regionalRadiusBlocks(),
                perGroup,
                config.maxPhysicalWildlife());
    }

    public static CaravanProjectionConfig caravans(SimulationConfig config) {
        Objects.requireNonNull(config, "config");
        return new CaravanProjectionConfig(config.physicalRadiusBlocks(), config.maxPhysicalCaravans());
    }

    /** Aircraft are visible farther away than ground entities but share the military performance envelope. */
    public static double aircraftRadiusBlocks(SimulationConfig config) {
        Objects.requireNonNull(config, "config");
        return Math.max(config.physicalRadiusBlocks() * 2.5D, config.physicalRadiusBlocks() + 256.0D);
    }

    public static int aircraftBudget(SimulationConfig config) {
        Objects.requireNonNull(config, "config");
        return Math.max(0, Math.min(64, config.maxPhysicalMilitaryEntities() / 3));
    }

    /** Ships need a slightly longer visual horizon, while still scaling with the selected profile. */
    public static double navalRadiusBlocks(SimulationConfig config) {
        Objects.requireNonNull(config, "config");
        return Math.max(480.0D, config.physicalRadiusBlocks() * 1.5D);
    }

    /** Civilian projections are bounded independently from population size but follow the profile envelope. */
    public static int citizenBudget(SimulationConfig config) {
        Objects.requireNonNull(config, "config");
        return Math.max(64, Math.min(160, config.maxPhysicalMilitaryEntities() + 48));
    }

    /** Citizens remain visible somewhat farther than wildlife so an approaching town looks inhabited. */
    public static double citizenRadiusBlocks(SimulationConfig config) {
        Objects.requireNonNull(config, "config");
        return Math.min(720.0D, Math.max(520.0D, config.physicalRadiusBlocks() * 1.5D));
    }

    /** Traveling civilians are sparse physical representatives of canonical MigrationGroup records. */
    public static int migrationBudget(SimulationConfig config) {Objects.requireNonNull(config,"config");return Math.max(4,Math.min(32,citizenBudget(config)/6));}
    public static double migrationRadiusBlocks(SimulationConfig config) {Objects.requireNonNull(config,"config");return Math.max(480.0D,config.physicalRadiusBlocks()*1.35D);}
    /** Pirates share the naval envelope but remain much more tightly bounded than real fleets. */
    public static int pirateBudget(SimulationConfig config) {Objects.requireNonNull(config,"config");return Math.max(2,Math.min(24,Math.max(2,config.maxPhysicalNavalEntities()/2)));}
    public static double pirateRadiusBlocks(SimulationConfig config) {Objects.requireNonNull(config,"config");return navalRadiusBlocks(config);}
}
