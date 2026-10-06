package dev.livingrealms;

import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.RoadLifeEngine;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import dev.livingrealms.sim.worldgen.StarterRoadsideSitePlanner;
import java.util.HashSet;
import java.util.Set;

/** Determinism and canonical-bootstrap parity for day-zero roadside worldgen anchors. */
public final class StarterRoadsideSitePlannerTest {
    private StarterRoadsideSitePlannerTest() {}

    public static void main(String[] args) {
        sameSeedProducesSamePlan();
        idsAndSpacingAreStable();
        bootstrapConsumesExactPlan();
        System.out.println("PASS starter roadside sites: deterministic + stable ids + bootstrap parity");
    }

    private static void sameSeedProducesSamePlan() {
        var layout = StarterCivilizationLayoutPlanner.plan(0x51DE51DEL);
        var a = StarterRoadsideSitePlanner.plan(layout);
        var b = StarterRoadsideSitePlanner.plan(layout);
        check(a.equals(b), "same seed/layout must produce identical roadside plan");
        check(!a.isEmpty(), "starter route network should produce roadside anchors");
    }

    private static void idsAndSpacingAreStable() {
        var sites = StarterRoadsideSitePlanner.plan(
                StarterCivilizationLayoutPlanner.plan(0x991177L));
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < sites.size(); i++) {
            var site = sites.get(i);
            check(StarterRoadsideSitePlanner.isStarterRoadsideSiteId(site.stableSiteId()),
                    "starter roadside id outside reserved range");
            check(ids.add(site.stableSiteId()), "duplicate starter roadside id");
            for (int j = 0; j < i; j++) {
                check(site.position().distanceTo(sites.get(j).position())
                                >= StarterRoadsideSitePlanner.MIN_SITE_SPACING - 1.0e-9,
                        "starter roadside spacing floor violated");
            }
        }
    }

    private static void bootstrapConsumesExactPlan() {
        long seed = 0x771155L;
        var expected = StarterRoadsideSitePlanner.plan(
                StarterCivilizationLayoutPlanner.plan(seed));
        SimulationState state = new SimulationState(seed);
        DemoSeeder.seed(state);

        check(state.roadsideSites().size() == expected.size(),
                "bootstrap roadside count mismatch");
        check(RoadLifeEngine.ensureCorridorSites(state) == 0,
                "runtime corridor reconciliation must not add day-zero roadside anchors");
        check(state.peekNextId() >= StarterRoadsideSitePlanner.STARTER_ROADSIDE_SITE_ID_LIMIT,
                "runtime id watermark must clear reserved starter roadside range");
        for (var plan : expected) {
            var actual = state.findRoadsideSite(plan.stableSiteId()).orElseThrow();
            check(actual.type() == plan.type(), "roadside type mismatch " + plan.stableKey());
            check(actual.position().equals(plan.position()), "roadside position mismatch " + plan.stableKey());
            check(actual.relatedSettlementId() == plan.relatedSettlementId(),
                    "roadside settlement mismatch " + plan.stableKey());
            check(actual.relatedRouteId() == plan.relatedRouteId(),
                    "roadside route mismatch " + plan.stableKey());
            check(actual.createdDay() == 0, "starter roadside site must be day zero");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
