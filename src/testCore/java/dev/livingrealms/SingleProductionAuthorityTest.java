package dev.livingrealms;

import dev.livingrealms.sim.economy.SettlementEconomyEngine;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/**
 * H11: daily sim is the sole GRAIN authority — a physical harvest call must not double-book.
 */
public final class SingleProductionAuthorityTest {
    private SingleProductionAuthorityTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0xH11L);
        Faction f = new Faction(state.nextId(), "Farm Realm", "Farmer Queen");
        Settlement s = new Settlement(state.nextId(), "Grainfields", new SimPosition(0, 0), 200, 240);
        s.markConstructionCompleted("farm:0");
        s.markConstructionCompleted("farm:1");
        f.addSettlement(s);
        state.addFaction(f);
        s.stockpile().set(ResourceType.GRAIN, 50);
        double before = s.stockpile().get(ResourceType.GRAIN);
        new SettlementEconomyEngine().simulateDay(state);
        double afterSim = s.stockpile().get(ResourceType.GRAIN);
        // Physical harvest animation must be a no-op once the sim day has booked yield.
        double physical = SettlementEconomyEngine.physicalHarvestIfUnbooked(s, state.clock().day());
        check(physical == 0, "physical harvest after sim day must not add grain: " + physical);
        check(s.stockpile().get(ResourceType.GRAIN) == afterSim, "stockpile unchanged by physical call");
        check(afterSim >= before, "sim day should produce some grain with farms");
        System.out.println("PASS single production authority: sim books grain, physical harvest is animation-only");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
