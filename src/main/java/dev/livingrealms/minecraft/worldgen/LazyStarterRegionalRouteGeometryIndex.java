package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.sim.transport.RouteProjectionPlanner;
import dev.livingrealms.sim.transport.TerrainCorridorPlanner;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.worldgen.StarterRegionalRoutePlanner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import net.minecraft.server.level.ServerLevel;

/**
 * Bounded lazy actual-terrain geometry for starter regional routes.
 *
 * <p>No chunk is allowed to solve a multi-kilometre route. The authored strategic line is clipped
 * into deterministic 256x256-block tiles. Each tile runs a strictly bounded local terrain-cost A*
 * and is cached/indexed independently. Adjacent tiles share the same authored boundary crossing,
 * so generation order cannot alter connectivity. Terrain-relocated settlement gates use short
 * bounded endpoint connectors rather than forcing the entire regional route to be replanned.</p>
 */
public final class LazyStarterRegionalRouteGeometryIndex {
    private static final int TILE_CHUNKS = 16;
    private static final int TILE_BLOCKS = TILE_CHUNKS * 16;
    private static final int LOCAL_CELL_SIZE = 16;
    private static final int LOCAL_MAX_NODES = 384;
    private static final double ENDPOINT_TRIGGER_RADIUS = 512.0;

    private record TileKey(long routeId, int tileX, int tileZ) {}
    private record EndpointKey(long routeId, boolean from) {}
    private record Segment(SimPosition from, SimPosition to) {}
    private record Bounds(int minX, int minZ, int maxX, int maxZ) {}

    private final ServerLevel level;
    private final List<StarterRegionalRoutePlanner.RoutePlan> routes;
    private final Map<Long, StarterRegionalRoutePlanner.RoutePlan> routeById;
    private final LazyStarterCivilizationFabricIndex settlementFabric;
    private final StarterGeneratorTerrainCache terrainCache;

    private final ConcurrentHashMap<TileKey, FutureTask<StarterRegionalRouteGeometryIndex.RouteSlice>>
            tileTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<EndpointKey, FutureTask<StarterRegionalRouteGeometryIndex.RouteSlice>>
            connectorTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<StarterRegionalRouteGeometryIndex.RouteSlice>>
            byChunk = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, StarterRegionalRouteGeometryIndex.PlannedPoint>>
            accumulatedPointsByRoute = new ConcurrentHashMap<>();

    public LazyStarterRegionalRouteGeometryIndex(
            ServerLevel level,
            List<StarterRegionalRoutePlanner.RoutePlan> routes,
            LazyStarterCivilizationFabricIndex settlementFabric,
            StarterGeneratorTerrainCache terrainCache) {
        this.level = Objects.requireNonNull(level, "level");
        this.routes = List.copyOf(Objects.requireNonNull(routes, "routes"));
        this.settlementFabric = Objects.requireNonNull(settlementFabric, "settlementFabric");
        this.terrainCache = Objects.requireNonNull(terrainCache, "terrainCache");

        Map<Long, StarterRegionalRoutePlanner.RoutePlan> mutable = new HashMap<>();
        for (StarterRegionalRoutePlanner.RoutePlan route : routes) {
            mutable.put(route.stableRouteId(), route);
        }
        this.routeById = Map.copyOf(mutable);
    }

    public StarterRegionalRouteGeometryIndex.ChunkSlice query(int chunkX, int chunkZ) {
        long chunkKey = pack(chunkX, chunkZ);
        int tileX = Math.floorDiv(chunkX, TILE_CHUNKS);
        int tileZ = Math.floorDiv(chunkZ, TILE_CHUNKS);
        Bounds tile = tileBounds(tileX, tileZ);

        double chunkCenterX = (chunkX << 4) + 7.5;
        double chunkCenterZ = (chunkZ << 4) + 7.5;

        for (StarterRegionalRoutePlanner.RoutePlan route : routes) {
            // Endpoint connectors are short and bounded. Every potentially affected chunk is inside
            // this trigger radius, so a connector cannot appear after an already-generated slice.
            if (nearPoint(chunkCenterX, chunkCenterZ, route.from(), ENDPOINT_TRIGGER_RADIUS)) {
                resolveEndpointConnector(route, true);
            }
            if (nearPoint(chunkCenterX, chunkCenterZ, route.to(), ENDPOINT_TRIGGER_RADIUS)) {
                resolveEndpointConnector(route, false);
            }

            if (clipToRect(route.from(), route.to(), tile) != null) {
                resolveTile(route, tileX, tileZ);
            }
        }

        CopyOnWriteArrayList<StarterRegionalRouteGeometryIndex.RouteSlice> found =
                byChunk.get(chunkKey);
        if (found == null || found.isEmpty()) {
            return StarterRegionalRouteGeometryIndex.ChunkSlice.EMPTY;
        }
        return new StarterRegionalRouteGeometryIndex.ChunkSlice(
                List.copyOf(new LinkedHashSet<>(found)));
    }

