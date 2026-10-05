package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.List;
import java.util.Objects;

/**
 * Projects a morphology-owned {@link SettlementStreetGraph} into physical ROAD intents.
 *
 * <p>This class deliberately does not invent topology. Street graph first, block geometry second.</p>
 */
final class SettlementRoadPlanner {
    private SettlementRoadPlanner() {}

    static SettlementStreetGraph planGraph(Faction faction, Settlement settlement,
                                           SettlementMorphology morphology, int baseRotation) {
        return SettlementStreetGraph.plan(faction, settlement, morphology, baseRotation);
    }

    static void addRoadNetwork(List<ConstructionIntent> out, Faction faction, Settlement settlement,
                               SettlementStreetGraph graph) {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(graph, "graph");
        for (SettlementStreetGraph.RoadSegment segment : graph.segments()) {
            SimPosition center = midpoint(segment.centerline());
            int nominalDepth = Math.max(segment.width(), (int) Math.ceil(segment.length()) + 1);
            out.add(new ConstructionIntent(
                    segment.key(), faction.id(), settlement.id(), StructureRole.ROAD, center,
                    segment.width(), nominalDepth, 0, segment.priority(),
                    "", 0, 0, segment.centerline()));
        }
    }

    private static SimPosition midpoint(List<SimPosition> points) {
        if (points.isEmpty()) throw new IllegalArgumentException("points");
        double total = 0;
        for (int i = 1; i < points.size(); i++) total += points.get(i - 1).distanceTo(points.get(i));
        if (total <= 1e-6) return points.getFirst();
        double target = total * .5, walked = 0;
        for (int i = 1; i < points.size(); i++) {
            SimPosition a = points.get(i - 1), b = points.get(i);
            double length = a.distanceTo(b);
            if (walked + length >= target) {
                return a.lerp(b, (target - walked) / Math.max(1e-6, length));
            }
            walked += length;
        }
        return points.getLast();
    }
}
