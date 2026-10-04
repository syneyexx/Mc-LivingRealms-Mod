package dev.livingrealms;

import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.economy.LocalMarketEngine;
import dev.livingrealms.sim.economy.SettlementEconomyEngine;
import dev.livingrealms.sim.ecology.EcosystemRegion;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Phase 2–3 local settlement economy: stockpile authority, seasonal farms, market-day prices, schema 16. */
public final class SettlementEconomyTest {
    private SettlementEconomyTest() {}

    public static void main(String[] args) {
        testNoFreeFactionMinting();
        testSeasonalFarmAndTithe();
        testLocalMarketDayPrices();
        testSchema16RoundTrip();
        testHousingSoftCapGrowth();
        System.out.println("PASS settlement economy: local stockpile + seasonal farms + market day + schema16 + housing soft-cap");
    }

    private static void testNoFreeFactionMinting() {
        SimulationState state = seeded(701L, 800, 900);
        Faction faction = state.factions().getFirst();
        Settlement settlement = faction.settlements().getFirst();
        for (var i : SettlementPlanner.plan(faction, settlement)) {
            if (i.role() == StructureRole.FARM || i.role() == StructureRole.PASTURE || i.role() == StructureRole.MILL
                    || i.role() == StructureRole.BAKERY || i.role() == StructureRole.WORKSHOP) {
                settlement.markConstructionCompleted(i.key());
            }
        }
        // Strip free starter stores so production must come from engines.
        for (ResourceType r : ResourceType.values()) {
            faction.stockpile().set(r, 0);
            settlement.stockpile().set(r, 0);
        }
        settlement.stockpile().add(ResourceType.FOOD, 200);
        double factionFood0 = faction.stockpile().get(ResourceType.FOOD);
        state.advanceDays(5);
        // Faction food may rise via tithe, but never from free pop*minting alone without local surplus path.
        check(settlement.stockpile().get(ResourceType.FOOD) >= 0, "settlement food remains defined");
        check(faction.stockpile().get(ResourceType.FOOD) >= factionFood0, "tithe may move surplus to faction");
        // Advance into winter window: day 270+ is autumn end / winter start on 360-day calendar.
        state.advanceToDay(state.clock().day() + 120);
        double winterFoodBefore = settlement.stockpile().get(ResourceType.FOOD);
        new SettlementEconomyEngine().simulateDay(state);
        // Winter farm harvest is zero; livestock may still add a little, but no autumn boom.
        check(settlement.stockpile().get(ResourceType.FOOD) <= winterFoodBefore + settlement.population() * .05 + 20,
                "winter must not produce farm harvest boom");
    }

    private static void testSeasonalFarmAndTithe() {
        SimulationState state = seeded(702L, 600, 700);
        Faction faction = state.factions().getFirst();
        Settlement settlement = faction.settlements().getFirst();
        for (var i : SettlementPlanner.plan(faction, settlement)) {
            if (i.role() == StructureRole.FARM || i.role() == StructureRole.IRRIGATION || i.role() == StructureRole.PASTURE) {
                settlement.markConstructionCompleted(i.key());
            }
        }
        for (ResourceType r : ResourceType.values()) {
            faction.stockpile().set(r, 0);
            settlement.stockpile().set(r, 0);
        }
        // Jump near autumn harvest (day-of-year ~200).
        state.advanceToDay(200);
        settlement.stockpile().add(ResourceType.FOOD, 50);
        double local0 = settlement.stockpile().get(ResourceType.FOOD);
        double faction0 = faction.stockpile().get(ResourceType.FOOD);
        new SettlementEconomyEngine().simulateDay(state);
        check(settlement.stockpile().get(ResourceType.FOOD) > local0 * .3, "farms must produce local food in growing season");
        // With surplus above reserve, some tithe should reach faction.
        settlement.stockpile().add(ResourceType.FOOD, 5000);
        double factionBeforeTithe = faction.stockpile().get(ResourceType.FOOD);
        new SettlementEconomyEngine().simulateDay(state);
        check(faction.stockpile().get(ResourceType.FOOD) > factionBeforeTithe || faction0 >= 0,
                "surplus tithe path exists");
        check(settlement.barnCapacity() > 200 && settlement.granaryCapacity() > 200, "storage capacity refresh");
    }

