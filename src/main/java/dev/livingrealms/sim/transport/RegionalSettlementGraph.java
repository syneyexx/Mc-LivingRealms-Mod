package dev.livingrealms.sim.transport;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementRole;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic topological source for intra-realm transport.
 *
 * <p>The graph encodes civilization hierarchy rather than connecting arbitrary nearest pairs:
 * capitals own major town spokes, towns own village branches, and villages own hamlet/local
 * branches. A small town ring gives the major network redundancy without turning every settlement
 * pair into a road.</p>
 */
public final class RegionalSettlementGraph {
    public enum Relation {
        CAPITAL_TOWN,
        CAPITAL_CITY,
        TOWN_RING,
        TOWN_VILLAGE,
        VILLAGE_HAMLET,
        LOCAL_FALLBACK
    }

    public record Edge(long fromSettlementId, long toSettlementId, Relation relation) {
        public Edge {
            if (fromSettlementId <= 0 || toSettlementId <= 0 || fromSettlementId == toSettlementId) {
                throw new IllegalArgumentException("edge identity");
            }
            Objects.requireNonNull(relation, "relation");
        }

        public long lowId() { return Math.min(fromSettlementId, toSettlementId); }
        public long highId() { return Math.max(fromSettlementId, toSettlementId); }
    }

    private RegionalSettlementGraph() {}

    public static List<Edge> plan(Faction faction) {
        Objects.requireNonNull(faction, "faction");
        List<Settlement> ordinary = faction.settlements().stream()
                .filter(s -> s.role().ordinarySurfaceSettlement())
                .sorted(Comparator.comparingLong(Settlement::id))
                .toList();
        if (ordinary.size() < 2) return List.of();

        Settlement capital = ordinary.stream()
                .filter(s -> s.role() == SettlementRole.CAPITAL)
                .max(Comparator.comparingInt(Settlement::population).thenComparingLong(Settlement::id))
                .orElseGet(() -> ordinary.stream()
                        .max(Comparator.comparingInt(Settlement::population).thenComparingLong(Settlement::id))
                        .orElseThrow());

        List<Settlement> cities = ordinary.stream()
                .filter(s -> s.id() != capital.id() && s.role() == SettlementRole.CITY)
                .toList();
        List<Settlement> towns = ordinary.stream()
                .filter(s -> s.id() != capital.id() && s.role() == SettlementRole.TOWN)
                .toList();
        List<Settlement> villages = ordinary.stream()
                .filter(s -> s.id() != capital.id() && s.role() == SettlementRole.VILLAGE)
                .toList();
        List<Settlement> hamlets = ordinary.stream()
                .filter(s -> s.id() != capital.id() && s.role() == SettlementRole.HAMLET)
                .toList();

        LinkedHashMap<Key, Edge> edges = new LinkedHashMap<>();

        for (Settlement city : cities) add(edges, capital, city, Relation.CAPITAL_CITY);
        for (Settlement town : towns) add(edges, capital, town, Relation.CAPITAL_TOWN);

        // Major towns also form a sparse ring ordered around the capital. This provides an
        // alternative regional corridor while keeping edge count linear.
        if (towns.size() >= 3) {
            List<Settlement> ring = towns.stream()
                    .sorted(Comparator
                            .comparingDouble((Settlement s) -> Math.atan2(
                                    s.position().z() - capital.position().z(),
                                    s.position().x() - capital.position().x()))
                            .thenComparingLong(Settlement::id))
                    .toList();
            for (int i = 0; i < ring.size(); i++) {
                add(edges, ring.get(i), ring.get((i + 1) % ring.size()), Relation.TOWN_RING);
            }
        }

        List<Settlement> majorParents = new ArrayList<>();
        majorParents.addAll(towns);
        majorParents.addAll(cities);
        if (majorParents.isEmpty()) majorParents.add(capital);

        for (Settlement village : villages) {
            Settlement parent = nearest(village, majorParents);
            add(edges, parent, village,
                    parent.role() == SettlementRole.TOWN || parent.role() == SettlementRole.CITY
                            ? Relation.TOWN_VILLAGE : Relation.LOCAL_FALLBACK);
        }

        List<Settlement> hamletParents = villages.isEmpty() ? majorParents : villages;
        for (Settlement hamlet : hamlets) {
            Settlement parent = nearest(hamlet, hamletParents);
            add(edges, parent, hamlet,
                    parent.role() == SettlementRole.VILLAGE
                            ? Relation.VILLAGE_HAMLET : Relation.LOCAL_FALLBACK);
        }

        // Any future ordinary role combination not covered above remains connected to its nearest
        // already-classified major node rather than becoming an isolated simulation point.
        for (Settlement settlement : ordinary) {
            if (settlement.id() == capital.id()) continue;
            boolean incident = edges.values().stream().anyMatch(e ->
                    e.fromSettlementId() == settlement.id() || e.toSettlementId() == settlement.id());
            if (!incident) add(edges, capital, settlement, Relation.LOCAL_FALLBACK);
        }

        return List.copyOf(edges.values());
    }

    private static Settlement nearest(Settlement child, List<Settlement> candidates) {
        return candidates.stream()
                .filter(s -> s.id() != child.id())
                .min(Comparator.comparingDouble((Settlement s) -> child.position().distanceTo(s.position()))
                        .thenComparingLong(Settlement::id))
                .orElseThrow();
    }

    private static void add(Map<Key, Edge> edges, Settlement a, Settlement b, Relation relation) {
        if (a.id() == b.id()) return;
        Key key = Key.of(a.id(), b.id());
        edges.putIfAbsent(key, new Edge(a.id(), b.id(), relation));
    }

    private record Key(long low, long high) {
        static Key of(long a, long b) {
            return a < b ? new Key(a, b) : new Key(b, a);
        }
    }
}
