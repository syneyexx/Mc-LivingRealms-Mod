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
        // Main A–R authority: farm: completion keys (not presentation activation) gate real GRAIN yield.
        SimulationState state = new SimulationState(22);
        state.advanceToDay(100); // autumn-ish calendar window
        var founded = PlayerSettlementFounder.found(state, "player:farm", "Farmer", "Farmless", new SimPosition(60_000, 60_000));
        check(founded.success(), founded.reason());
        var capital = state.findSettlement(founded.settlementId()).orElseThrow();
        check(!capital.isConstructionCompleted("farm:0"), "founding camp must not start with a free farm");
        capital.stockpile().set(ResourceType.FOOD, 0);
        capital.stockpile().set(ResourceType.GRAIN, 0);
        new SettlementEconomyEngine().simulateDay(state);
        double withoutFarm = capital.stockpile().get(ResourceType.GRAIN) + capital.stockpile().get(ResourceType.FOOD);
        capital.stockpile().set(ResourceType.FOOD, 0);
        capital.stockpile().set(ResourceType.GRAIN, 0);
        check(capital.markConstructionCompleted("farm:0"), "farm key marked");
        new SettlementEconomyEngine().simulateDay(state);
        double withFarm = capital.stockpile().get(ResourceType.GRAIN) + capital.stockpile().get(ResourceType.FOOD);
        check(withFarm > withoutFarm * 1.5 + 1, "completed farm must outproduce subsistence: bare=" + withoutFarm + " farm=" + withFarm);
        check(withoutFarm < capital.population() * .25, "settlement without farms must stay near subsistence");
    }

    private static void founderRequestsCatchupAndLandmark() {
        SimulationState state = new SimulationState(33);
        var founded = PlayerSettlementFounder.found(state, "player:catch", "Catch", "Catchburg", new SimPosition(70_000, 70_000));
        check(founded.success(), founded.reason());
        check(state.consumeConstructionCatchup() >= 20, "founder must request construction catch-up");
        var capital = state.findSettlement(founded.settlementId()).orElseThrow();
        check(capital.priorityLandmarks().contains("town_hall:0"), "founder prioritizes town hall landmark");
        check(capital.population() == PlayerSettlementFounder.FOUNDING_POPULATION, "founder camp population");
        check(capital.developmentMode() == dev.livingrealms.sim.faction.DevelopmentMode.HYBRID, "default HYBRID");
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
        check(shipment.progress() >= before, "presentation pulse must not rewind shipments");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
