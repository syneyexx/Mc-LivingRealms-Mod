package dev.livingrealms;

import dev.livingrealms.sim.config.SimulationConfig;
import dev.livingrealms.sim.runtime.ProjectionBudget;
import dev.livingrealms.sim.runtime.RuntimeBudgetController;
import dev.livingrealms.sim.runtime.RuntimePressureBridge;

/**
 * Headless coverage for shared ProjectionBudget lane caps and wildlife LOD under pressure.
 */
public final class ProjectionBudgetTest {
    public static void main(String[] args) {
        RuntimePressureBridge.reset();
        healthyMatchesProfile();
        softPressureShrinksWildlifeAggressively();
        hardPressureFloorsWildlifeAndConstruction();
        lanesPreferNearbyEnvelopeNotFullEntityBudget();
        bridgePublishesPressure();
        System.out.println("ProjectionBudgetTest OK");
    }

    private static void healthyMatchesProfile() {
        SimulationConfig config = SimulationConfig.defaults();
        ProjectionBudget budget = ProjectionBudget.healthy(config, 2);
        check(budget.lane(ProjectionBudget.Lane.WILDLIFE) == config.maxPhysicalWildlife(), "healthy wildlife per-player");
        check(budget.lane(ProjectionBudget.Lane.MILITARY) == config.maxPhysicalMilitaryEntities(), "healthy military");
        check(budget.lane(ProjectionBudget.Lane.CARAVANS) == config.maxPhysicalCaravans(), "healthy caravans");
        check(budget.lane(ProjectionBudget.Lane.SHIPS) == config.maxPhysicalNavalEntities(), "healthy ships");
        check(budget.lane(ProjectionBudget.Lane.CITIZENS) >= 64, "citizens bounded");
        check(budget.lane(ProjectionBudget.Lane.JOURNEYS) >= 4, "journeys bounded");
        check(budget.lane(ProjectionBudget.Lane.AIRCRAFT) >= 0, "aircraft bounded");
        check(budget.constructionBlockOpsPerTick() == Math.max(320, config.constructionBlockOpsPerTick()),
                "healthy construction throughput");
        var wildlife = budget.wildlifeMaterialization();
        check(wildlife.maxAnimalsPerPlayer() == config.maxPhysicalWildlife(), "wildlife config per-player");
        check(wildlife.maxAnimalsPerGroup() == 32, "healthy wildlife per-group cap");
    }

    private static void softPressureShrinksWildlifeAggressively() {
        SimulationConfig config = SimulationConfig.defaults();
        ProjectionBudget soft = ProjectionBudget.healthy(config, 1).withPressure(RuntimeBudgetController.Pressure.SOFT);
        int healthyWildlife = ProjectionBudget.healthy(config, 1).lane(ProjectionBudget.Lane.WILDLIFE);
        check(soft.lane(ProjectionBudget.Lane.WILDLIFE) < healthyWildlife, "soft wildlife reduced");
        check(soft.wildlifeMaterialization().maxAnimalsPerGroup() < 32, "soft wildlife LOD per-group");
        check(soft.lane(ProjectionBudget.Lane.CITIZENS)
                        < ProjectionBudget.healthy(config, 1).lane(ProjectionBudget.Lane.CITIZENS),
                "soft citizens reduced mildly");
    }

    private static void hardPressureFloorsWildlifeAndConstruction() {
        SimulationConfig config = SimulationConfig.defaults();
        ProjectionBudget hard = ProjectionBudget.healthy(config, 1).withPressure(RuntimeBudgetController.Pressure.HARD);
        check(hard.lane(ProjectionBudget.Lane.WILDLIFE) >= 8, "hard wildlife floor");
        check(hard.wildlifeMaterialization().maxAnimalsPerGroup() <= 10, "hard wildlife sparse");
        check(hard.constructionBlockOpsPerTick()
                        < ProjectionBudget.healthy(config, 1).constructionBlockOpsPerTick(),
                "hard construction slower");
        check(hard.constructionBlockOpsPerTick() >= 48, "construction floor");
    }

    private static void lanesPreferNearbyEnvelopeNotFullEntityBudget() {
        SimulationConfig config = SimulationConfig.defaults();
        ProjectionBudget budget = ProjectionBudget.healthy(config, 1);
        int sum = budget.lane(ProjectionBudget.Lane.CITIZENS)
                + budget.lane(ProjectionBudget.Lane.WILDLIFE)
                + budget.lane(ProjectionBudget.Lane.MILITARY)
                + budget.lane(ProjectionBudget.Lane.CARAVANS)
                + budget.lane(ProjectionBudget.Lane.JOURNEYS)
                + budget.lane(ProjectionBudget.Lane.SHIPS)
                + budget.lane(ProjectionBudget.Lane.AIRCRAFT);
        check(sum < SimulationConfig.MAX_PHYSICAL_ENTITY_BUDGET, "lanes must not assume full entity budget");
        check(budget.lane(ProjectionBudget.Lane.WILDLIFE) <= config.maxPhysicalWildlife(), "wildlife not inflated");
    }

    private static void bridgePublishesPressure() {
        RuntimePressureBridge.reset();
        check(RuntimePressureBridge.current() == RuntimeBudgetController.Pressure.HEALTHY, "bridge reset");
        RuntimePressureBridge.publish(RuntimeBudgetController.Pressure.HARD);
        ProjectionBudget fromBridge = ProjectionBudget.forPlayers(SimulationConfig.defaults(), 1);
        check(fromBridge.pressure() == RuntimeBudgetController.Pressure.HARD, "forPlayers reads bridge");
        RuntimePressureBridge.reset();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
