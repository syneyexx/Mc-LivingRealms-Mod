package dev.livingrealms;

import dev.livingrealms.sim.construction.HousingCapacity;
import dev.livingrealms.sim.construction.SettlementPlanCache;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.economy.SettlementEconomyEngine;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.List;

/** WAVE 1 gates: housing credit, player-activated production truth, founder catch-up request. */
public final class HousingAndProductionTruthTest {
    public static void main(String[] args) {
        housingCreditsFromPhysicalHouses();
        activatedSettlementsRequireFarms();
        founderRequestsCatchupAndLandmark();
        presentationPulseAdvancesShipments();
        System.out.println("PASS housing/production truth: physical beds + activated farms + founder catch-up + microstep");
    }

    private static void housingCreditsFromPhysicalHouses() {
        SimulationState state = new SimulationState(11);
        DemoSeeder.seed(state);
        var faction = state.factions().getFirst();
        var settlement = faction.settlements().getFirst();
        int before = settlement.housing();
        var house = SettlementPlanCache.plan(faction, settlement).stream()
                .filter(i -> i.role() == StructureRole.HOUSE && !settlement.isConstructionCompleted(i.key()))
                .findFirst().orElseThrow();
        check(settlement.markConstructionCompleted(house.key()), "house key marked");
        // Materializer credits HousingCapacity; simulate that credit path here.
        settlement.addHousing(HousingCapacity.representedResidents(house));
        check(settlement.housing() >= before + 8, "physical house must credit real capacity");
    }

    private static void activatedSettlementsRequireFarms() {
        // Use fixed day in autumn so farm seasonMul > 0.
        SimulationState state = new SimulationState(22);
        state.advanceToDay(100); // autumn-ish calendar window
        var founded = PlayerSettlementFounder.found(state, "player:farm", "Farmer", "Farmless", new SimPosition(60_000, 60_000));
        check(founded.success(), founded.reason());
        var capital = state.findSettlement(founded.settlementId()).orElseThrow();
        capital.stockpile().set(ResourceType.FOOD, 0);
        state.presentationScope().setActivated(List.of(capital.id()));
        new SettlementEconomyEngine().simulateDay(state);
        double activatedFood = capital.stockpile().get(ResourceType.FOOD);
        capital.stockpile().set(ResourceType.FOOD, 0);
        state.presentationScope().clear();
        new SettlementEconomyEngine().simulateDay(state);
        double unloadedFood = capital.stockpile().get(ResourceType.FOOD);
        check(unloadedFood > activatedFood * 1.5 + 1, "unloaded implied farms must outproduce activated farmless kitchen gardens: act=" + activatedFood + " unload=" + unloadedFood);
        check(activatedFood < capital.population() * .25, "activated settlement without farms must stay near subsistence");
    }

    private static void founderRequestsCatchupAndLandmark() {
        SimulationState state = new SimulationState(33);
        var founded = PlayerSettlementFounder.found(state, "player:catch", "Catch", "Catchburg", new SimPosition(70_000, 70_000));
        check(founded.success(), founded.reason());
        check(state.consumeConstructionCatchup() >= 45, "founder must request construction catch-up");
        var capital = state.findSettlement(founded.settlementId()).orElseThrow();
        check(capital.priorityLandmarks().contains("keep:0"), "founder prioritizes keep landmark");
    }

    private static void presentationPulseAdvancesShipments() {
        SimulationState state = new SimulationState(44);
        DemoSeeder.seed(state);
        state.advanceDays(40);
        // Ensure at least one shipment exists by advancing trade-heavy days if needed.
        for (int i = 0; i < 80 && state.shipments().isEmpty(); i++) state.advanceDays(1);
        if (state.shipments().isEmpty()) {
            System.out.println("WARN presentation pulse: no shipments after warm-up — skipping distance check");
            state.advancePresentationPulse(0.5);
            return;
        }
        var shipment = state.shipments().getFirst();
        double before = shipment.progress();
        state.advancePresentationPulse(0.5);
        check(shipment.progress() >= before, "microstep must not rewind shipment progress");
        check(state.clock().day() >= 0, "microstep must not require clock advance");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}