    /**
     * Diagnostic/compatibility view of route points that have actually been generated/planned so
     * far. Calling this method never forces the rest of a route to be solved.
     */
    public List<StarterRegionalRouteGeometryIndex.PlannedPoint> pointsForRoute(long routeId) {
        var points = accumulatedPointsByRoute.get(routeId);
        if (points == null || points.isEmpty()) return List.of();
        return points.values().stream()
                .sorted(Comparator
                        .comparingInt(StarterRegionalRouteGeometryIndex.PlannedPoint::x)
                        .thenComparingInt(StarterRegionalRouteGeometryIndex.PlannedPoint::z))
                .toList();
    }

    public int resolvedRouteCount() {
        Set<Long> routeIds = new HashSet<>();
        for (var entry : tileTasks.entrySet()) {
            if (entry.getValue().isDone()) routeIds.add(entry.getKey().routeId());
        }
        for (var entry : connectorTasks.entrySet()) {
            if (entry.getValue().isDone()) routeIds.add(entry.getKey().routeId());
        }
        return routeIds.size();
    }

    public int totalRouteCount() {
        return routes.size();
    }

    int plannedTileCount() {
        int count = 0;
        for (FutureTask<?> task : tileTasks.values()) if (task.isDone()) count++;
        return count;
    }

    private StarterRegionalRouteGeometryIndex.RouteSlice resolveTile(
            StarterRegionalRoutePlanner.RoutePlan route,
            int tileX,
            int tileZ) {
        TileKey key = new TileKey(route.stableRouteId(), tileX, tileZ);
        FutureTask<StarterRegionalRouteGeometryIndex.RouteSlice> task =
                tileTasks.computeIfAbsent(
                        key,
                        ignored -> new FutureTask<>(() -> planTile(route, tileX, tileZ)));
        task.run();
        return await(task, "route tile " + key);
    }

    private StarterRegionalRouteGeometryIndex.RouteSlice resolveEndpointConnector(
            StarterRegionalRoutePlanner.RoutePlan route,
            boolean from) {
        EndpointKey key = new EndpointKey(route.stableRouteId(), from);
        FutureTask<StarterRegionalRouteGeometryIndex.RouteSlice> task =
                connectorTasks.computeIfAbsent(
                        key,
                        ignored -> new FutureTask<>(() -> planEndpointConnector(route, from)));
        task.run();
        return await(task, "route endpoint connector " + key);
    }

    private StarterRegionalRouteGeometryIndex.RouteSlice planTile(
            StarterRegionalRoutePlanner.RoutePlan route,
            int tileX,
            int tileZ) {
        Bounds bounds = tileBounds(tileX, tileZ);
        Segment segment = clipToRect(route.from(), route.to(), bounds);
        if (segment == null || segment.from().distanceTo(segment.to()) < 1.0) {
            return emptySlice(route);
        }

        TerrainCorridorPlanner.TerrainSample terrain = boundedTerrain(bounds);
        List<TerrainCorridorPlanner.Cell> corridor = TerrainCorridorPlanner.planLocal(
                (int) Math.round(segment.from().x()),
                (int) Math.round(segment.from().z()),
                (int) Math.round(segment.to().x()),
                (int) Math.round(segment.to().z()),
                LOCAL_CELL_SIZE,
                LOCAL_MAX_NODES,
                terrain);

        boolean engineeredFallback = corridor.isEmpty();
        if (engineeredFallback) {
            corridor = straightCells(segment.from(), segment.to());
        }

        List<RouteProjectionPlanner.RoutePoint> projected =
                densify(route, corridor, bounds);
        if (projected.isEmpty()) return emptySlice(route);

        List<StarterRegionalRouteGeometryIndex.PlannedPoint> points =
                StarterRegionalRouteGeometryIndex.gradeProfile(projected, terrain);
        StarterRegionalRouteGeometryIndex.RouteSlice slice =
                new StarterRegionalRouteGeometryIndex.RouteSlice(
                        route, points, engineeredFallback);
        index(slice);
        settlementFabric.indexRoadsideForRouteWindow(
                route.stableRouteId(), points,
                bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ());
        return slice;
    }

