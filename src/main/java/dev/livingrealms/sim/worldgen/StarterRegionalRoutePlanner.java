package dev.livingrealms.sim.worldgen;

import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.transport.RegionalSettlementGraph;
import dev.livingrealms.sim.transport.TransportMode;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure deterministic starter regional-road topology and gate-aligned endpoints. */
public final class StarterRegionalRoutePlanner {
    public static final long STARTER_ROUTE_ID_BASE = 2_000_000L;
    private static final int ROUTE_IDS_PER_REALM = 100;

    public record RoutePlan(
            long stableRouteId,
            String stableKey,
            long factionId,
            long fromSettlementId,
            long toSettlementId,
            RegionalSettlementGraph.Relation relation,
            SimPosition from,
            SimPosition to,
            boolean rural
    ) {
        public RoutePlan {
            if (stableRouteId <= 0 || factionId <= 0 || fromSettlementId <= 0 || toSettlementId <= 0) {
                throw new IllegalArgumentException("route ids");
            }
            if (stableKey == null || stableKey.isBlank()) throw new IllegalArgumentException("stableKey");
            relation = Objects.requireNonNull(relation, "relation");
            from = Objects.requireNonNull(from, "from");
            to = Objects.requireNonNull(to, "to");
        }

        public TransportRoute asTransportRoute() {
            double distance = Math.max(1.0, from.distanceTo(to));
            return new TransportRoute(stableRouteId, factionId, fromSettlementId, toSettlementId,
                    TransportMode.ROAD, distance, 0.65, 0.72, rural ? 220.0 : 420.0);
        }
    }

    private StarterRegionalRoutePlanner() {}

    public static List<RoutePlan> plan(StarterCivilizationLayoutPlanner.Layout layout) {
        Objects.requireNonNull(layout, "layout");
        List<RoutePlan> out = new ArrayList<>();
        for (int realmOrdinal = 0; realmOrdinal < layout.realms().size(); realmOrdinal++) {
            StarterCivilizationLayoutPlanner.RealmPlan realmPlan = layout.realms().get(realmOrdinal);
            Faction faction = snapshot(realmPlan);
            Map<Long, Settlement> byId = new HashMap<>();
            for (Settlement settlement : faction.settlements()) byId.put(settlement.id(), settlement);
            List<RegionalSettlementGraph.Edge> edges = RegionalSettlementGraph.plan(faction);
            for (int edgeOrdinal = 0; edgeOrdinal < edges.size(); edgeOrdinal++) {
                RegionalSettlementGraph.Edge edge = edges.get(edgeOrdinal);
                Settlement fromSettlement = Objects.requireNonNull(byId.get(edge.fromSettlementId()));
                Settlement toSettlement = Objects.requireNonNull(byId.get(edge.toSettlementId()));
                SimPosition from = endpoint(faction, fromSettlement, toSettlement.position());
                SimPosition to = endpoint(faction, toSettlement, fromSettlement.position());
                boolean rural = fromSettlement.role() == SettlementRole.VILLAGE
                        || fromSettlement.role() == SettlementRole.HAMLET
                        || toSettlement.role() == SettlementRole.VILLAGE
                        || toSettlement.role() == SettlementRole.HAMLET;
                long id = STARTER_ROUTE_ID_BASE + (long) realmOrdinal * ROUTE_IDS_PER_REALM + edgeOrdinal;
                String key = "realm:" + realmPlan.definition().id() + "/route:"
                        + edge.lowId() + "-" + edge.highId() + ":" + edge.relation().name().toLowerCase(java.util.Locale.ROOT);
                out.add(new RoutePlan(id, key, faction.id(), edge.fromSettlementId(), edge.toSettlementId(),
                        edge.relation(), from, to, rural));
            }
        }
        return List.copyOf(out);
    }

    private static SimPosition endpoint(Faction faction, Settlement settlement, SimPosition target) {
        return SettlementPlanner.boundary(faction, settlement)
                .map(boundary -> boundary.gateToward(settlement.position(), target).position())
                .orElse(settlement.position());
    }

    private static Faction snapshot(StarterCivilizationLayoutPlanner.RealmPlan realmPlan) {
        Faction faction = new Faction(realmPlan.factionId(), realmPlan.definition().displayName(),
                realmPlan.definition().rulerSeedName());
        faction.restoreTechnology(realmPlan.definition().technology());
        faction.restoreTreasury(realmPlan.definition().treasury());
        for (StarterCivilizationLayoutPlanner.SettlementPlan starter : realmPlan.settlements()) {
            faction.addSettlement(new Settlement(starter.id(), starter.name(), starter.position(),
                    starter.population(), starter.housing(), SettlementOrigin.AUTHORED_SEED,
                    false, DevelopmentMode.AUTO, starter.role()));
        }
        return faction;
    }
}
