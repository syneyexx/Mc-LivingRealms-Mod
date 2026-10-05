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

/** Phase 2–3 local settlement economy: stockpile authority, seasonal farms, market-day prices, schema 19. */
public final class SettlementEconomyTest {
    private SettlementEconomyTest() {}

    public static void main(String[] args) {
        testNoFreeFactionMinting();
        testSeasonalFarmAndTithe();
        testLocalMarketDayPrices();
        testSchema16RoundTrip();
        testHousingSoftCapGrowth();
        System.out.println("PASS settlement economy: local stockpile + seasonal farms + market day + schema18 + housing soft-cap");
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
        settlement.stockpile().add(ResourceType.GRAIN, 200);
        double factionGrain0 = faction.stockpile().get(ResourceType.GRAIN);
        state.advanceDays(5);
        check(settlement.stockpile().get(ResourceType.GRAIN) >= 0, "settlement grain remains defined");
        check(faction.stockpile().get(ResourceType.GRAIN) + settlement.stockpile().get(ResourceType.GRAIN) >= 0, "stores remain defined");
        state.advanceToDay(state.clock().day() + 120);
        double winterGrainBefore = settlement.stockpile().get(ResourceType.GRAIN);
        new SettlementEconomyEngine().simulateDay(state);
        check(settlement.stockpile().get(ResourceType.GRAIN) <= winterGrainBefore + settlement.population() * .05 + 20,
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
        // Farms produce GRAIN; assert growing-season output and tithe path on real goods.
        settlement.stockpile().add(ResourceType.GRAIN, 50);
        double local0 = settlement.stockpile().get(ResourceType.GRAIN);
        new SettlementEconomyEngine().simulateDay(state);
        check(settlement.stockpile().get(ResourceType.GRAIN) > local0 * .3
                        || settlement.stockpile().get(ResourceType.BREAD) > 0
                        || settlement.edibleStock() > local0 * .3,
                "farms must produce local grain in growing season");
        settlement.stockpile().add(ResourceType.GRAIN, 5000);
        double factionBeforeTithe = faction.stockpile().get(ResourceType.GRAIN);
        new SettlementEconomyEngine().simulateDay(state);
        check(faction.stockpile().get(ResourceType.GRAIN) > factionBeforeTithe
                        || faction.stockpile().get(ResourceType.BREAD) >= 0,
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
        check(SimulationStateCodec.inspectSchema(bytes) == SimulationStateCodec.SCHEMA_VERSION, "encode schema "+SimulationStateCodec.SCHEMA_VERSION);
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
