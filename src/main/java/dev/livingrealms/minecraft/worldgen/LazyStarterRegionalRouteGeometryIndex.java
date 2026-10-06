package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.worldgen.StarterRegionalRoutePlanner;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import net.minecraft.server.level.ServerLevel;

/**
 * Lazy exact-terrain geometry for starter regional routes.
 *
 * <p>Route topology is known at level load, but full terrain A* is not. A route is planned the
 * first time worldgen reaches a chunk near its authored corridor or one of its endpoint
 * settlements. The resulting full route is then indexed by exact chunk and reused forever.</p>
 */
public final class LazyStarterRegionalRouteGeometryIndex {
    private static final double ACTIVATION_DISTANCE_BLOCKS = 96.0;

    private final ServerLevel level;
    private final List<StarterRegionalRoutePlanner.RoutePlan> routes;
    private final Map<Long, StarterRegionalRoutePlanner.RoutePlan> routeById;
    private final LazyStarterCivilizationFabricIndex settlementFabric;
    private final StarterGeneratorTerrainCache terrainCache;

    private final ConcurrentHashMap<Long, FutureTask<StarterRegionalRouteGeometryIndex.RouteSlice>>
            routeTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<StarterRegionalRouteGeometryIndex.RouteSlice>>
            byChunk = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<StarterRegionalRouteGeometryIndex.PlannedPoint>>
            pointsByRoute = new ConcurrentHashMap<>();

    public LazyStarterRegionalRouteGeometryIndex(
            ServerLevel level,
            List<StarterRegionalRoutePlanner.RoutePlan> routes,
            LazyStarterCivilizationFabricIndex settlementFabric,
            StarterGeneratorTerrainCache terrainCache) {
        this.level = Objects.requireNonNull(level, "level");
        this.routes = List.copyOf(Objects.requireNonNull(routes, "routes"));
        this.settlementFabric = Objects.requireNonNull(settlementFabric, "settlementFabric");
        this.terrainCache = Objects.requireNonNull(terrainCache, "terrainCache");

        Map<Long, StarterRegionalRoutePlanner.RoutePlan> mutable = new java.util.HashMap<>();
        for (StarterRegionalRoutePlanner.RoutePlan route : routes) {
            mutable.put(route.stableRouteId(), route);
        }
        this.routeById = Map.copyOf(mutable);
    }

    public StarterRegionalRouteGeometryIndex.ChunkSlice query(int chunkX, int chunkZ) {
        long key = pack(chunkX, chunkZ);

        // Resolve only routes whose authored corridor is actually near this generation window.
        // Once a route is resolved, exact detours are indexed directly and no proximity guess is
        // used for its subsequent chunks.
        for (StarterRegionalRoutePlanner.RoutePlan route : routes) {
            FutureTask<StarterRegionalRouteGeometryIndex.RouteSlice> existing =
                    routeTasks.get(route.stableRouteId());
            if (existing != null && existing.isDone()) continue;
            if (nearAuthoredCorridor(route, chunkX, chunkZ)) {
                resolveRoute(route.stableRouteId());
            }
        }

        CopyOnWriteArrayList<StarterRegionalRouteGeometryIndex.RouteSlice> found = byChunk.get(key);
        if (found == null || found.isEmpty()) {
            return StarterRegionalRouteGeometryIndex.ChunkSlice.EMPTY;
        }
        return new StarterRegionalRouteGeometryIndex.ChunkSlice(
                List.copyOf(new LinkedHashSet<>(found)));
    }

    public List<StarterRegionalRouteGeometryIndex.PlannedPoint> pointsForRoute(long routeId) {
        resolveRoute(routeId);
        return pointsByRoute.getOrDefault(routeId, List.of());
    }

    public int resolvedRouteCount() {
        int count = 0;
        for (FutureTask<StarterRegionalRouteGeometryIndex.RouteSlice> task : routeTasks.values()) {
            if (task.isDone()) count++;
        }
        return count;
    }

    public int totalRouteCount() {
        return routes.size();
    }

    private StarterRegionalRouteGeometryIndex.RouteSlice resolveRoute(long routeId) {
        StarterRegionalRoutePlanner.RoutePlan route = routeById.get(routeId);
        if (route == null) throw new IllegalArgumentException("unknown starter route " + routeId);

        FutureTask<StarterRegionalRouteGeometryIndex.RouteSlice> task =
                routeTasks.computeIfAbsent(routeId,
                        ignored -> new FutureTask<>(() -> planAndIndex(route)));
        task.run();
        try {
            return task.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while resolving starter route " + routeId, interrupted);
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause() == null ? failed : failed.getCause();
            throw new IllegalStateException(
                    "Failed to resolve starter route " + routeId, cause);
        }
    }

    private StarterRegionalRouteGeometryIndex.RouteSlice planAndIndex(
            StarterRegionalRoutePlanner.RoutePlan authored) {
        LazyStarterCivilizationFabricIndex.ResolvedSettlement fromSettlement =
                settlementFabric.resolveSettlement(authored.fromSettlementId());
        LazyStarterCivilizationFabricIndex.ResolvedSettlement toSettlement =
                settlementFabric.resolveSettlement(authored.toSettlementId());

        SimPosition from = StarterRegionalRoutePlanner.endpointFor(
                fromSettlement.realm(),
                fromSettlement.strategic(),
                toSettlement.strategic().position());
        SimPosition to = StarterRegionalRoutePlanner.endpointFor(
                toSettlement.realm(),
                toSettlement.strategic(),
                fromSettlement.strategic().position());

        StarterRegionalRoutePlanner.RoutePlan resolvedRoute =
                new StarterRegionalRoutePlanner.RoutePlan(
                        authored.stableRouteId(),
                        authored.stableKey(),
                        authored.factionId(),
                        authored.fromSettlementId(),
                        authored.toSettlementId(),
                        authored.relation(),
                        from,
                        to,
                        authored.rural(),
                        authored.mode());

        StarterRegionalRouteGeometryIndex.RouteSlice slice =
                StarterRegionalRouteGeometryIndex.planOne(
                        level, resolvedRoute, terrainCache);
        pointsByRoute.put(resolvedRoute.stableRouteId(), slice.points());
        index(slice);
        settlementFabric.indexRoadsideForResolvedRoute(slice);
        return slice;
    }

    private void index(StarterRegionalRouteGeometryIndex.RouteSlice slice) {
        int halfWidth = slice.route().rural() ? 0 : 2;
        LinkedHashSet<Long> touched = new LinkedHashSet<>();
        for (StarterRegionalRouteGeometryIndex.PlannedPoint point : slice.points()) {
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

    private static boolean nearAuthoredCorridor(
            StarterRegionalRoutePlanner.RoutePlan route,
            int chunkX,
            int chunkZ) {
        double px = (chunkX << 4) + 7.5;
        double pz = (chunkZ << 4) + 7.5;
        double ax = route.from().x();
        double az = route.from().z();
        double bx = route.to().x();
        double bz = route.to().z();

        double vx = bx - ax;
        double vz = bz - az;
        double len2 = vx * vx + vz * vz;
        double t = len2 <= 1.0e-9
                ? 0.0
                : ((px - ax) * vx + (pz - az) * vz) / len2;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = ax + vx * t;
        double cz = az + vz * t;
        double dx = px - cx;
        double dz = pz - cz;
        double threshold = ACTIVATION_DISTANCE_BLOCKS + 12.0;
        return dx * dx + dz * dz <= threshold * threshold;
    }

    private static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }
}
