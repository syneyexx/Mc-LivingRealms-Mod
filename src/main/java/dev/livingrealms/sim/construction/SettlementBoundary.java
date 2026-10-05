package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.transport.RegionalSettlementGraph;
import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Coherent defensive boundary for CITY+ settlements.
 *
 * <p>The perimeter is an ordered closed polyline. Gates are explicit openings derived from
 * regional-road directions where possible. Wall runs cover every remaining perimeter interval,
 * and graph-authored approach roads pass through the gate nodes.</p>
 */
public final class SettlementBoundary {
    public enum Side { NORTH, EAST, SOUTH, WEST }

    public record GateNode(
            String id,
            Side side,
            SimPosition position,
            String roadKey,
            int rotationQuarterTurns
    ) {
        public GateNode {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(position, "position");
            if (roadKey == null || roadKey.isBlank()) throw new IllegalArgumentException("roadKey");
            rotationQuarterTurns = Math.floorMod(rotationQuarterTurns, 4);
        }
    }

    public record WallRun(
            Side side,
            SimPosition from,
            SimPosition to,
            int rotationQuarterTurns
    ) {
        public WallRun {
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            rotationQuarterTurns = Math.floorMod(rotationQuarterTurns, 4);
            if (from.distanceTo(to) < 1.0) throw new IllegalArgumentException("wall run");
        }

        public double length() { return from.distanceTo(to); }
        public SimPosition center() { return from.lerp(to, .5); }
    }

    private static final double GATE_HALF_OPENING = 6.0;

    private final long settlementId;
    private final double radius;
    private final List<SimPosition> perimeter;
    private final List<GateNode> gates;
    private final List<WallRun> wallRuns;
    private final List<SettlementStreetGraph.RoadSegment> approachSegments;

    public SettlementBoundary(long settlementId, double radius, List<SimPosition> perimeter,
                              List<GateNode> gates, List<WallRun> wallRuns,
                              List<SettlementStreetGraph.RoadSegment> approachSegments) {
        if (settlementId <= 0) throw new IllegalArgumentException("settlementId");
        if (!(radius > 0)) throw new IllegalArgumentException("radius");
        this.settlementId = settlementId;
        this.radius = radius;
        this.perimeter = List.copyOf(Objects.requireNonNull(perimeter, "perimeter"));
        this.gates = List.copyOf(Objects.requireNonNull(gates, "gates"));
        this.wallRuns = List.copyOf(Objects.requireNonNull(wallRuns, "wallRuns"));
        this.approachSegments = List.copyOf(Objects.requireNonNull(approachSegments, "approachSegments"));
        if (!closedExceptGates()) throw new IllegalArgumentException("incoherent boundary");
    }

    public long settlementId() { return settlementId; }
    public double radius() { return radius; }
    public List<SimPosition> perimeter() { return perimeter; }
    public List<GateNode> gates() { return gates; }
    public List<WallRun> wallRuns() { return wallRuns; }
    public List<SettlementStreetGraph.RoadSegment> approachSegments() { return approachSegments; }

    public boolean closedExceptGates() {
        if (perimeter.size() != 5 || !same(perimeter.getFirst(), perimeter.getLast())) return false;
        if (gates.isEmpty()) return false;
        Map<Side, Double> covered = new EnumMap<>(Side.class);
        Map<Side, Integer> openings = new EnumMap<>(Side.class);
        for (Side side : Side.values()) { covered.put(side, 0.0); openings.put(side, 0); }
        for (WallRun run : wallRuns) covered.merge(run.side(), run.length(), Double::sum);
        for (GateNode gate : gates) openings.merge(gate.side(), 1, Integer::sum);
        double sideLength = radius * 2.0;
        for (Side side : Side.values()) {
            double expected = sideLength - openings.get(side) * GATE_HALF_OPENING * 2.0;
            if (Math.abs(covered.get(side) - expected) > 2.5) return false;
        }
        return true;
    }

    public static SettlementBoundary plan(Faction faction, Settlement settlement,
                                          SettlementStreetGraph baseGraph, int baseRotation) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(baseGraph, "baseGraph");
        if (settlement.tier().ordinal() < Settlement.Tier.CITY.ordinal()) {
            throw new IllegalArgumentException("boundary requires CITY+");
        }

        double radius = settlement.tier() == Settlement.Tier.METROPOLIS ? 238.0 : 182.0;
        SettlementMorphology morphology = SettlementMorphology.derive(faction, settlement);
        if (morphology == SettlementMorphology.HILL_TOWN) radius *= .82;
        if (morphology == SettlementMorphology.WALLED_CORE) radius *= .90;

        List<Direction> desired = regionalDirections(faction, settlement, baseRotation);
        // Fill missing sides from the strongest graph exits so a standalone CITY still has real gate roads.
        List<Direction> graphExits = graphExitDirections(settlement, baseGraph, baseRotation);
        for (Direction exit : graphExits) {
            if (desired.stream().noneMatch(d -> d.side() == exit.side())) desired.add(exit);
            if (desired.size() >= 4) break;
        }
        if (desired.isEmpty()) throw new IllegalStateException("CITY+ street graph has no exits");
        desired = desired.stream()
                .sorted(Comparator.comparingInt((Direction d) -> d.priority()).reversed()
                        .thenComparing(d -> d.side().ordinal()))
                .toList();

