package dev.livingrealms;

import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.SettlementBoundary;
import dev.livingrealms.sim.construction.SettlementMorphology;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.SettlementStreetGraph;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.world.SimPosition;
import java.util.List;

/** Defensive-topology regression: coherent perimeter, explicit road-aligned gates, no wall-sealed roads. */
public final class SettlementBoundaryTest {
    private SettlementBoundaryTest() {}

    public static void main(String[] args) {
        Faction faction = new Faction(70, "Boundary Realm", "Queen");
        Settlement capital = new Settlement(701, "Stone Crown", new SimPosition(0, 0), 4200, 4800,
                SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, SettlementRole.CAPITAL);
        faction.addSettlement(capital);
        faction.addSettlement(new Settlement(702, "East Town", new SimPosition(900, 120), 800, 900,
                SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, SettlementRole.TOWN));
        faction.addSettlement(new Settlement(703, "North Town", new SimPosition(-80, -850), 720, 820,
                SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, SettlementRole.TOWN));
        faction.addSettlement(new Settlement(704, "South Village", new SimPosition(160, 620), 260, 300,
                SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, SettlementRole.VILLAGE));

        SettlementStreetGraph base = SettlementStreetGraph.plan(
                faction, capital, SettlementMorphology.RADIAL_CAPITAL, 0);
        SettlementBoundary boundary = SettlementBoundary.plan(faction, capital, base, 0);

        check(boundary.perimeter().size() == 5, "closed square perimeter point count");
        check(boundary.perimeter().getFirst().distanceTo(boundary.perimeter().getLast()) < 1e-6,
                "perimeter must close");
        check(boundary.gates().size() == 4, "CITY+ must keep four stable gate openings");
        check(boundary.closedExceptGates(), "wall runs must cover perimeter except explicit gates");
        check(boundary.approachSegments().size() == boundary.gates().size(),
                "every gate requires one approach road");

        SettlementStreetGraph withApproaches = base.withAdditionalSegments(boundary.approachSegments());
        check(withApproaches.hasConnectedCore(), "gate approaches must remain connected to street graph");
        for (SettlementBoundary.GateNode gate : boundary.gates()) {
            var segment = withApproaches.segmentByKey().get(gate.roadKey());
            check(segment != null, "gate road missing: " + gate.roadKey());
            check(segment.centerline().stream().anyMatch(p -> p.distanceTo(gate.position()) < 1e-6),
                    "gate approach must pass through opening: " + gate.id());
            check(boundary.wallRuns().stream().noneMatch(run -> distanceToSegment(
                            gate.position(), run.from(), run.to()) < 5.0),
                    "wall run seals gate opening: " + gate.id());
        }

        List<ConstructionIntent> plan = SettlementPlanner.plan(faction, capital);
        List<ConstructionIntent> gates = plan.stream().filter(i -> i.role() == StructureRole.GATE).toList();
        List<ConstructionIntent> walls = plan.stream().filter(i -> i.role() == StructureRole.WALL).toList();
        check(gates.size() == 4, "planner must project four explicit gates");
        check(!walls.isEmpty(), "planner must project wall runs");
        for (ConstructionIntent gate : gates) {
            check(plan.stream().filter(i -> i.role() == StructureRole.ROAD && i.hasPath())
                            .anyMatch(road -> road.path().stream().anyMatch(p -> p.distanceTo(gate.center()) < 1.5)),
                    "planned gate has no road through it: " + gate.key());
        }

        List<SimPosition> before = boundary.gates().stream().map(SettlementBoundary.GateNode::position).toList();
        capital.addHousing(900);
        SettlementStreetGraph grownBase = SettlementStreetGraph.plan(
                faction, capital, SettlementMorphology.RADIAL_CAPITAL, 0);
        SettlementBoundary grown = SettlementBoundary.plan(faction, capital, grownBase, 0);
        check(before.equals(grown.gates().stream().map(SettlementBoundary.GateNode::position).toList()),
                "housing growth must not move finished gates");

        System.out.println("PASS settlement boundary: closed wall topology + explicit stable gates + road-through-gate invariant");
    }

    private static double distanceToSegment(SimPosition p, SimPosition a, SimPosition b) {
        double vx = b.x() - a.x(), vz = b.z() - a.z();
        double len2 = vx * vx + vz * vz;
        if (len2 <= 1e-9) return p.distanceTo(a);
        double t = ((p.x() - a.x()) * vx + (p.z() - a.z()) * vz) / len2;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(p.x() - (a.x() + vx * t), p.z() - (a.z() + vz * t));
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
