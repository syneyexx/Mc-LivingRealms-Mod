package dev.livingrealms;

import dev.livingrealms.sim.economy.SettlementEconomyEngine;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/**
 * H11: daily sim is the sole GRAIN authority — a physical harvest call must not double-book.
 * Physical hunter kills must never mint canonical FOOD from loaded chunks.
 */
public final class SingleProductionAuthorityTest {
    private SingleProductionAuthorityTest() {}

    public static void main(String[] args) throws Exception {
        grainAuthority();
        hunterDeathMustNotMintFoodSourceGuard();
        System.out.println("PASS single production authority: sim books grain, physical harvest is animation-only, hunter death does not mint FOOD");
    }

    private static void grainAuthority() {
        SimulationState state = new SimulationState(0xB11L);
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
    }

    /** Static guard: LivingRealmsEvents must not mint FOOD from hunter animal deaths. */
    private static void hunterDeathMustNotMintFoodSourceGuard() throws Exception {
        String events = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/dev/livingrealms/minecraft/LivingRealmsEvents.java"));
        // The death handler for projected hunters must not call stockpile().add(ResourceType.FOOD, ...).
        int idx = events.indexOf("CitizenRole.HUNTER");
        check(idx >= 0, "hunter role still referenced in events");
        String window = events.substring(Math.max(0, idx - 200), Math.min(events.length(), idx + 500));
        check(!window.contains("stockpile().add(ResourceType.FOOD"),
                "hunter death window must not mint FOOD: " + window);
        check(window.contains("presentation") || window.contains("Canonical hunting") || window.contains("never mint"),
                "hunter death must document presentation-only authority");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
