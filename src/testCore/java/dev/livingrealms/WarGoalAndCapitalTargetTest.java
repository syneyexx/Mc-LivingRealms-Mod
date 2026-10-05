package dev.livingrealms;

import dev.livingrealms.sim.diplomacy.DiplomacyEngine;
import dev.livingrealms.sim.diplomacy.WarGoalType;
import dev.livingrealms.sim.diplomacy.WarState;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SettlementTransfer;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** G1–G3: capital targeting, war goals, single active war per pair. */
public final class WarGoalAndCapitalTargetTest {
    private WarGoalAndCapitalTargetTest() {}

    public static void main(String[] args) {
        capitalIsNotListOrder();
        discoverWarsTargetsCapital();
        singleWarPerPair();
        System.out.println("PASS war goals: capital target + discoverWars + unique war pair");
    }

    private static void capitalIsNotListOrder() {
        Faction f = new Faction(1, "Realm", "Ruler");
        Settlement hamlet = new Settlement(10, "FirstInList", new SimPosition(0, 0), 80, 90);
        Settlement capital = new Settlement(30, "TrueCapital", new SimPosition(100, 0), 5000, 5500);
        Settlement town = new Settlement(20, "MiddleTown", new SimPosition(50, 0), 800, 900);
        f.addSettlement(hamlet);
        f.addSettlement(town);
        f.addSettlement(capital);
        Settlement picked = SettlementTransfer.capitalTarget(f);
        check(picked != null && picked.id() == capital.id(), "capital must be highest tier/pop not list index 0");
    }

    private static void discoverWarsTargetsCapital() {
        SimulationState state = new SimulationState(0x6111L);
        Faction a = new Faction(state.nextId(), "A Realm", "A");
        Faction b = new Faction(state.nextId(), "B Realm", "B");
        Settlement outpost = new Settlement(state.nextId(), "Outpost", new SimPosition(0, 0), 100, 120);
        Settlement capital = new Settlement(state.nextId(), "B Capital", new SimPosition(2000, 0), 4000, 4500);
        a.addSettlement(new Settlement(state.nextId(), "A Home", new SimPosition(-2000, 0), 3000, 3200));
        b.addSettlement(outpost);
        b.addSettlement(capital);
        a.relationWith(b.id()).declareWar();
        b.relationWith(a.id()).declareWar();
        state.addFaction(a);
        state.addFaction(b);
        new DiplomacyEngine().simulateDay(state, new DeterministicRng(1));
        WarState war = state.wars().stream().filter(WarState::active).findFirst().orElseThrow();
        check(war.targetSettlementId() == capital.id(), "war target must be capital not first settlement");
        check(war.goal() != null, "war goal assigned");
    }

    private static void singleWarPerPair() {
        SimulationState state = new SimulationState(0x6333L);
        Faction a = new Faction(state.nextId(), "North", "N");
        Faction b = new Faction(state.nextId(), "South", "S");
        a.addSettlement(new Settlement(state.nextId(), "N1", new SimPosition(0, 0), 1000, 1100));
        b.addSettlement(new Settlement(state.nextId(), "S1", new SimPosition(3000, 0), 1000, 1100));
        a.relationWith(b.id()).declareWar();
        b.relationWith(a.id()).declareWar();
        state.addFaction(a);
        state.addFaction(b);
        DiplomacyEngine engine = new DiplomacyEngine();
        DeterministicRng rng = new DeterministicRng(9);
        for (int i = 0; i < 30; i++) engine.simulateDay(state, rng);
        long active = state.wars().stream().filter(WarState::active)
                .filter(w -> w.between(a.id(), b.id())).count();
        check(active == 1, "exactly one active war per pair: " + active);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
