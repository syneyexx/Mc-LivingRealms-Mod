package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit street graph derived from planned road intents. Parcels and house facing use this
 * geometry as source of truth rather than an abstract spiral grid.
 */
public final class SettlementStreetGraph {
    public record RoadNode(long id, SimPosition position) {
        public RoadNode {
            if (id <= 0) throw new IllegalArgumentException("id");
            position = Objects.requireNonNull(position, "position");
        }
    }

    /**
     * Resolved local centerline segment. Axis-aligned at planning level; terrain correction may
     * refine the polyline downstream without changing parcel frontage ownership.
     */
    public record RoadSegment(
            String key,
            StreetType streetType,
            int width,
            SimPosition start,
            SimPosition end,
            List<SimPosition> centerline,
            int priority,
            SettlementGrowthLayer growthLayer
    ) {
        public RoadSegment {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("key");
            streetType = Objects.requireNonNull(streetType, "streetType");
            if (width <= 0) throw new IllegalArgumentException("width");
            start = Objects.requireNonNull(start, "start");
            end = Objects.requireNonNull(end, "end");
            centerline = List.copyOf(Objects.requireNonNull(centerline, "centerline"));
            if (centerline.isEmpty()) throw new IllegalArgumentException("centerline");
            growthLayer = Objects.requireNonNullElse(growthLayer, SettlementGrowthLayer.HISTORIC_CORE);
        }

        public double length() {
            return start.distanceTo(end);
        }

        public boolean axisAlignedAlongX() {
            return Math.abs(end.z() - start.z()) < 0.5;
        }
    }

    /** Connectivity edge between two nodes along one segment. */
    public record StreetEdge(long fromNodeId, long toNodeId, String segmentKey) {
        public StreetEdge {
            if (fromNodeId <= 0 || toNodeId <= 0) throw new IllegalArgumentException("node ids");
            if (segmentKey == null || segmentKey.isBlank()) throw new IllegalArgumentException("segmentKey");
        }
    }

    private final long settlementId;
    private final List<RoadNode> nodes;
    private final List<RoadSegment> segments;
    private final List<StreetEdge> edges;

    public SettlementStreetGraph(long settlementId, List<RoadNode> nodes, List<RoadSegment> segments, List<StreetEdge> edges) {
        if (settlementId <= 0) throw new IllegalArgumentException("settlementId");
        this.settlementId = settlementId;
        this.nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        this.segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        this.edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
    }

    public long settlementId() { return settlementId; }
    public List<RoadNode> nodes() { return nodes; }
    public List<RoadSegment> segments() { return segments; }
    public List<StreetEdge> edges() { return edges; }

    public boolean isEmpty() { return segments.isEmpty(); }

    /** Build a graph from already-planned road construction intents. */
    public static SettlementStreetGraph fromRoadIntents(long settlementId, List<ConstructionIntent> roadIntents) {
        Objects.requireNonNull(roadIntents, "roadIntents");
        List<RoadSegment> segments = new ArrayList<>();
        List<RoadNode> nodes = new ArrayList<>();
        List<StreetEdge> edges = new ArrayList<>();
        Map<String, Long> nodeIndex = new LinkedHashMap<>();
        long[] nextNode = {1L};
        for (ConstructionIntent intent : roadIntents) {
            if (intent.role() != StructureRole.ROAD) continue;
            RoadSegment segment = segmentFromIntent(intent);
            segments.add(segment);
            // Sample centerline so crossing arterials share quantized intersection nodes.
            List<SimPosition> samples = segment.centerline();
            if (samples.size() < 2) {
                samples = List.of(segment.start(), segment.end());
            }
            long prev = -1;
            for (SimPosition sample : samples) {
                long id = nodeId(nodeIndex, nodes, sample, nextNode);
                if (prev > 0 && prev != id) {
                    edges.add(new StreetEdge(prev, id, segment.key()));
                }
                prev = id;
            }
        }
        return new SettlementStreetGraph(settlementId, nodes, segments, edges);
    }

    private static long nodeId(Map<String, Long> index, List<RoadNode> nodes, SimPosition pos, long[] nextId) {
        // Quantize so orthogonal crossings land on the same node key.
        long qx = Math.round(pos.x() / 8.0) * 8;
        long qz = Math.round(pos.z() / 8.0) * 8;
        String key = qx + ":" + qz;
        Long existing = index.get(key);
        if (existing != null) return existing;
        long id = nextId[0]++;
        index.put(key, id);
        nodes.add(new RoadNode(id, new SimPosition(qx, qz)));
        return id;
    }

    static RoadSegment segmentFromIntent(ConstructionIntent intent) {
        int turns = Math.floorMod(intent.rotationQuarterTurns(), 4);
        int halfDepth = intent.depth() / 2;
        // Local depth axis is ±Z before rotation; after rotation it becomes the road length axis.
        SimPosition start = offset(intent.center(), turns, 0, -halfDepth);
        SimPosition end = offset(intent.center(), turns, 0, halfDepth);
        List<SimPosition> line = new ArrayList<>();
        int steps = Math.max(1, intent.depth() / 4);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            line.add(start.lerp(end, t));
        }
        StreetType type = StreetType.forWidth(intent.width(), false, intent.key().contains("market"), intent.key().contains("regional") || intent.key().contains("royal"));
        SettlementGrowthLayer layer = growthLayerFromKey(intent.key());
        return new RoadSegment(intent.key(), type, intent.width(), start, end, line, intent.priority(), layer);
    }

    private static SettlementGrowthLayer growthLayerFromKey(String key) {
        String k = key == null ? "" : key.toLowerCase(java.util.Locale.ROOT);
        for (SettlementGrowthLayer layer : SettlementGrowthLayer.values()) {
            if (k.contains(layer.name().toLowerCase(java.util.Locale.ROOT))) return layer;
        }
        return SettlementGrowthLayer.HISTORIC_CORE;
    }

    private static SimPosition offset(SimPosition origin, int quarterTurns, double x, double z) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> new SimPosition(origin.x() + x, origin.z() + z);
            case 1 -> new SimPosition(origin.x() - z, origin.z() + x);
            case 2 -> new SimPosition(origin.x() - x, origin.z() - z);
            default -> new SimPosition(origin.x() + z, origin.z() - x);
        };
    }

    /** True when every segment shares an endpoint with at least one other (or there is a single spine). */
    public boolean hasConnectedCore() {
        if (segments.isEmpty()) return false;
        if (segments.size() == 1) return true;
        Map<String, Integer> degree = new LinkedHashMap<>();
        for (StreetEdge edge : edges) {
            degree.merge(String.valueOf(edge.fromNodeId()), 1, Integer::sum);
            degree.merge(String.valueOf(edge.toNodeId()), 1, Integer::sum);
        }
        // BFS over undirected edges from the first node.
        if (nodes.isEmpty()) return false;
        long start = nodes.getFirst().id();
        java.util.ArrayDeque<Long> q = new java.util.ArrayDeque<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty()) {
            long cur = q.removeFirst();
            for (StreetEdge edge : edges) {
                long other = edge.fromNodeId() == cur ? edge.toNodeId()
                        : edge.toNodeId() == cur ? edge.fromNodeId() : -1;
                if (other > 0 && seen.add(other)) q.add(other);
            }
        }
        // Allow organic stubs: require a majority of nodes reachable from the historic core.
        return seen.size() * 2 >= nodes.size();
    }

    public Map<String, RoadSegment> segmentByKey() {
        Map<String, RoadSegment> map = new LinkedHashMap<>();
        for (RoadSegment segment : segments) map.put(segment.key(), segment);
        return Collections.unmodifiableMap(map);
    }
}
