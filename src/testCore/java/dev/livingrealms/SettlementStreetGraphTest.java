package dev.livingrealms;

import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.SettlementParcelPlanner;
import dev.livingrealms.sim.construction.SettlementMorphology;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.SettlementStreetGraph;
import dev.livingrealms.sim.construction.StructureBlueprintFactory;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.List;

/** Graph-first urbanism: morphology topology, polyline roads, parcels, frontage-facing houses. */
public final class SettlementStreetGraphTest {
    private SettlementStreetGraphTest() {}

    public static void main(String[] args) {
        morphologyOwnsTopology();
        connectedCoreAndParcels();
        housesFaceFrontage();
        housesCarryParcelBindings();
        System.out.println("PASS settlement street graph: morphology-owned connected polylines + parcels + frontage-facing houses");
    }

    private static void morphologyOwnsTopology() {
        Faction faction = new Faction(6, "Morph Realm", "Planner");
        Settlement city = new Settlement(60, "Morph City", new SimPosition(0, 0), 4200, 5000);
        faction.addSettlement(city);
        java.util.Set<String> signatures = new java.util.HashSet<>();
        for (SettlementMorphology morphology : List.of(
                SettlementMorphology.ORGANIC_MEDIEVAL,
                SettlementMorphology.MARKET_CROSS,
                SettlementMorphology.RADIAL_CAPITAL,
                SettlementMorphology.HILL_TOWN,
                SettlementMorphology.RIVER_TOWN,
                SettlementMorphology.COASTAL_PORT)) {
            SettlementStreetGraph graph = SettlementStreetGraph.plan(faction, city, morphology, 0);
            check(graph.hasConnectedCore(), morphology + " graph must be fully connected");
            check(graph.segments().stream().anyMatch(SettlementStreetGraph.RoadSegment::hasNonAxisGeometry),
                    morphology + " must contain true diagonal/curved geometry");
            String signature = graph.segments().stream()
                    .map(s -> s.centerline().stream()
                            .map(p -> Math.round(p.x()) + "," + Math.round(p.z()))
                            .reduce((a,b) -> a + ">" + b).orElse(""))
                    .reduce((a,b) -> a + "|" + b).orElse("");
            check(signatures.add(signature), morphology + " duplicated another topology");
        }
    }

    private static void connectedCoreAndParcels() {
        Faction faction = new Faction(7, "Road Realm", "Mayor");
        Settlement town = new Settlement(70, "Crossways", new SimPosition(0, 0), 900, 850);
        faction.addSettlement(town);
        List<ConstructionIntent> plan = SettlementPlanner.plan(faction, town);
        List<ConstructionIntent> roads = plan.stream().filter(i -> i.role() == StructureRole.ROAD).toList();
        check(!roads.isEmpty(), "town must plan roads");
        check(roads.stream().allMatch(ConstructionIntent::hasPath), "production ROAD intents must carry graph polylines");
        SettlementStreetGraph graph = SettlementStreetGraph.fromRoadIntents(town.id(), roads);
        check(!graph.isEmpty(), "street graph must contain segments");
        check(graph.hasConnectedCore(), "roads must form a connected usable core");
        List<SettlementParcelPlanner.ParcelPlan> parcels =
                SettlementParcelPlanner.plan(graph, faction, town, 20);
        check(!parcels.isEmpty(), "parcels must be generated along roads");
        for (SettlementParcelPlanner.ParcelPlan parcel : parcels) {
            check(graph.segmentByKey().containsKey(parcel.frontageRoadKey()),
                    "parcel must reference an actual road: " + parcel.frontageRoadKey());
            SettlementStreetGraph.RoadSegment segment = graph.segmentByKey().get(parcel.frontageSegmentKey());
            check(segment != null, "frontage segment must exist");
            check(parcel.footprintClearsRoad(segment), "house footprint must not overlap frontage road");
        }
    }

    private static void housesFaceFrontage() {
        Faction faction = new Faction(8, "Facing Realm", "Warden");
        Settlement village = new Settlement(80, "Frontage", new SimPosition(100, 100), 400, 380);
        faction.addSettlement(village);
        List<ConstructionIntent> plan = SettlementPlanner.plan(faction, village);
        List<ConstructionIntent> roads = plan.stream().filter(i -> i.role() == StructureRole.ROAD).toList();
        SettlementStreetGraph graph = SettlementStreetGraph.fromRoadIntents(village.id(), roads);
        List<SettlementParcelPlanner.ParcelPlan> parcels =
                SettlementParcelPlanner.plan(graph, faction, village, 40);
        check(parcels.size() >= 3, "village should yield several parcels");
        List<ConstructionIntent> houses = plan.stream().filter(i -> i.role() == StructureRole.HOUSE).toList();
        check(!houses.isEmpty(), "houses must be planned");
        int matched = 0;
        for (SettlementParcelPlanner.ParcelPlan parcel : parcels) {
            ConstructionIntent house = houses.stream()
                    .filter(h -> h.center().distanceTo(parcel.center()) < 0.5)
                    .findFirst().orElse(null);
            if (house == null) continue;
            check(house.rotationQuarterTurns() == parcel.orientationQuarterTurns(),
                    "house must face parcel frontage");
            check(house.rotationQuarterTurns()
                            == SettlementParcelPlanner.facingToward(house.center(), parcel.frontage().point()),
                    "door must face frontage road point");
            var bp = StructureBlueprintFactory.create(house);
            check(bp.placements().stream().anyMatch(p -> p.slot() == PaletteSlot.DOOR),
                    "house blueprint must include a door");
            matched++;
            if (matched >= 3) break;
        }
        check(matched >= 3, "houses must inherit parcel frontage facing (matched=" + matched + ")");
    }

    private static void housesCarryParcelBindings() {
        Faction faction = new Faction(9, "Bound Realm", "Steward");
        Settlement town = new Settlement(90, "Boundtown", new SimPosition(200, 200), 900, 850);
        faction.addSettlement(town);
        List<ConstructionIntent> plan = SettlementPlanner.plan(faction, town);
        List<ConstructionIntent> houses = plan.stream().filter(h -> h.role() == StructureRole.HOUSE).toList();
        check(!houses.isEmpty(), "bound town must plan houses");
        long bound = houses.stream().filter(ConstructionIntent::hasParcel).count();
        check(bound >= 3, "planned houses must bind parcelId (bound=" + bound + ")");
        for (ConstructionIntent house : houses) {
            if (!house.hasParcel()) continue;
            check(house.parcelId().startsWith("parcel:"), "parcel id format: " + house.parcelId());
            check(house.parcelWidth() >= house.width(), "parcel width must cover house");
            check(house.parcelDepth() >= house.depth(), "parcel depth must cover house");
        }
    }

    private static void check(boolean cond, String message) {
        if (!cond) throw new AssertionError(message);
    }
}