        EnumMap<Side, Direction> bySide = new EnumMap<>(Side.class);
        for (Direction d : desired) bySide.putIfAbsent(d.side(), d);
        List<GateLocal> locals = new ArrayList<>();
        int gateIndex = 0;
        for (Side side : Side.values()) {
            Direction d = bySide.get(side);
            if (d == null) continue;
            double scale = radius / Math.max(Math.abs(d.localDx()), Math.abs(d.localDz()));
            double x = clamp(d.localDx() * scale, -radius + 18, radius - 18);
            double z = clamp(d.localDz() * scale, -radius + 18, radius - 18);
            switch (side) {
                case NORTH -> z = -radius;
                case SOUTH -> z = radius;
                case WEST -> x = -radius;
                case EAST -> x = radius;
            }
            SimPosition gate = localToWorld(settlement, baseRotation, x, z);
            String roadKey = "roadgraph:gate:" + side.name().toLowerCase(java.util.Locale.ROOT) + ":" + gateIndex;
            locals.add(new GateLocal(side, x, z, gate, roadKey, gateRotation(side, baseRotation)));
            gateIndex++;
        }

        List<GateNode> gates = locals.stream()
                .map(g -> new GateNode("gate:" + g.side().name().toLowerCase(java.util.Locale.ROOT),
                        g.side(), g.world(), g.roadKey(), g.rotationQuarterTurns()))
                .toList();
        List<WallRun> runs = wallRuns(settlement, baseRotation, radius, locals);
        List<SettlementStreetGraph.RoadSegment> approaches =
                approachSegments(settlement, baseGraph, locals);

        List<SimPosition> perimeter = List.of(
                localToWorld(settlement, baseRotation, -radius, -radius),
                localToWorld(settlement, baseRotation, radius, -radius),
                localToWorld(settlement, baseRotation, radius, radius),
                localToWorld(settlement, baseRotation, -radius, radius),
                localToWorld(settlement, baseRotation, -radius, -radius));

