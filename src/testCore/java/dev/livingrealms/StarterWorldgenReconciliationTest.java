package dev.livingrealms;

import dev.livingrealms.sim.construction.SettlementCoreCompleteness;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.ConstructionOrigin;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.transport.TransportNetworkEngine;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import dev.livingrealms.sim.worldgen.SettlementInitialWorldgenPlan;
import dev.livingrealms.sim.worldgen.StarterRegionalRoutePlanner;
import dev.livingrealms.sim.worldgen.StarterWorldgenCompletion;
import java.util.HashSet;
import java.util.Set;

/** Regression gate separating day-zero worldgen fabric from later runtime construction. */
public final class StarterWorldgenReconciliationTest {
    private StarterWorldgenReconciliationTest() {}

    public static void main(String[] args) {
        canonicalBootstrapUsesSharedStarterRouteIds();
        dynamicTransportDoesNotDuplicateStarterEdges();
        worldgenReceiptsSuppressBaselineButNotFutureGrowth();
        fallbackPrimaryEconomyBelongsToWorldgen();
        starterSettlementsAreCoreComplete();
        constructionOriginOrdinalsRemainBackwardCompatible();
        System.out.println("PASS starter worldgen reconciliation: shared routes + no baseline duplication + runtime growth preserved");
    }

    private static void canonicalBootstrapUsesSharedStarterRouteIds() {
        long seed = 0x771122L;
        SimulationState state = new SimulationState(seed);
        DemoSeeder.seed(state);
        var planned = StarterRegionalRoutePlanner.plan(StarterCivilizationLayoutPlanner.plan(seed));
        Set<Long> canonical = new HashSet<>();
        for (TransportRoute route : state.routes()) canonical.add(route.id());
        for (var route : planned) {
            check(canonical.contains(route.stableRouteId()),
                    "canonical bootstrap missing shared starter route " + route.stableKey());
            check(StarterRegionalRoutePlanner.isStarterRouteId(route.stableRouteId()),
                    "starter route id classifier rejected " + route.stableRouteId());
        }
    }

    private static void dynamicTransportDoesNotDuplicateStarterEdges() {
        long seed = 0x771133L;
        SimulationState state = new SimulationState(seed);
        DemoSeeder.seed(state);
        var planned = StarterRegionalRoutePlanner.plan(StarterCivilizationLayoutPlanner.plan(seed));
        int starterCount = planned.size();
        check(state.routes().stream().filter(r -> StarterRegionalRoutePlanner.isStarterRouteId(r.id())).count() == starterCount,
                "starter route bootstrap count mismatch");
        check(state.peekNextId() >= StarterRegionalRoutePlanner.STARTER_ROUTE_ID_LIMIT,
                "runtime id watermark must clear reserved starter route range");

        new TransportNetworkEngine().ensureRoutes(state);
        for (var starter : planned) {
            long sameEnds = state.routes().stream().filter(route ->
                    route.ownerFactionId() == starter.factionId()
                            && ((route.fromSettlementId() == starter.fromSettlementId()
                            && route.toSettlementId() == starter.toSettlementId())
                            || (route.fromSettlementId() == starter.toSettlementId()
                            && route.toSettlementId() == starter.fromSettlementId()))).count();
            check(sameEnds == 1,
                    "dynamic transport duplicated starter edge " + starter.stableKey() + " count=" + sameEnds);
        }
    }

