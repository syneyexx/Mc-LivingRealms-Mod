package dev.livingrealms;

import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.HousingCapacity;
import dev.livingrealms.sim.construction.SettlementDistrict;
import dev.livingrealms.sim.construction.SettlementDistrictPlan;
import dev.livingrealms.sim.construction.SettlementPlanCache;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.RelationStatus;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.social.HouseholdHomeBinder;
import dev.livingrealms.sim.social.HouseholdState;
import dev.livingrealms.sim.transport.TransportMode;
import dev.livingrealms.sim.transport.TransportNetworkEngine;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.List;

/** P1 world-quality gates: districts, housing density, home binding, inter-faction corridors, plan cache. */
public final class WorldQualityPassTest {
    private WorldQualityPassTest() {}

    public static void main(String[] args) {
        districtPlanDeterministic();
        housingDensityUnderPressure();
        householdHomeBinding();
        interFactionTradeCorridors();
        planCacheBoundedAndStable();
        System.out.println("PASS world quality: districts + density + home binding + trade corridors + plan cache");
    }

    private static void districtPlanDeterministic() {
        Faction faction = new Faction(1, "Test", "Ruler");
        Settlement settlement = new Settlement(2, "Districtville", new SimPosition(0, 0), 3_500, 4_000);
        faction.addSettlement(settlement);
        SettlementDistrictPlan a = SettlementDistrictPlan.derive(faction, settlement);
        SettlementDistrictPlan b = SettlementDistrictPlan.derive(faction, settlement);
        check(!a.anchors().isEmpty(), "district anchors present");
        check(a.anchors().size() == b.anchors().size(), "deterministic district count");
        check(a.districtOf("keep:0") == SettlementDistrict.GOVERNMENT || a.byIntentKey().containsValue(SettlementDistrict.GOVERNMENT),
                "government district exists");
        check(a.byIntentKey().values().stream().anyMatch(d -> d == SettlementDistrict.RESIDENTIAL
                || d == SettlementDistrict.OLD_TOWN || d == SettlementDistrict.WEALTHY_QUARTER || d == SettlementDistrict.WORKERS_QUARTER),
                "residential district family present");
        for (ConstructionIntent intent : SettlementPlanner.plan(faction, settlement)) {
            check(a.districtOf(intent.key()) != null, "every intent maps to a district");
        }
    }

    private static void housingDensityUnderPressure() {
        Faction calmFaction = new Faction(3, "Calm", "Ruler");
        Settlement calm = new Settlement(4, "Calm Town", new SimPosition(100, 0), 900, 1_200);
        calmFaction.addSettlement(calm);
        Faction pressureFaction = new Faction(5, "Pressure", "Ruler");
        Settlement pressure = new Settlement(6, "Crowded City", new SimPosition(200, 0), 8_000, 2_000);
        pressureFaction.addSettlement(pressure);
        int calmDense = countDense(SettlementPlanner.plan(calmFaction, calm));
        int pressureDense = countDense(SettlementPlanner.plan(pressureFaction, pressure));
        check(pressureDense > calmDense, "population pressure must emit more dense housing: calm=" + calmDense + " pressure=" + pressureDense);
        check(HousingCapacity.representedResidents(13, 11) == 48, "apartment capacity");
        check(HousingCapacity.representedResidents(11, 9) == 48, "11-wide footprint is apartment-scale");
        check(HousingCapacity.representedResidents(9, 9) == 18, "townhouse capacity");
        check(HousingCapacity.representedResidents(7, 7) == 8, "cottage capacity");
    }

    private static int countDense(List<ConstructionIntent> intents) {
        int n = 0;
        for (ConstructionIntent intent : intents) {
            if (intent.role() != StructureRole.HOUSE) continue;
            if (HousingCapacity.representedResidents(intent) >= 18) n++;
        }
        return n;
    }

    private static void householdHomeBinding() {
        Faction faction = new Faction(7, "Homes", "Ruler");
        Settlement settlement = new Settlement(8, "Homeville", new SimPosition(0, 100), 400, 500);
        faction.addSettlement(settlement);
        for (ConstructionIntent intent : SettlementPlanner.plan(faction, settlement)) {
            if (intent.role() == StructureRole.HOUSE && intent.key().equals("house:0")) {
                settlement.markConstructionCompleted(intent.key());
            }
            if (intent.role() == StructureRole.HOUSE && intent.key().equals("house:1")) {
                settlement.markConstructionCompleted(intent.key());
            }
        }
        String key = HouseholdHomeBinder.assignHomeKey(faction, settlement, 99);
        check(key.startsWith("house:"), "home key is a house intent");
        check(HouseholdHomeBinder.isValidHomeKey(faction, settlement, key), "assigned home key valid");
        check(!HouseholdHomeBinder.isValidHomeKey(faction, settlement, "house:9999"), "missing house invalid");
        HouseholdState house = new HouseholdState(55, faction.id(), settlement.id(), 0);
        house.setHomeKey("house:9999");
        HouseholdHomeBinder.rebindIfInvalid(house, faction, settlement);
        check(HouseholdHomeBinder.isValidHomeKey(faction, settlement, house.homeKey()), "invalid home rebound");
        ConstructionIntent resolved = HouseholdHomeBinder.resolveHomeIntent(faction, settlement, house.homeKey(), 0);
        check(resolved != null && resolved.key().equals(house.homeKey()), "resolve returns bound house");
    }