        return new SettlementBoundary(settlement.id(), radius, perimeter, gates, runs, approaches);
    }

    private static List<Direction> regionalDirections(Faction faction, Settlement settlement, int baseRotation) {
        List<Direction> out = new ArrayList<>();
        for (RegionalSettlementGraph.Edge edge : RegionalSettlementGraph.plan(faction)) {
            long otherId;
            if (edge.fromSettlementId() == settlement.id()) otherId = edge.toSettlementId();
            else if (edge.toSettlementId() == settlement.id()) otherId = edge.fromSettlementId();
            else continue;
            Settlement other = faction.settlements().stream().filter(s -> s.id() == otherId).findFirst().orElse(null);
            if (other == null) continue;
            double worldDx = other.position().x() - settlement.position().x();
            double worldDz = other.position().z() - settlement.position().z();
            double[] local = worldToLocalVector(baseRotation, worldDx, worldDz);
            Side side = dominantSide(local[0], local[1]);
            int priority = switch (edge.relation()) {
                case CAPITAL_CITY -> 500;
                case CAPITAL_TOWN -> 480;
                case TOWN_RING -> 430;
                case TOWN_VILLAGE -> 400;
                case VILLAGE_HAMLET -> 360;
                case LOCAL_FALLBACK -> 320;
            };
            out.add(new Direction(side, local[0], local[1], priority));
        }
        return out;
    }

    private static List<Direction> graphExitDirections(Settlement settlement,
                                                       SettlementStreetGraph graph, int baseRotation) {
        List<Direction> out = new ArrayList<>();
        for (SettlementStreetGraph.RoadSegment segment : graph.segments()) {
            SimPosition a = segment.centerline().getFirst(), b = segment.centerline().getLast();
            SimPosition endpoint = a.distanceTo(settlement.position()) >= b.distanceTo(settlement.position()) ? a : b;
            double[] local = worldToLocalVector(baseRotation,
                    endpoint.x() - settlement.position().x(), endpoint.z() - settlement.position().z());
            if (Math.hypot(local[0], local[1]) < 12) continue;
            out.add(new Direction(dominantSide(local[0], local[1]), local[0], local[1], segment.priority()));
        }
        out.sort(Comparator.comparingInt(Direction::priority).reversed());
        return out;
    }

    private static List<SettlementStreetGraph.RoadSegment> approachSegments(
            Settlement settlement, SettlementStreetGraph graph, List<GateLocal> gates) {
        List<SettlementStreetGraph.RoadSegment> out = new ArrayList<>();
        for (GateLocal gate : gates) {
            double dx = gate.world().x() - settlement.position().x();
            double dz = gate.world().z() - settlement.position().z();
            double len = Math.max(1.0, Math.hypot(dx, dz));
            double ux = dx / len, uz = dz / len;
            SimPosition anchor = bestAnchor(settlement, graph, ux, uz);
            SimPosition mid = new SimPosition(
                    anchor.x() + (gate.world().x() - anchor.x()) * .62,
                    anchor.z() + (gate.world().z() - anchor.z()) * .62);
            SimPosition outside = new SimPosition(gate.world().x() + ux * 32.0, gate.world().z() + uz * 32.0);
            StreetType type = settlement.role() == SettlementRole.CAPITAL
                    ? StreetType.ROYAL_ROAD : StreetType.REGIONAL_ROAD;
            out.add(new SettlementStreetGraph.RoadSegment(
                    gate.roadKey(), type, type.width(), anchor, outside,
                    List.of(anchor, mid, gate.world(), outside), 148,
                    SettlementGrowthLayer.DEFENSIVE_EXPANSION));
        }
        return List.copyOf(out);
    }

    private static SimPosition bestAnchor(Settlement settlement, SettlementStreetGraph graph, double ux, double uz) {
        SimPosition best = settlement.position();
        double bestScore = -Double.MAX_VALUE;
        for (SettlementStreetGraph.RoadSegment segment : graph.segments()) {
            for (SimPosition point : segment.centerline()) {
                double dx = point.x() - settlement.position().x();
                double dz = point.z() - settlement.position().z();
                double score = dx * ux + dz * uz - Math.abs(dx * uz - dz * ux) * .20;
                if (score > bestScore) {
                    bestScore = score;
                    best = point;
                }
            }
        }
        return best;
    }

    private static List<WallRun> wallRuns(Settlement settlement, int baseRotation, double radius,
                                          List<GateLocal> gates) {
        List<WallRun> out = new ArrayList<>();
        for (Side side : Side.values()) {
            List<GateLocal> sideGates = gates.stream().filter(g -> g.side() == side)
                    .sorted(Comparator.comparingDouble(g -> along(g, side))).toList();
            double cursor = -radius;
            for (GateLocal gate : sideGates) {
                double at = along(gate, side);
                double openStart = at - GATE_HALF_OPENING;
                double openEnd = at + GATE_HALF_OPENING;
                if (openStart - cursor >= 1.0) {
                    out.add(run(settlement, baseRotation, side, radius, cursor, openStart));
                }
                cursor = openEnd;
            }
            if (radius - cursor >= 1.0) out.add(run(settlement, baseRotation, side, radius, cursor, radius));
        }
        return List.copyOf(out);
    }

    private static WallRun run(Settlement settlement, int baseRotation, Side side, double radius,
                               double fromAlong, double toAlong) {
        double x1, z1, x2, z2;
        switch (side) {
            case NORTH -> { x1 = fromAlong; z1 = -radius; x2 = toAlong; z2 = -radius; }
            case SOUTH -> { x1 = fromAlong; z1 = radius; x2 = toAlong; z2 = radius; }
            case WEST -> { x1 = -radius; z1 = fromAlong; x2 = -radius; z2 = toAlong; }
            case EAST -> { x1 = radius; z1 = fromAlong; x2 = radius; z2 = toAlong; }
            default -> throw new IllegalStateException();
        }
        int rotation = (side == Side.NORTH || side == Side.SOUTH) ? baseRotation + 1 : baseRotation;
        return new WallRun(side,
                localToWorld(settlement, baseRotation, x1, z1),
                localToWorld(settlement, baseRotation, x2, z2),
                rotation);
    }

    private static double along(GateLocal gate, Side side) {
        return side == Side.NORTH || side == Side.SOUTH ? gate.localX() : gate.localZ();
    }

    private static int gateRotation(Side side, int baseRotation) {
        return switch (side) {
            case NORTH -> baseRotation;
            case SOUTH -> baseRotation + 2;
            case WEST -> baseRotation + 1;
            case EAST -> baseRotation + 3;
        };
    }

    private static Side dominantSide(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Side.EAST : Side.WEST;
        return dz >= 0 ? Side.SOUTH : Side.NORTH;
    }

    private static double[] worldToLocalVector(int quarterTurns, double x, double z) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> new double[]{x, z};
            case 1 -> new double[]{z, -x};
            case 2 -> new double[]{-x, -z};
            default -> new double[]{-z, x};
        };
    }

    private static SimPosition localToWorld(Settlement settlement, int quarterTurns, double x, double z) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> new SimPosition(settlement.position().x() + x, settlement.position().z() + z);
            case 1 -> new SimPosition(settlement.position().x() - z, settlement.position().z() + x);
            case 2 -> new SimPosition(settlement.position().x() - x, settlement.position().z() - z);
            default -> new SimPosition(settlement.position().x() + z, settlement.position().z() - x);
        };
    }

    private static boolean same(SimPosition a, SimPosition b) {
        return a.distanceTo(b) < 1.0e-6;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Direction(Side side, double localDx, double localDz, int priority) {}
    private record GateLocal(Side side, double localX, double localZ, SimPosition world,
                             String roadKey, int rotationQuarterTurns) {}
}