    private static void worldgenReceiptsSuppressBaselineButNotFutureGrowth() {
        long seed = 0x551177L;
        SimulationState state = new SimulationState(seed);
        DemoSeeder.seed(state);
        int marked = StarterWorldgenCompletion.adoptPlannedBaseline(state);
        check(marked > 0, "fresh bootstrap should adopt worldgen completion metadata");

        var layout = StarterCivilizationLayoutPlanner.plan(seed);
        for (var realm : layout.realms()) {
            var faction = state.findFaction(realm.factionId()).orElseThrow();
            for (var starter : realm.settlements()) {
                Settlement settlement = state.findSettlement(starter.id()).orElseThrow();
                check(SettlementPlanner.pending(faction, settlement).isEmpty(),
                        "runtime planner still sees day-zero backlog for " + starter.stableKey());
                for (var intent : SettlementPlanner.plan(faction, settlement)) {
                    check(settlement.constructionOrigin(intent.key()) == ConstructionOrigin.WORLDGEN,
                            "day-zero completion origin is not WORLDGEN: " + intent.key());
                }
            }
        }

        var firstRealm = layout.realms().getFirst();
        var faction = state.findFaction(firstRealm.factionId()).orElseThrow();
        Settlement village = firstRealm.settlements().stream()
                .map(s -> state.findSettlement(s.id()).orElseThrow())
                .filter(s -> s.role() == dev.livingrealms.sim.faction.SettlementRole.VILLAGE)
                .findFirst().orElseThrow();
        village.addPopulation(2_400);
        village.addHousing(2_500);
        check(!SettlementPlanner.pending(faction, village).isEmpty(),
                "future growth must remain available to runtime construction");
    }


    private static void fallbackPrimaryEconomyBelongsToWorldgen() {
        long seed = 0x4455AA11L;
        SimulationState state = new SimulationState(seed);
        DemoSeeder.seed(state);
        StarterWorldgenCompletion.adoptPlannedBaseline(state);

        var layout = StarterCivilizationLayoutPlanner.plan(seed);
        for (SettlementInitialWorldgenPlan plan : SettlementInitialWorldgenPlan.buildAll(layout)) {
            Settlement settlement = state.findSettlement(plan.settlementId()).orElseThrow();
            boolean hasLumber = plan.intents().stream().anyMatch(i ->
                    i.role() == StructureRole.LUMBER_CAMP && i.key().equals("lumber_camp:0"));
            boolean hasMine = plan.intents().stream().anyMatch(i ->
                    i.role() == StructureRole.MINE && i.key().equals("mine:0"));
            boolean hasFishery = plan.intents().stream().anyMatch(i ->
                    i.role() == StructureRole.FISHERY && i.key().equals("fishery:0"));

            check(hasLumber == (plan.tier().ordinal() >= Settlement.Tier.HAMLET.ordinal()),
                    "starter lumber baseline tier mismatch: " + plan.stableKey());
            check(hasMine == (plan.tier().ordinal() >= Settlement.Tier.VILLAGE.ordinal()),
                    "starter mine baseline tier mismatch: " + plan.stableKey());
            check(!hasFishery,
                    "fishery must wait for discovered geography: " + plan.stableKey());

            if (hasLumber) {
                check(settlement.constructionOrigin("lumber_camp:0") == ConstructionOrigin.WORLDGEN,
                        "starter lumber receipt not WORLDGEN: " + plan.stableKey());
            }
            if (hasMine) {
                check(settlement.constructionOrigin("mine:0") == ConstructionOrigin.WORLDGEN,
                        "starter mine receipt not WORLDGEN: " + plan.stableKey());
            }
        }
    }

    private static void starterSettlementsAreCoreComplete() {
        long seed = 0x55CC7711L;
        SimulationState state = new SimulationState(seed);
        DemoSeeder.seed(state);
        StarterWorldgenCompletion.adoptPlannedBaseline(state);

        var layout = StarterCivilizationLayoutPlanner.plan(seed);
        for (var realm : layout.realms()) {
            var faction = state.findFaction(realm.factionId()).orElseThrow();
            for (var starter : realm.settlements()) {
                Settlement settlement = state.findSettlement(starter.id()).orElseThrow();
                var status = SettlementCoreCompleteness.analyze(faction, settlement);
                check(status.complete(),
                        "starter settlement is not core-complete after worldgen adoption: "
                                + starter.stableKey() + " gaps=" + status);
            }
        }
    }

    private static void constructionOriginOrdinalsRemainBackwardCompatible() {
        check(ConstructionOrigin.MATERIALIZED.ordinal() == 0, "MATERIALIZED ordinal changed");
        check(ConstructionOrigin.FOREIGN_ADOPTED.ordinal() == 1, "FOREIGN_ADOPTED ordinal changed");
        check(ConstructionOrigin.WORLDGEN.ordinal() == 2, "WORLDGEN must be append-only");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
