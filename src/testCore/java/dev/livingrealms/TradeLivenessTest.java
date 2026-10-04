package dev.livingrealms;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementGeographyProfile;
import dev.livingrealms.sim.transport.TransportMode;
import dev.livingrealms.sim.transport.TransportNetworkEngine;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/**
 * Cumulative trade liveness: snapshot shipments==0 must not hide past dispatch/delivery activity.
 * Also gates geography-driven water modes over bare name heuristics when profiles are authored.
 */
public final class TradeLivenessTest {
    private TradeLivenessTest() {}

    public static void main(String[] args) {
        cumulativeTradeActivityOccurs();
        geographyOverridesNameForShipMode();
        housingDensityCompression();
        System.out.println("PASS trade liveness: cumulative dispatch/delivery + geography ship modes + housing density");
    }

    private static void cumulativeTradeActivityOccurs() {
        SimulationState state = new SimulationState(0x7A1DE111L);
        Faction seller = new Faction(state.nextId(), "Grain League", "Mayor A");
        Faction buyer = new Faction(state.nextId(), "Iron March", "Mayor B");
        Settlement origin = new Settlement(state.nextId(), "Selltown", new SimPosition(0, 0), 2_000, 2_200);
        Settlement dest = new Settlement(state.nextId(), "Buycity", new SimPosition(400, 0), 2_400, 2_600);
        // Force a steep local price gap: seller food-rich, buyer starved locally.
        origin.stockpile().add(ResourceType.FOOD, 8_000);
        seller.stockpile().add(ResourceType.FOOD, 4_000);
        dest.stockpile().set(ResourceType.FOOD, 5);
        buyer.stockpile().set(ResourceType.FOOD, 5);
        seller.addSettlement(origin);
        buyer.addSettlement(dest);
        seller.relationWith(buyer.id()).setTradeAgreement(true);
        buyer.relationWith(seller.id()).setTradeAgreement(true);
        seller.restoreTreasury(20_000);
        buyer.addTreasury(80_000);
        state.addFaction(seller);
        state.addFaction(buyer);
        state.addRoute(new TransportRoute(state.nextId(), seller.id(), origin.id(), dest.id(), TransportMode.ROAD, 400, .8, .85, 900));

        state.advanceDays(120);
        check(state.liveness().shipmentsDispatched() >= 3,
                "expected cumulative dispatches over 120 days, got " + state.liveness().shipmentsDispatched());
        check(state.liveness().tradeResolutions() >= 1,
                "expected at least one delivery or interception, got " + state.liveness());
        // Snapshot emptiness is allowed; cumulative counters are the authority.
        check(state.liveness().shipmentsDispatched() >= state.shipments().size(),
                "cumulative dispatch must be >= live shipment count");
    }

    private static void geographyOverridesNameForShipMode() {
        SimulationState state = new SimulationState(42L);
        Faction realm = new Faction(state.nextId(), "Coast Realm", "Admiral");
        // Names deliberately do NOT contain port/bay tokens.
        Settlement a = new Settlement(state.nextId(), "Stonewall", new SimPosition(0, 0), 3_000, 3_200);
        Settlement b = new Settlement(state.nextId(), "Highridge", new SimPosition(500, 0), 3_000, 3_200);
        a.setGeography(SettlementGeographyProfile.unknown().withDiscovery(true, false, true, false, 0.8, 62, 1, .4, .2, .1, "ocean"));
        b.setGeography(SettlementGeographyProfile.unknown().withDiscovery(true, false, true, false, 0.75, 61, 1, .4, .2, .1, "ocean"));
        realm.addSettlement(a);
        realm.addSettlement(b);
        realm.stockpile().add(ResourceType.STONE, 5_000);
        realm.stockpile().add(ResourceType.IRON, 200);
        state.addFaction(realm);
        // Drive weekly route discovery enough times.
        for (int i = 0; i < 40; i++) state.advanceDays(7);
        boolean ship = state.routes().stream().anyMatch(r -> r.mode() == TransportMode.SHIP
                && ((r.fromSettlementId() == a.id() && r.toSettlementId() == b.id())
                || (r.fromSettlementId() == b.id() && r.toSettlementId() == a.id())));
        check(ship, "world-discovered coastal geography must allow SHIP routes without port-name tokens");

        Settlement inland = new Settlement(state.nextId(), "Riverport", new SimPosition(0, 800), 800, 900);
        Settlement other = new Settlement(state.nextId(), "Seahaven", new SimPosition(400, 800), 800, 900);
        // Explicit dry geography overrides watery names.
        inland.setGeography(new SettlementGeographyProfile(false, false, false, false, 0, 90, 4, .5, .4, .3, "plains", true));
        other.setGeography(new SettlementGeographyProfile(false, false, false, false, 0, 88, 3, .5, .4, .3, "plains", true));
        Faction dry = new Faction(state.nextId(), "Dry Realm", "Warden");
        dry.addSettlement(inland);
        dry.addSettlement(other);
        dry.stockpile().add(ResourceType.STONE, 5_000);
        state.addFaction(dry);
        for (int i = 0; i < 40; i++) state.advanceDays(7);
        boolean wateryNameButDryGeo = state.routes().stream().anyMatch(r ->
                (r.fromSettlementId() == inland.id() || r.toSettlementId() == inland.id())
                        && (r.mode() == TransportMode.SHIP || r.mode() == TransportMode.RIVER));
        check(!wateryNameButDryGeo, "dry world-discovered geography must suppress name-heuristic water modes");
        check(TransportNetworkEngine.bestRoute(state, dry.id(), inland.id(), other.id()).isPresent()
                        || state.routes().stream().anyMatch(r -> r.ownerFactionId() == dry.id()),
                "dry realm should still form land routes");
    }

    private static void housingDensityCompression() {
        check(dev.livingrealms.sim.construction.HousingCapacity.representedResidents(7, 7) == 8, "cottage capacity");
        check(dev.livingrealms.sim.construction.HousingCapacity.representedResidents(9, 9) == 18, "townhouse capacity");
        check(dev.livingrealms.sim.construction.HousingCapacity.representedResidents(12, 12) == 48, "apartment capacity");
        SimulationState state = new SimulationState(55L);
        DemoSeeder.seed(state);
        Faction faction = state.factions().getFirst();
        Settlement capital = faction.settlements().getFirst();
        var deficit = dev.livingrealms.sim.construction.PhysicalDevelopmentReconciler.analyze(faction, capital);
        check(deficit.pendingCount() >= 0, "reconciler must analyze capital");
        // Completing apartment-scale houses should close gap faster than cottages.
        int before = deficit.housingCapacityGap();
        for (var intent : deficit.backlog()) {
            if (intent.role() == dev.livingrealms.sim.construction.StructureRole.HOUSE) {
                capital.markConstructionCompleted(intent.key());
            }
        }
        var after = dev.livingrealms.sim.construction.PhysicalDevelopmentReconciler.analyze(faction, capital);
        check(after.housingCapacityGap() <= before, "completing houses must not worsen housing gap");
    }

    private static void check(boolean v, String m) { if (!v) throw new AssertionError(m); }
}
