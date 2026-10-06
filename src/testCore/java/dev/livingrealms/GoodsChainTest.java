package dev.livingrealms;

import dev.livingrealms.sim.economy.SettlementEconomyEngine;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** D2–D4/D6/D8: real goods chain without FOOD multiplication. */
public final class GoodsChainTest {
    private GoodsChainTest() {}

    public static void main(String[] args) {
        check(SimulationStateCodec.SCHEMA_VERSION==21, "schema 21 goods");
        subsistenceCannotFillStores();
        millBakeryConserveFoodValue();
        noWorkshopNoFlour();
        emptyRealmTreasuryFalls();
        foodSecurityThreshold();
        legacyFoodMigrationPreservesSum();
        System.out.println("PASS goods chain: schema" + SimulationStateCodec.SCHEMA_VERSION
                + " subsistence/mill/bakery/treasury/foodSecurity/migration");
    }

    private static void subsistenceCannotFillStores() {
        SimulationState state = bare("Barefields", 120);
        Settlement s = state.factions().getFirst().settlements().getFirst();
        s.stockpile().set(ResourceType.GRAIN, 0);
        s.stockpile().set(ResourceType.BREAD, 0);
        s.stockpile().set(ResourceType.FOOD, 0);
        double before = s.edibleStock();
        state.advanceDays(10);
        // Without farms, stores must not climb above a few days of consumption.
        check(s.edibleStock() < s.population() * 0.20 * 5, "subsistence overfilled: " + s.edibleStock());
        check(s.countProductionPrefix("farm:") == 0, "subsistence must not invent farm keys");
        check(before >= 0, "baseline");
    }

    private static void millBakeryConserveFoodValue() {
        SimulationState state = bare("Milltown", 200);
        Faction f = state.factions().getFirst();
        Settlement s = f.settlements().getFirst();
        s.markConstructionCompleted("mill:0");
        s.markConstructionCompleted("bakery:0");
        s.stockpile().set(ResourceType.GRAIN, 100);
        s.stockpile().set(ResourceType.FLOUR, 0);
        s.stockpile().set(ResourceType.BREAD, 0);
        double valueBefore = 100 * ResourceType.foodValue(ResourceType.GRAIN);
        new SettlementEconomyEngine().simulateDay(state);
        double valueAfter = s.stockpile().get(ResourceType.GRAIN) * ResourceType.foodValue(ResourceType.GRAIN)
                + s.stockpile().get(ResourceType.FLOUR) * ResourceType.foodValue(ResourceType.FLOUR)
                + s.stockpile().get(ResourceType.BREAD) * ResourceType.foodValue(ResourceType.BREAD);
        check(valueAfter <= valueBefore + 1e-6, "conversion must not increase food value: " + valueBefore + " -> " + valueAfter);
        check(s.stockpile().get(ResourceType.FLOUR) > 0 || s.stockpile().get(ResourceType.BREAD) > 0, "mill/bakery should produce");
    }

    private static void noWorkshopNoFlour() {
        SimulationState state = bare("NoMill", 250);
        Settlement s = state.factions().getFirst().settlements().getFirst();
        s.stockpile().set(ResourceType.GRAIN, 80);
        s.stockpile().set(ResourceType.FLOUR, 0);
        s.stockpile().set(ResourceType.BREAD, 0);
        s.stockpile().set(ResourceType.ALE, 0);
        state.advanceDays(30);
        check(s.stockpile().get(ResourceType.FLOUR) == 0, "no mill => no flour");
        check(s.stockpile().get(ResourceType.BREAD) <= 80 * 0.45 + 1, "no bakery => no bread beyond migration seed");
        check(s.stockpile().get(ResourceType.ALE) == 0, "no brewery => no ale");
    }

    private static void emptyRealmTreasuryFalls() {
        SimulationState state = bare("Brokehold", 180);
        Faction f = state.factions().getFirst();
        Settlement s = f.settlements().getFirst();
        // Strip productive keys and stores.
        s.resetConstructionCompletion();
        s.stockpile().set(ResourceType.GRAIN, 5);
        s.stockpile().set(ResourceType.BREAD, 5);
        s.stockpile().set(ResourceType.MEAT, 0);
        f.stockpile().set(ResourceType.GRAIN, 0);
        f.stockpile().set(ResourceType.BREAD, 0);
        f.addArmy(new Army(state.nextId(), f.id(), s.position(), 80));
        double treasury0 = f.treasury();
        double grain0 = s.stockpile().get(ResourceType.GRAIN) + f.stockpile().get(ResourceType.GRAIN);
        state.advanceDays(60);
        check(f.treasury() < treasury0, "treasury should fall without farms: " + treasury0 + " -> " + f.treasury());
        double grain1 = s.stockpile().get(ResourceType.GRAIN) + f.stockpile().get(ResourceType.GRAIN);
        check(grain1 <= grain0 + 30, "grain must not fountain without farms: " + grain0 + " -> " + grain1);
    }

    private static void foodSecurityThreshold() {
        SimulationState state = bare("Hungry", 300);
        Settlement s = state.factions().getFirst().settlements().getFirst();
        s.resetConstructionCompletion();
        for (ResourceType r : new ResourceType[]{ResourceType.FOOD, ResourceType.GRAIN, ResourceType.BREAD, ResourceType.MEAT, ResourceType.ALE, ResourceType.FLOUR})
            s.stockpile().set(r, 0);
        s.setFoodSecurity(0.7);
        double unrest0 = s.unrest();
        state.advanceDays(20);
        check(s.foodSecurity() < 0.55, "foodSecurity must drop below 0.55 without stores: " + s.foodSecurity());
        check(s.unrest() > unrest0 + 0.01, "unrest must rise when foodSecurity < 0.55");
    }

    private static void legacyFoodMigrationPreservesSum() {
        Stockpile stock = new Stockpile();
        stock.set(ResourceType.FOOD, 100);
        ResourceType.migrateLegacyFood(stock);
        double units = stock.get(ResourceType.GRAIN) + stock.get(ResourceType.BREAD);
        check(Math.abs(units - 100) < 1e-9, "FOOD migration must preserve unit sum");
        check(stock.get(ResourceType.FOOD) == 0, "FOOD cleared after migration");
    }

    private static SimulationState bare(String name, int pop) {
        SimulationState state = new SimulationState(name.hashCode() & 0xffffffffL);
        Faction f = new Faction(state.nextId(), name + " Realm", "Lord " + name);
        Settlement s = new Settlement(state.nextId(), name, new SimPosition(10, 10), pop, pop + 40);
        f.addSettlement(s);
        f.restoreTreasury(500);
        state.addFaction(f);
        return state;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