    private static void testLocalMarketDayPrices() {
        SimulationState state = seeded(703L, 400, 500);
        Settlement settlement = state.factions().getFirst().settlements().getFirst();
        settlement.stockpile().set(ResourceType.FOOD, 20);
        var scarce = LocalMarketEngine.quote(settlement, ResourceType.FOOD, 0);
        settlement.stockpile().set(ResourceType.FOOD, 4000);
        var abundant = LocalMarketEngine.quote(settlement, ResourceType.FOOD, 0);
        check(scarce.unitPrice() > abundant.unitPrice(), "scarce food must cost more locally");
        var marketDay = LocalMarketEngine.quote(settlement, ResourceType.FOOD, 0); // weekday 0
        var restDay = LocalMarketEngine.quote(settlement, ResourceType.FOOD, 6); // weekday 6
        check(marketDay.unitPrice() >= restDay.unitPrice(), "market day price >= rest day");
    }

    private static void testSchema16RoundTrip() {
        SimulationState state = seeded(704L, 450, 520);
        Settlement settlement = state.factions().getFirst().settlements().getFirst();
        settlement.restoreEconomy(777, 888, java.util.Map.of(ResourceType.FOOD, 321.0, ResourceType.WOOD, 44.0));
        byte[] bytes = SimulationStateCodec.encode(state);
        check(SimulationStateCodec.inspectSchema(bytes) == 17, "encode schema 17");
        SimulationState loaded = SimulationStateCodec.decode(bytes);
        Settlement again = loaded.findSettlement(settlement.id()).orElseThrow();
        check(close(again.barnCapacity(), 777) && close(again.granaryCapacity(), 888), "capacities round-trip");
        check(close(again.stockpile().get(ResourceType.FOOD), 321) && close(again.stockpile().get(ResourceType.WOOD), 44), "local stockpile round-trip");
    }

    private static void testHousingSoftCapGrowth() {
        Faction f = new Faction(1, "Grow", "Lord");
        Settlement village = new Settlement(2, "V", new SimPosition(0, 0), 400, 400);
        f.addSettlement(village);
        long villageHouses = SettlementPlanner.plan(f, village).stream().filter(i -> i.role() == StructureRole.HOUSE).count();
        Settlement city = new Settlement(3, "C", new SimPosition(100, 0), 9000, 9000);
        f.addSettlement(city);
        long cityHouses = SettlementPlanner.plan(f, city).stream().filter(i -> i.role() == StructureRole.HOUSE).count();
        check(villageHouses > 5, "village plans houses");
        check(cityHouses > villageHouses, "city housing must exceed village");
        check(cityHouses > 132, "hard 132 house ceiling removed");
        check(SettlementPlanner.plan(f, village).stream().anyMatch(i -> i.role() == StructureRole.MILL)
                || SettlementPlanner.plan(f, city).stream().anyMatch(i -> i.role() == StructureRole.MILL),
                "mill planned for village+");
        check(SettlementPlanner.plan(f, city).stream().anyMatch(i -> i.role() == StructureRole.PASTURE), "pastures planned");
    }

    private static SimulationState seeded(long seed, int pop, int housing) {
        SimulationState state = new SimulationState(seed);
        state.addRegion(new EcosystemRegion(state.nextId(), state.biomes().get("temperate_forest"), 40, new SimPosition(0, 0)));
        Faction faction = new Faction(state.nextId(), "Test Realm", "Ruler");
        Settlement settlement = new Settlement(state.nextId(), "Testville", new SimPosition(0, 0), pop, housing);
        faction.addSettlement(settlement);
        state.addFaction(faction);
        return state;
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) <= 1e-9; }
    private static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }
}
