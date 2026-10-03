package dev.livingrealms;

import dev.livingrealms.sim.aviation.*;
import dev.livingrealms.sim.civilian.*;
import dev.livingrealms.sim.ecology.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.logistics.projection.*;
import dev.livingrealms.sim.materialization.*;
import dev.livingrealms.sim.military.*;
import dev.livingrealms.sim.naval.*;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;

/**
 * High-cardinality projection stress gate. The canonical simulation may contain thousands of
 * aggregate objects, but every physical projection must remain bounded and uniquely addressable.
 */
public final class ProjectionStressTest {
    private ProjectionStressTest() {}

    public static void main(String[] args) {
        stressWildlife();
        stressCaravans();
        stressCitizens();
        stressMilitary();
        stressAircraft();
        stressNaval();
        System.out.println("PASS projection stress: wildlife + caravans + citizens + military + aircraft + naval budgets/identity/reconciliation");
    }

    private static void stressWildlife() {
        Map<String, SpeciesDefinition> species = SpeciesCatalog.starter();
        List<PopulationGroup> groups = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            groups.add(new PopulationGroup(i + 1L, "rabbit", "temperate_forest",
                    new SimPosition((i % 30) * 3.0, (i / 30) * 3.0), 500));
        }
        List<SimPosition> players = List.of(new SimPosition(0, 0), new SimPosition(40, 40), new SimPosition(80, 80));
        MaterializationConfig config = new MaterializationConfig(500, 2500, 32, 180);
        List<MaterializationRequest> requests = new MaterializationPlanner(config).plan(groups, species, players);
        int physical = requests.stream().mapToInt(MaterializationRequest::desiredPhysicalCount).sum();
        check(physical == 540, "wildlife must spend, but never exceed, 3-player global budget");
        check(uniqueLong(requests.stream().map(MaterializationRequest::populationGroupId).toList()), "wildlife requests must have unique group IDs");

