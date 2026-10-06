package dev.livingrealms;

import dev.livingrealms.sim.construction.StructureBlueprintFactory;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import dev.livingrealms.sim.worldgen.SettlementInitialWorldgenPlan;
import dev.livingrealms.sim.worldgen.StarterRegionalRoutePlanner;

public final class WorldgenStartupBenchmark {
    private WorldgenStartupBenchmark() {}

    public static void main(String[] args) {
        long seed = 0x771122334455L;

        long t0 = System.nanoTime();
        SimulationState state = new SimulationState(seed);
        DemoSeeder.seed(state);
        long t1 = System.nanoTime();

        var layout = StarterCivilizationLayoutPlanner.plan(seed);
        long t2 = System.nanoTime();

        var routes = StarterRegionalRoutePlanner.plan(layout);
        long t3 = System.nanoTime();

        var physical = SettlementInitialWorldgenPlan.buildAll(layout);
        long t4 = System.nanoTime();

        var aster = layout.realm("aster");
        var capitalStarter = aster.capital();
        var capital = SettlementInitialWorldgenPlan.buildOne(aster, capitalStarter);
        long t5 = System.nanoTime();
        int blueprintPlacements = 0;
        for (var intent : capital.intents()) {
            blueprintPlacements += StructureBlueprintFactory
                    .create(intent, capital.architecture())
                    .placements().size();
        }
        long t6 = System.nanoTime();

        System.out.println("BENCH demoSeederMs=" + ms(t0, t1)
                + " layoutMs=" + ms(t1, t2)
                + " routeTopologyMs=" + ms(t2, t3)
                + " physicalAllMs=" + ms(t3, t4)
                + " capitalPlanMs=" + ms(t4, t5)
                + " capitalBlueprintMs=" + ms(t5, t6)
                + " capitalIntents=" + capital.intents().size()
                + " capitalBlueprintPlacements=" + blueprintPlacements
                + " settlements=" + physical.size()
                + " routes=" + routes.size());
    }

    private static long ms(long a, long b) {
        return (b - a) / 1_000_000L;
    }
}
