package dev.livingrealms;

import dev.livingrealms.sim.config.SimulationConfig;
import dev.livingrealms.sim.runtime.ProjectionBudget;
import dev.livingrealms.sim.runtime.RuntimeBudgetController;

/**
 * Physical construction throughput adapts to runtime pressure; canonical intent stays untouched.
 */
public final class ConstructionBudgetAdaptationTest {
    public static void main(String[] args) {
        healthyHigherThanPressure();
        softBetweenHealthyAndHard();
        floorsHold();
        System.out.println("ConstructionBudgetAdaptationTest OK");
    }

    private static void healthyHigherThanPressure() {
        SimulationConfig config = new SimulationConfig(320, 2048, 160, 24, 96, 32, 480, 1, 1.25, .20, .78, 12.0);
        int healthy = ProjectionBudget.healthy(config, 1).constructionBlockOpsPerTick();
        int soft = ProjectionBudget.healthy(config, 1)
                .withPressure(RuntimeBudgetController.Pressure.SOFT).constructionBlockOpsPerTick();
        int hard = ProjectionBudget.healthy(config, 1)
                .withPressure(RuntimeBudgetController.Pressure.HARD).constructionBlockOpsPerTick();
        check(healthy == 480, "healthy uses profile ops");
        check(soft < healthy, "soft slows physical projection");
        check(hard < soft, "hard slows further");
    }

    private static void softBetweenHealthyAndHard() {
        SimulationConfig config = SimulationConfig.defaults();
        int healthy = ProjectionBudget.healthy(config, 2).constructionBlockOpsPerTick();
        int soft = ProjectionBudget.healthy(config, 2)
                .withPressure(RuntimeBudgetController.Pressure.SOFT).constructionBlockOpsPerTick();
        int hard = ProjectionBudget.healthy(config, 2)
                .withPressure(RuntimeBudgetController.Pressure.HARD).constructionBlockOpsPerTick();
        check(soft > hard && soft < healthy, "soft is intermediate");
    }

    private static void floorsHold() {
        SimulationConfig tiny = new SimulationConfig(320, 2048, 10, 2, 8, 2, 320, 1, 1.25, .20, .78, 12.0);
        int hard = ProjectionBudget.healthy(tiny, 1)
                .withPressure(RuntimeBudgetController.Pressure.HARD).constructionBlockOpsPerTick();
        check(hard >= 48, "hard construction floor");
        int soft = ProjectionBudget.healthy(tiny, 1)
                .withPressure(RuntimeBudgetController.Pressure.SOFT).constructionBlockOpsPerTick();
        check(soft >= 96, "soft construction floor");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