        MaterializationRequest first = requests.stream().filter(r -> r.desiredPhysicalCount() >= 2).findFirst().orElseThrow();
        List<PhysicalProjection> actual = List.of(
                new PhysicalProjection("wild-a", first.populationGroupId(), first.speciesId(), 0),
                new PhysicalProjection("wild-b", first.populationGroupId(), first.speciesId(), 0),
                new PhysicalProjection("wild-over", first.populationGroupId(), first.speciesId(), first.desiredPhysicalCount() + 5),
                new PhysicalProjection("wild-orphan", 999_999L, "rabbit", 0));
        ProjectionPlan delta = MaterializationReconciler.reconcile(requests, actual);
        check(delta.despawns().stream().anyMatch(d -> d.reason() == ProjectionRemovalReason.DUPLICATE_SLOT), "wildlife duplicate slot must be removed");
        check(delta.despawns().stream().anyMatch(d -> d.reason() == ProjectionRemovalReason.OVER_BUDGET), "wildlife over-budget slot must be removed");
        check(delta.despawns().stream().anyMatch(d -> d.reason() == ProjectionRemovalReason.ORPHANED_GROUP), "wildlife orphan must be removed");
        check(uniqueStrings(delta.spawns().stream().map(s -> s.populationGroupId() + ":" + s.slot()).toList()), "wildlife spawn slots must be unique");
    }

    private static void stressCaravans() {
        List<TradeShipment> shipments = new ArrayList<>();
        Set<Long> active = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            long id = i + 1L;
            TradeShipment shipment = new TradeShipment(id, 1, 2, ResourceType.FOOD, 10, 25,
                    new SimPosition(i % 20, i / 20.0), new SimPosition(800, 0));
            shipments.add(shipment);
            active.add(id);
        }
        CaravanMaterializationPlanner planner = new CaravanMaterializationPlanner(new CaravanProjectionConfig(500, 24));
        List<CaravanProjectionRequest> desired = planner.plan(shipments, List.of(new SimPosition(0, 0)));
        check(desired.size() == 24, "caravan planner must enforce global cap");
        check(uniqueLong(desired.stream().map(CaravanProjectionRequest::shipmentId).toList()), "caravan desired IDs must be unique");

        long firstId = desired.getFirst().shipmentId();
        List<CaravanProjectionSnapshot> actual = List.of(
                new CaravanProjectionSnapshot("caravan-a", firstId),
                new CaravanProjectionSnapshot("caravan-b", firstId),
                new CaravanProjectionSnapshot("caravan-orphan", 999_999L));
        CaravanProjectionPlan delta = planner.reconcile(desired, actual, active);
        check(delta.spawns().size() == desired.size() - 1, "existing caravan must suppress exactly one spawn");
        check(delta.despawns().stream().anyMatch(d -> d.reason() == CaravanProjectionRemovalReason.DUPLICATE), "duplicate caravan must despawn");
        check(delta.despawns().stream().anyMatch(d -> d.shipmentId() == 999_999L), "orphan caravan must despawn");
        check(uniqueLong(delta.spawns().stream().map(CaravanProjectionSpawn::shipmentId).toList()), "caravan spawn IDs must be unique");
    }

    private static void stressCitizens() {
        List<Faction> factions = new ArrayList<>();
        long settlementId = 10_000;
        for (int f = 0; f < 30; f++) {
            Faction faction = new Faction(1_000L + f, "Citizen Realm " + f, "Ruler " + f);
            for (int s = 0; s < 12; s++) {
                faction.addSettlement(new Settlement(settlementId++, "City " + f + "-" + s,
                        new SimPosition((f % 6) * 30.0 + s, (f / 6) * 30.0), 4_000, 4_500));
            }
            factions.add(faction);
        }
        List<CitizenProjection> projections = CitizenMaterializationPlanner.plan(factions, List.of(new SimPosition(70, 70)), 500, 128);
        check(projections.size() == 128, "citizen planner must enforce global cap");
        check(uniqueStrings(projections.stream().map(CitizenProjection::projectionKey).toList()), "citizen projection keys must be unique");
    }

    private static void stressMilitary() {
        List<Faction> factions = new ArrayList<>();
        long armyId = 50_000;
        for (int f = 0; f < 20; f++) {
            Faction faction = new Faction(20_000L + f, "Army Realm " + f, "Marshal " + f);
            for (int a = 0; a < 20; a++) {
                Army army = new Army(armyId++, faction.id(), new SimPosition((a % 10) * 6.0, f * 4.0), 5_000);
                army.addArmor(80);
                army.addArtillery(40);
                faction.addArmy(army);
            }
            factions.add(faction);
        }
        List<MilitaryProjection> projections = MilitaryMaterializationPlanner.plan(factions, List.of(new SimPosition(0, 0)), 500, 96);
        check(projections.size() == 96, "military planner must enforce global cap");
        check(uniqueStrings(projections.stream().map(MilitaryProjection::projectionKey).toList()), "military projection keys must be unique");
    }

    private static void stressAircraft() {
        List<AirWing> wings = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            wings.add(new AirWing(80_000L + i, 1 + (i % 8), AircraftModel.LIGHT_FIGHTER, 12,
                    new SimPosition((i % 20) * 5.0, (i / 20) * 5.0)));
        }
        List<AircraftProjection> projections = AircraftMaterializationPlanner.plan(wings, List.of(new SimPosition(0, 0)), 500, 64);
        check(projections.size() == 64, "aircraft planner must enforce global cap");
        check(uniqueStrings(projections.stream().map(AircraftProjection::projectionKey).toList()), "aircraft projection keys must be unique");
    }

    private static void stressNaval() {
        List<Fleet> fleets = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Fleet fleet = new Fleet(90_000L + i, 1 + (i % 8), 100 + i,
                    new SimPosition((i % 20) * 5.0, (i / 20) * 5.0), ShipClass.DESTROYER, 10);
            fleets.add(fleet);
        }
        List<FleetProjection> projections = NavalMaterializationPlanner.plan(fleets, List.of(new SimPosition(0, 0)), 500, 48);
        check(projections.size() == 48, "naval planner must enforce global cap");
        check(uniqueStrings(projections.stream().map(FleetProjection::projectionKey).toList()), "naval projection keys must be unique");
    }

    private static boolean uniqueLong(List<Long> values) {
        return new HashSet<>(values).size() == values.size();
    }

    private static boolean uniqueStrings(List<String> values) {
        return new HashSet<>(values).size() == values.size();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
