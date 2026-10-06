package dev.livingrealms;

import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.transport.RegionalSettlementGraph;
import dev.livingrealms.sim.transport.TransportNetworkEngine;
import dev.livingrealms.sim.world.RoadLifeEngine;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Focused gate for hierarchical regional civilization topology and route discovery. */
public final class RegionalSettlementGraphTest {
    private RegionalSettlementGraphTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0x6E3779B9L);
        Faction faction = new Faction(state.nextId(), "Graph Realm", "Ruler");

        Settlement capital = add(state, faction, "Capital", 0, 0, 3600, SettlementRole.CAPITAL);
        Settlement townA = add(state, faction, "Town A", 800, 0, 900, SettlementRole.TOWN);
        Settlement townB = add(state, faction, "Town B", -450, 760, 850, SettlementRole.TOWN);
        Settlement townC = add(state, faction, "Town C", -520, -720, 800, SettlementRole.TOWN);
        Settlement villageA = add(state, faction, "Village A", 1150, 180, 220, SettlementRole.VILLAGE);
        Settlement villageB = add(state, faction, "Village B", -680, 1080, 200, SettlementRole.VILLAGE);
        Settlement villageC = add(state, faction, "Village C", -810, -980, 190, SettlementRole.VILLAGE);
        Settlement hamletA = add(state, faction, "Hamlet A", 1330, 250, 60, SettlementRole.HAMLET);
        Settlement hamletB = add(state, faction, "Hamlet B", -820, 1260, 55, SettlementRole.HAMLET);
        Settlement hamletC = add(state, faction, "Hamlet C", -960, -1130, 50, SettlementRole.HAMLET);
        state.addFaction(faction);
        faction.stockpile().add(ResourceType.STONE, 50_000);
        faction.stockpile().add(ResourceType.IRON, 10_000);

        List<RegionalSettlementGraph.Edge> graph = RegionalSettlementGraph.plan(faction);
        check(edge(graph, capital, townA, RegionalSettlementGraph.Relation.CAPITAL_TOWN), "capital→town A");
        check(edge(graph, capital, townB, RegionalSettlementGraph.Relation.CAPITAL_TOWN), "capital→town B");
        check(edge(graph, capital, townC, RegionalSettlementGraph.Relation.CAPITAL_TOWN), "capital→town C");
        check(hasIncident(graph, villageA, RegionalSettlementGraph.Relation.TOWN_VILLAGE), "village A parent");
        check(hasIncident(graph, villageB, RegionalSettlementGraph.Relation.TOWN_VILLAGE), "village B parent");
        check(hasIncident(graph, villageC, RegionalSettlementGraph.Relation.TOWN_VILLAGE), "village C parent");
        check(hasIncident(graph, hamletA, RegionalSettlementGraph.Relation.VILLAGE_HAMLET), "hamlet A parent");
        check(hasIncident(graph, hamletB, RegionalSettlementGraph.Relation.VILLAGE_HAMLET), "hamlet B parent");
        check(hasIncident(graph, hamletC, RegionalSettlementGraph.Relation.VILLAGE_HAMLET), "hamlet C parent");
        check(graph.stream().filter(e -> e.relation() == RegionalSettlementGraph.Relation.TOWN_RING).count() == 3,
                "three-town ring should have three edges");
        check(connected(graph, faction.settlements()), "regional graph must connect every ordinary settlement");

        List<RegionalSettlementGraph.Edge> repeat = RegionalSettlementGraph.plan(faction);
        check(graph.equals(repeat), "same faction state must produce identical graph");

        new TransportNetworkEngine().simulateDay(state); // day 0 triggers discovery
        for (RegionalSettlementGraph.Edge e : graph) {
            check(state.routes().stream().anyMatch(r -> sameEnds(r.fromSettlementId(), r.toSettlementId(),
                            e.fromSettlementId(), e.toSettlementId())),
                    "canonical route missing for graph edge " + e);
        }
        check(state.routes().size() == graph.size(),
                "route discovery should not add arbitrary intra-realm nearest-neighbour edges: routes="
                        + state.routes().size() + " graph=" + graph.size());

        corridorSitesBoundMeaningfulFabricGaps(state);
        System.out.println("PASS regional settlement graph: hierarchy + connectivity + deterministic route discovery"
                + " + <=450-block corridor fabric gaps");
    }

    private static void corridorSitesBoundMeaningfulFabricGaps(SimulationState state) {
        int first = RoadLifeEngine.ensureCorridorSites(state);
        int second = RoadLifeEngine.ensureCorridorSites(state);
        check(first > 0, "long inhabited routes should receive deterministic roadside anchors");
        check(second == 0, "corridor gap filling must be idempotent");

        for (var route : state.routes()) {
            if (!route.operational()) continue;
            if (route.mode() != dev.livingrealms.sim.transport.TransportMode.ROAD
                    && route.mode() != dev.livingrealms.sim.transport.TransportMode.CARAVAN) continue;
            Settlement from = state.findSettlement(route.fromSettlementId()).orElseThrow();
            Settlement to = state.findSettlement(route.toSettlementId()).orElseThrow();
            double distance = from.position().distanceTo(to.position());
            if (distance <= 450.0) continue;

            java.util.List<SimPosition> points = new java.util.ArrayList<>();
            points.add(from.position());
            state.roadsideSites().stream()
                    .filter(site -> site.active() && site.relatedRouteId() == route.id())
                    .map(dev.livingrealms.sim.world.RoadsideSite::position)
                    .forEach(points::add);
            points.add(to.position());
            points.sort(java.util.Comparator.comparingDouble(p -> p.distanceTo(from.position())));

            double maxGap = 0.0;
            for (int i = 1; i < points.size(); i++) {
                maxGap = Math.max(maxGap, points.get(i - 1).distanceTo(points.get(i)));
            }
            check(maxGap <= 450.0 + 1.0,
                    "inhabited route has accidental fabric gap " + Math.round(maxGap)
                            + " on route " + route.id());
        }
    }

    private static Settlement add(SimulationState state, Faction faction, String name,
                                  double x, double z, int population, SettlementRole role) {
        Settlement settlement = new Settlement(state.nextId(), name, new SimPosition(x, z),
                population, population + 120, SettlementOrigin.AUTHORED_SEED, false,
                DevelopmentMode.AUTO, role);
        faction.addSettlement(settlement);
        return settlement;
    }

    private static boolean edge(List<RegionalSettlementGraph.Edge> graph, Settlement a, Settlement b,
                                RegionalSettlementGraph.Relation relation) {
        return graph.stream().anyMatch(e -> e.relation() == relation
                && sameEnds(e.fromSettlementId(), e.toSettlementId(), a.id(), b.id()));
    }

    private static boolean hasIncident(List<RegionalSettlementGraph.Edge> graph, Settlement settlement,
                                       RegionalSettlementGraph.Relation relation) {
        return graph.stream().anyMatch(e -> e.relation() == relation
                && (e.fromSettlementId() == settlement.id() || e.toSettlementId() == settlement.id()));
    }

    private static boolean connected(List<RegionalSettlementGraph.Edge> graph, List<Settlement> settlements) {
        if (settlements.isEmpty()) return true;
        Set<Long> reached = new HashSet<>();
        reached.add(settlements.getFirst().id());
        boolean changed;
        do {
            changed = false;
            for (RegionalSettlementGraph.Edge edge : graph) {
                if (reached.contains(edge.fromSettlementId()) && reached.add(edge.toSettlementId())) changed = true;
                if (reached.contains(edge.toSettlementId()) && reached.add(edge.fromSettlementId())) changed = true;
            }
        } while (changed);
        return settlements.stream().filter(s -> s.role().ordinarySurfaceSettlement())
                .allMatch(s -> reached.contains(s.id()));
    }

    private static boolean sameEnds(long a1, long b1, long a2, long b2) {
        return (a1 == a2 && b1 == b2) || (a1 == b2 && b1 == a2);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
