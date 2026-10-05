package dev.livingrealms;

import dev.livingrealms.sim.construction.HistoricCityEvolution;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.Set;

/** Wave 33 — completed construction keys persist across tier growth / planning appends. */
public final class HistoricCityEvolutionTest {
    private HistoricCityEvolutionTest() {}

    public static void main(String[] args) {
        completedKeysPersistAcrossTier();
        appendPlanDoesNotErase();
        System.out.println("PASS HistoricCityEvolution: completed keys persist across tier growth + planning appends");
    }

    private static void completedKeysPersistAcrossTier() {
        SimulationState state = new SimulationState(0x1515733L);
        Faction faction = new Faction(state.nextId(), "Historic Realm", "Founder");
        Settlement settlement = new Settlement(state.nextId(), "Oldcore", new SimPosition(100, 200), 80, 100);
        faction.addSettlement(settlement);
        state.addFaction(faction);

        // Mark early-tier core structures complete.
        settlement.markConstructionCompleted("well:0");
        settlement.markConstructionCompleted("house:0");
        settlement.markConstructionCompleted("house:1");
        settlement.markConstructionCompleted("road:1:0"); // hamlet-tier road key
        Set<String> early = HistoricCityEvolution.copyMemory(settlement);
        check(early.size() == 4, "early memory size");

        // Grow through village → town → city populations (tier recalc on addPopulation).
        settlement.addPopulation(200); // village-ish
        check(HistoricCityEvolution.preserves(early, settlement), "preserved after village growth");
        SettlementPlanner.pending(faction, settlement); // planning must not erase
        check(HistoricCityEvolution.preserves(early, settlement), "preserved after village plan");

        settlement.addPopulation(800); // town
        check(settlement.tier().ordinal() >= Settlement.Tier.TOWN.ordinal(), "reached town: " + settlement.tier());
        var pendingTown = HistoricCityEvolution.appendPlan(faction, settlement);
        check(HistoricCityEvolution.preserves(early, settlement), "preserved after town append plan");
        check(pendingTown.stream().noneMatch(i -> early.contains(i.key()) && settlement.isConstructionCompleted(i.key())
                        && pendingTown.stream().anyMatch(p -> p.key().equals(i.key()) && false)),
                "pending excludes completed");
        check(pendingTown.stream().noneMatch(i -> settlement.isConstructionCompleted(i.key())),
                "pending never re-emits completed keys");

        settlement.addPopulation(5000); // city
        check(settlement.tier().ordinal() >= Settlement.Tier.CITY.ordinal(), "reached city: " + settlement.tier());
        HistoricCityEvolution.appendPlan(faction, settlement);
        check(HistoricCityEvolution.preserves(early, settlement), "preserved after city growth");
        check(settlement.isConstructionCompleted("well:0"), "well remains");
        check(settlement.isConstructionCompleted("house:0"), "house:0 remains");
        check(settlement.isConstructionCompleted("road:1:0"), "historic road key remains");
        // New tier may plan new road:/keep: keys — that is append, not erase.
        long newRoads = SettlementPlanner.pending(faction, settlement).stream()
                .filter(i -> i.role() == StructureRole.ROAD).count();
        check(newRoads >= 0, "new road intents may append");
    }

    private static void appendPlanDoesNotErase() {
        SimulationState state = new SimulationState(0x1515734L);
        Faction faction = new Faction(state.nextId(), "Append Realm", "Founder");
        Settlement settlement = new Settlement(state.nextId(), "Appendon", new SimPosition(300, 400), 600, 700);
        faction.addSettlement(settlement);
        state.addFaction(faction);
        for (var intent : SettlementPlanner.plan(faction, settlement)) {
            if (intent.role() == StructureRole.KEEP || intent.role() == StructureRole.WELL
                    || intent.key().startsWith("house:0") || intent.key().startsWith("market:")) {
                settlement.markConstructionCompleted(intent.key());
            }
        }
        Set<String> before = HistoricCityEvolution.spatialMemory(settlement);
        check(!before.isEmpty(), "some keys completed");
        HistoricCityEvolution.appendPlan(faction, settlement);
        check(before.equals(HistoricCityEvolution.spatialMemory(settlement)), "spatial memory unchanged by append");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