    private StarterRegionalRouteGeometryIndex.RouteSlice planEndpointConnector(
            StarterRegionalRoutePlanner.RoutePlan route,
            boolean from) {
        long settlementId = from ? route.fromSettlementId() : route.toSettlementId();
        SimPosition authoredGate = from ? route.from() : route.to();
        SimPosition target = from ? route.to() : route.from();

        LazyStarterCivilizationFabricIndex.ResolvedSettlement resolvedSettlement =
                settlementFabric.resolveSettlement(settlementId);
        SimPosition resolvedGate = StarterRegionalRoutePlanner.endpointFor(
                resolvedSettlement.realm(),
                resolvedSettlement.strategic(),
                target);

        if (resolvedGate.distanceTo(authoredGate) < 1.5) {
            return emptySlice(route);
        }

        Bounds bounds = boundsAround(resolvedGate, authoredGate, 40);
        TerrainCorridorPlanner.TerrainSample terrain = boundedTerrain(bounds);

        // Endpoint relocation is at most a few hundred blocks and exists only to reconnect a
        // terrain-shifted gate to the authored regional corridor. Do not spend another A* budget
        // for every outgoing capital route: use a deterministic engineered connector and let the
        // grade profile/road writer turn elevation differences into bounded cuts, causeways,
        // bridge supports or tunnel clearance.
        List<TerrainCorridorPlanner.Cell> corridor =
                straightCells(resolvedGate, authoredGate);
        boolean engineeredFallback = true;

        List<RouteProjectionPlanner.RoutePoint> projected =
                densify(route, corridor, bounds);
        if (projected.isEmpty()) return emptySlice(route);

        List<StarterRegionalRouteGeometryIndex.PlannedPoint> points =
                StarterRegionalRouteGeometryIndex.gradeProfile(projected, terrain);
        StarterRegionalRouteGeometryIndex.RouteSlice slice =
                new StarterRegionalRouteGeometryIndex.RouteSlice(
                        route, points, engineeredFallback);
        index(slice);
        settlementFabric.indexRoadsideForRouteWindow(
                route.stableRouteId(), points,
                bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ());
        return slice;
    }

    private TerrainCorridorPlanner.TerrainSample boundedTerrain(Bounds bounds) {
        return new TerrainCorridorPlanner.TerrainSample() {
            @Override
            public int height(int x, int z) {
                if (!inside(bounds, x, z)) return Integer.MIN_VALUE;
                return terrainCache.surfaceY(x, z);
            }

            @Override
            public boolean water(int x, int z) {
                return inside(bounds, x, z) && terrainCache.water(x, z);
            }

            @Override
            public boolean blocked(int x, int z) {
                return !inside(bounds, x, z);
            }
        };
    }

