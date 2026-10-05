package dev.livingrealms.sim.runtime;

import dev.livingrealms.sim.config.SimulationConfig;
import dev.livingrealms.sim.logistics.projection.CaravanProjectionConfig;
import dev.livingrealms.sim.materialization.MaterializationConfig;
import java.util.Objects;

/**
 * Shared physical projection envelope for a tick. Canonical simulation remains rich; this type
 * only bounds how many Minecraft entities each lane may materialize under profile + pressure.
 *
 * <p>Lane budgets are absolute caps for the tick (except wildlife, which is per-player and then
 * multiplied by player count inside {@link MaterializationConfig}/{@code MaterializationPlanner}).
 */
public final class ProjectionBudget {
    public enum Lane {
        CITIZENS,
        WILDLIFE,
        MILITARY,
        CARAVANS,
        JOURNEYS,
        SHIPS,
        AIRCRAFT
    }

    private final SimulationConfig config;
    private final RuntimeBudgetController.Pressure pressure;
    private final int playerCount;

    private ProjectionBudget(SimulationConfig config, RuntimeBudgetController.Pressure pressure, int playerCount) {
        this.config = Objects.requireNonNull(config, "config");
        this.pressure = pressure == null ? RuntimeBudgetController.Pressure.HEALTHY : pressure;
        this.playerCount = Math.max(1, playerCount);
    }

    public static ProjectionBudget forPlayers(SimulationConfig config, int playerCount) {
        return new ProjectionBudget(config, RuntimePressureBridge.current(), playerCount);
    }

    public static ProjectionBudget healthy(SimulationConfig config, int playerCount) {
        return new ProjectionBudget(config, RuntimeBudgetController.Pressure.HEALTHY, playerCount);
    }

    public ProjectionBudget withPressure(RuntimeBudgetController.Pressure next) {
        return new ProjectionBudget(config, next, playerCount);
    }

    public RuntimeBudgetController.Pressure pressure() {
        return pressure;
    }

    public int playerCount() {
        return playerCount;
    }

    /** Absolute lane cap (wildlife = per-player base used by MaterializationPlanner). */
    public int lane(Lane lane) {
        int base = switch (lane) {
            case CITIZENS -> Math.max(64, Math.min(160, config.maxPhysicalMilitaryEntities() + 48));
            case WILDLIFE -> config.maxPhysicalWildlife();
            case MILITARY -> config.maxPhysicalMilitaryEntities();
            case CARAVANS -> config.maxPhysicalCaravans();
            case JOURNEYS -> {
                int citizens = Math.max(64, Math.min(160, config.maxPhysicalMilitaryEntities() + 48));
                yield Math.max(4, Math.min(24, citizens / 8));
            }
            case SHIPS -> config.maxPhysicalNavalEntities();
            case AIRCRAFT -> Math.max(0, Math.min(64, config.maxPhysicalMilitaryEntities() / 3));
        };
        return scaleLane(lane, base);
    }

    /** Aggressive wildlife LOD: lower per-group caps and per-player budget under pressure. */
    public MaterializationConfig wildlifeMaterialization() {
        int configured = Math.max(0, config.maxPhysicalWildlife());
        int perPlayer = lane(Lane.WILDLIFE);
        // MaterializationConfig requires per-group > 0 even when the per-player budget is zero.
        int perGroup = Math.max(1, Math.min(32, Math.max(1, configured)));
        if (configured > 0 && pressure == RuntimeBudgetController.Pressure.SOFT) {
            perGroup = Math.max(1, (int) Math.round(perGroup * 0.55));
        } else if (configured > 0 && pressure == RuntimeBudgetController.Pressure.HARD) {
            perGroup = Math.max(1, (int) Math.round(perGroup * 0.30));
        }
        return new MaterializationConfig(
                config.physicalRadiusBlocks(),
                config.regionalRadiusBlocks(),
                perGroup,
                perPlayer);
    }

    public CaravanProjectionConfig caravans() {
        return new CaravanProjectionConfig(config.physicalRadiusBlocks(), lane(Lane.CARAVANS));
    }

    /** Physical construction throughput only; canonical intent queues stay deterministic. */
    public int constructionBlockOpsPerTick() {
        int base = Math.max(320, config.constructionBlockOpsPerTick());
        return switch (pressure) {
            case HEALTHY -> base;
            case SOFT -> Math.max(96, (int) Math.round(base * 0.62));
            case HARD -> Math.max(48, (int) Math.round(base * 0.34));
        };
    }

    private int scaleLane(Lane lane, int base) {
        if (base <= 0) return 0;
        double factor = switch (pressure) {
            case HEALTHY -> 1.0;
            case SOFT -> switch (lane) {
                case WILDLIFE -> 0.45;
                case CITIZENS -> 0.88;
                case MILITARY -> 0.78;
                case CARAVANS -> 0.82;
                case JOURNEYS -> 0.72;
                case SHIPS -> 0.78;
                case AIRCRAFT -> 0.72;
            };
            case HARD -> switch (lane) {
                case WILDLIFE -> 0.18;
                case CITIZENS -> 0.72;
                case MILITARY -> 0.58;
                case CARAVANS -> 0.62;
                case JOURNEYS -> 0.52;
                case SHIPS -> 0.58;
                case AIRCRAFT -> 0.52;
            };
        };
        int floor = (lane == Lane.WILDLIFE && pressure == RuntimeBudgetController.Pressure.HARD)
                ? Math.min(8, base) : 1;
        return Math.min(base, Math.max(floor, (int) Math.round(base * factor)));
    }
}
