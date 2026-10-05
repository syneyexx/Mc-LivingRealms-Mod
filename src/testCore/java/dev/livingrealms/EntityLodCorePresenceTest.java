package dev.livingrealms;

import dev.livingrealms.sim.civilian.CitizenMaterializationPlanner;
import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.List;

/** Entity/detail LOD must activate life near already-existing physical cores, never create the core itself. */
public final class EntityLodCorePresenceTest {
    private EntityLodCorePresenceTest() {}

    public static void main(String[] args) {
        ordinarySettlementWaitsForPhysicalCore();
        anchoredForeignCoreProjectsWithoutLrRebuild();
        globalBudgetRemainsHardBound();
        System.out.println("PASS entity LOD: citizens require physical core + foreign anchors remain visible + hard budget");
    }

    private static void ordinarySettlementWaitsForPhysicalCore() {
        SimulationState state = new SimulationState(901L);
        Faction faction = new Faction(state.nextId(), "LOD Realm", "Mayor");
        Settlement town = new Settlement(state.nextId(), "Town", new SimPosition(0, 0), 700, 800);
        faction.addSettlement(town);
        state.addFaction(faction);
        var before = CitizenMaterializationPlanner.plan(state, state.factions(), List.of(new SimPosition(10, 0)), 600, 40);
        check(before.isEmpty(), "entity LOD must not make an unbuilt settlement look physically inhabited");
        town.markConstructionCompleted("roadgraph:arterial:0");
        var after = CitizenMaterializationPlanner.plan(state, state.factions(), List.of(new SimPosition(10, 0)), 600, 40);
        check(!after.isEmpty(), "citizens should project near an already-present core");
    }

    private static void anchoredForeignCoreProjectsWithoutLrRebuild() {
        SimulationState state = new SimulationState(902L);
        Faction faction = new Faction(state.nextId(), "Adopter", "Steward");
        Settlement foreign = new Settlement(state.nextId(), "Foreign Village", new SimPosition(0, 0), 180, 220,
                SettlementOrigin.FOREIGN_ADOPTED, true, DevelopmentMode.AUTO);
        faction.addSettlement(foreign);
        state.addFaction(faction);
        var projected = CitizenMaterializationPlanner.plan(state, state.factions(), List.of(new SimPosition(0, 0)), 600, 20);
        check(!projected.isEmpty(), "anchored foreign settlement already has a physical core");
    }

    private static void globalBudgetRemainsHardBound() {
        SimulationState state = new SimulationState(903L);
        Faction faction = new Faction(state.nextId(), "Dense Realm", "Queen");
        for (int i = 0; i < 6; i++) {
            Settlement city = new Settlement(state.nextId(), "City " + i, new SimPosition(i * 80.0, 0), 5000, 5500);
            city.markConstructionCompleted("roadgraph:arterial:0");
            faction.addSettlement(city);
        }
        state.addFaction(faction);
        int budget = 23;
        var projected = CitizenMaterializationPlanner.plan(state, state.factions(), List.of(new SimPosition(0, 0)), 800, budget);
        check(projected.size() <= budget, "citizen projection exceeded global budget: " + projected.size());
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