    private void index(StarterRegionalRouteGeometryIndex.RouteSlice slice) {
        if (slice.points().isEmpty()) return;
        int halfWidth = slice.route().rural() ? 0 : 2;
        LinkedHashSet<Long> touched = new LinkedHashSet<>();
        var accumulated = accumulatedPointsByRoute.computeIfAbsent(
                slice.route().stableRouteId(), ignored -> new ConcurrentHashMap<>());

        for (StarterRegionalRouteGeometryIndex.PlannedPoint point : slice.points()) {
            accumulated.putIfAbsent(pack(point.x(), point.z()), point);
            int minChunkX = Math.floorDiv(point.x() - halfWidth, 16);
            int maxChunkX = Math.floorDiv(point.x() + halfWidth, 16);
            int minChunkZ = Math.floorDiv(point.z() - halfWidth, 16);
            int maxChunkZ = Math.floorDiv(point.z() + halfWidth, 16);
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    touched.add(pack(cx, cz));
                }
            }
        }
        for (long key : touched) {
            byChunk.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>())
                    .addIfAbsent(slice);
        }
    }

    private static List<RouteProjectionPlanner.RoutePoint> densify(
            StarterRegionalRoutePlanner.RoutePlan route,
            List<TerrainCorridorPlanner.Cell> corridor,
            Bounds bounds) {
        if (corridor == null || corridor.isEmpty()) return List.of();

        LinkedHashMap<Long, RouteProjectionPlanner.RoutePoint> out = new LinkedHashMap<>();
        for (int i = 0; i < corridor.size() - 1; i++) {
            TerrainCorridorPlanner.Cell a = corridor.get(i);
            TerrainCorridorPlanner.Cell b = corridor.get(i + 1);
            int deltaX = b.x() - a.x();
            int deltaZ = b.z() - a.z();
            int steps = Math.max(1, Math.max(Math.abs(deltaX), Math.abs(deltaZ)));
            int dirX = Integer.compare(deltaX, 0);
            int dirZ = Integer.compare(deltaZ, 0);
            if (dirX == 0 && dirZ == 0) continue;

            for (int step = 0; step <= steps; step++) {
                double t = step / (double) steps;
                int x = (int) Math.round(a.x() + deltaX * t);
                int z = (int) Math.round(a.z() + deltaZ * t);
                if (!inside(bounds, x, z)) continue;
                long key = pack(x, z);
                out.putIfAbsent(
                        key,
                        new RouteProjectionPlanner.RoutePoint(
                                x, z, dirX, dirZ, route.mode(), route.stableRouteId()));
            }
        }

        if (corridor.size() == 1) {
            TerrainCorridorPlanner.Cell only = corridor.getFirst();
            if (inside(bounds, only.x(), only.z())) {
                int dx = Integer.compare(
                        (int) Math.round(route.to().x()),
                        (int) Math.round(route.from().x()));
                int dz = Integer.compare(
                        (int) Math.round(route.to().z()),
                        (int) Math.round(route.from().z()));
                if (dx == 0 && dz == 0) dx = 1;
                out.putIfAbsent(
                        pack(only.x(), only.z()),
                        new RouteProjectionPlanner.RoutePoint(
                                only.x(), only.z(), dx, dz,
                                route.mode(), route.stableRouteId()));
            }
        }
        return List.copyOf(out.values());
    }

    private static List<TerrainCorridorPlanner.Cell> straightCells(
            SimPosition from,
            SimPosition to) {
        int fx = (int) Math.round(from.x());
        int fz = (int) Math.round(from.z());
        int tx = (int) Math.round(to.x());
        int tz = (int) Math.round(to.z());
        int steps = Math.max(1, Math.max(Math.abs(tx - fx), Math.abs(tz - fz)));
        List<TerrainCorridorPlanner.Cell> out = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            out.add(new TerrainCorridorPlanner.Cell(
                    (int) Math.round(fx + (tx - fx) * t),
                    (int) Math.round(fz + (tz - fz) * t)));
        }
        return List.copyOf(out);
    }

    private static Segment clipToRect(
            SimPosition from,
            SimPosition to,
            Bounds bounds) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        double[] t = {0.0, 1.0};

        if (!clip(-dx, from.x() - bounds.minX(), t)
                || !clip(dx, bounds.maxX() - from.x(), t)
                || !clip(-dz, from.z() - bounds.minZ(), t)
                || !clip(dz, bounds.maxZ() - from.z(), t)) {
            return null;
        }
        if (t[1] < t[0]) return null;

        SimPosition a = from.lerp(to, t[0]);
        SimPosition b = from.lerp(to, t[1]);
        return a.distanceTo(b) < 0.5 ? null : new Segment(a, b);
    }

    private static boolean clip(double p, double q, double[] t) {
        if (Math.abs(p) < 1.0e-12) return q >= 0.0;
        double r = q / p;
        if (p < 0.0) {
            if (r > t[1]) return false;
            if (r > t[0]) t[0] = r;
        } else {
            if (r < t[0]) return false;
            if (r < t[1]) t[1] = r;
        }
        return true;
    }

    private static Bounds tileBounds(int tileX, int tileZ) {
        int minX = tileX * TILE_BLOCKS;
        int minZ = tileZ * TILE_BLOCKS;
        return new Bounds(
                minX, minZ,
                minX + TILE_BLOCKS - 1,
                minZ + TILE_BLOCKS - 1);
    }

    private static Bounds boundsAround(
            SimPosition a,
            SimPosition b,
            int margin) {
        int minX = (int) Math.floor(Math.min(a.x(), b.x())) - margin;
        int minZ = (int) Math.floor(Math.min(a.z(), b.z())) - margin;
        int maxX = (int) Math.ceil(Math.max(a.x(), b.x())) + margin;
        int maxZ = (int) Math.ceil(Math.max(a.z(), b.z())) + margin;
        return new Bounds(minX, minZ, maxX, maxZ);
    }

    private static boolean inside(Bounds bounds, int x, int z) {
        return x >= bounds.minX() && x <= bounds.maxX()
                && z >= bounds.minZ() && z <= bounds.maxZ();
    }

    private static boolean nearPoint(
            double x,
            double z,
            SimPosition point,
            double radius) {
        double dx = x - point.x();
        double dz = z - point.z();
        return dx * dx + dz * dz <= radius * radius;
    }

    private static StarterRegionalRouteGeometryIndex.RouteSlice emptySlice(
            StarterRegionalRoutePlanner.RoutePlan route) {
        return new StarterRegionalRouteGeometryIndex.RouteSlice(
                route, List.of(), false);
    }

    private static StarterRegionalRouteGeometryIndex.RouteSlice await(
            FutureTask<StarterRegionalRouteGeometryIndex.RouteSlice> task,
            String what) {
        try {
            return task.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while preparing " + what, interrupted);
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause() == null ? failed : failed.getCause();
            throw new IllegalStateException("Failed to prepare " + what, cause);
        }
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
