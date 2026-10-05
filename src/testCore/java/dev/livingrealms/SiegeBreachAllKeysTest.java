package dev.livingrealms;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** E7/G5: siege breach removes all matching keys per threshold. */
public final class SiegeBreachAllKeysTest {
    private SiegeBreachAllKeysTest() {}

    public static void main(String[] args) {
        damageAllRemovesEveryPrefix();
        emptyStockpileNoRam();
        System.out.println("PASS siege breach: all wall keys + equipment needs stockpile");
    }

    private static void damageAllRemovesEveryPrefix() {
        Settlement s = new Settlement(1, "Fort", new SimPosition(0, 0), 500, 600);
        s.markConstructionCompleted("wall:0");
        s.markConstructionCompleted("wall:1");
        s.markConstructionCompleted("wall:2");
        s.markConstructionCompleted("gate:0");
        int removed = s.damageAllAuthoredStructures("wall:");
        check(removed == 3, "all three wall keys removed: " + removed);
        check(s.completedConstruction().stream().noneMatch(k -> k.startsWith("wall:")), "no wall keys remain");
        check(s.isConstructionCompleted("gate:0"), "gate untouched by wall-only damage");
    }

    private static void emptyStockpileNoRam() {
        SimulationState state = new SimulationState(0xE7E7L);
        Faction attacker = new Faction(state.nextId(), "Siege Host", "General");
        Faction defender = new Faction(state.nextId(), "Holdouts", "Castellan");
        Settlement fort = new Settlement(state.nextId(), "Keepwall", new SimPosition(500, 0), 800, 900);
        fort.markConstructionCompleted("wall:0");
        fort.markConstructionCompleted("keep:0");
        attacker.addSettlement(new Settlement(state.nextId(), "Camp", new SimPosition(0, 0), 400, 450));
        defender.addSettlement(fort);
        Army army = new Army(state.nextId(), attacker.id(), new SimPosition(480, 0), 200);
        attacker.addArmy(army);
        // Empty stockpile — no wood/tools for rams.
        for (ResourceType r : ResourceType.values()) attacker.stockpile().set(r, 0);
        attacker.relationWith(defender.id()).declareWar();
        defender.relationWith(attacker.id()).declareWar();
        state.addFaction(attacker);
        state.addFaction(defender);
        state.advanceDays(5);
        var siege = state.sieges().stream().filter(s -> s.settlementId() == fort.id()).findFirst().orElse(null);
        if (siege != null) {
            check(siege.rams() == 0, "empty stockpile must not spawn rams: " + siege.rams());
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