    private static void interFactionTradeCorridors() {
        SimulationState state = new SimulationState(99);
        Faction a = new Faction(state.nextId(), "Realm A", "Ruler A");
        Faction b = new Faction(state.nextId(), "Realm B", "Ruler B");
        Settlement hubA = new Settlement(state.nextId(), "Hub A", new SimPosition(0, 0), 2_400, 2_800);
        Settlement hubB = new Settlement(state.nextId(), "Hub B", new SimPosition(900, 120), 2_200, 2_600);
        a.addSettlement(hubA); b.addSettlement(hubB);
        state.addFaction(a); state.addFaction(b);
        a.relationWith(b.id()).adjust(80);
        b.relationWith(a.id()).adjust(80);
        a.relationWith(b.id()).setTradeAgreement(true);
        b.relationWith(a.id()).setTradeAgreement(true);
        check(a.relationWith(b.id()).status() != RelationStatus.WAR, "peaceful trade partners");
        a.stockpile().add(dev.livingrealms.sim.faction.ResourceType.STONE, 50_000);
        a.stockpile().add(dev.livingrealms.sim.faction.ResourceType.WOOD, 50_000);
        b.stockpile().add(dev.livingrealms.sim.faction.ResourceType.STONE, 50_000);
        b.stockpile().add(dev.livingrealms.sim.faction.ResourceType.WOOD, 50_000);
        a.addTreasury(5_000); b.addTreasury(5_000);
        // Land on a weekly discovery day, then run transport discovery only.
        long skip = (7 - (state.clock().day() % 7)) % 7;
        if (skip > 0) state.clock().advance(skip * 24_000L);
        check(state.clock().day() % 7 == 0, "discovery day");
        new TransportNetworkEngine().simulateDay(state);
        boolean corridor = state.routes().stream().anyMatch(r -> crossFaction(r, a, b));
        check(corridor, "trade agreement must create an inter-faction corridor between hubs");
        check(TransportNetworkEngine.bestRoute(state, a.id(), hubA.id(), hubB.id()).isPresent()
                        || TransportNetworkEngine.bestRoute(state, b.id(), hubA.id(), hubB.id()).isPresent(),
                "bestRoute can see cross-faction corridor ends");
        check(state.routes().stream().anyMatch(r -> ends(r, hubA.id(), hubB.id())
                        && (r.mode() == TransportMode.ROAD || r.mode() == TransportMode.CARAVAN || r.mode() == TransportMode.SHIP)),
                "corridor uses a transport mode");
    }

    private static boolean crossFaction(TransportRoute route, Faction a, Faction b) {
        boolean fromA = a.settlements().stream().anyMatch(s -> s.id() == route.fromSettlementId());
        boolean toA = a.settlements().stream().anyMatch(s -> s.id() == route.toSettlementId());
        boolean fromB = b.settlements().stream().anyMatch(s -> s.id() == route.fromSettlementId());
        boolean toB = b.settlements().stream().anyMatch(s -> s.id() == route.toSettlementId());
        return (fromA && toB) || (fromB && toA);
    }

    private static boolean ends(TransportRoute r, long a, long b) {
        return (r.fromSettlementId() == a && r.toSettlementId() == b) || (r.fromSettlementId() == b && r.toSettlementId() == a);
    }

    private static void planCacheBoundedAndStable() {
        SettlementPlanCache.clear();
        Faction faction = new Faction(11, "Cache", "Ruler");
        Settlement settlement = new Settlement(12, "Cacheton", new SimPosition(50, 50), 1_200, 1_400);
        faction.addSettlement(settlement);
        List<ConstructionIntent> first = SettlementPlanCache.plan(faction, settlement);
        List<ConstructionIntent> second = SettlementPlanCache.plan(faction, settlement);
        check(first == second, "cache returns identical frozen list instance while inputs stable");
        check(SettlementPlanCache.size() >= 1, "cache populated");
        settlement.addPopulation(200);
        List<ConstructionIntent> third = SettlementPlanCache.plan(faction, settlement);
        // Population bucket may or may not change; either way list content must remain a valid plan.
        check(!third.isEmpty(), "cached/planned intents remain available");
        SettlementPlanCache.clear();
        check(SettlementPlanCache.size() == 0, "cache cleared");
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
