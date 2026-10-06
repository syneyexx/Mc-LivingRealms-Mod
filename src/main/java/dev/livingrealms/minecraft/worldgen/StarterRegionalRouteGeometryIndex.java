package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.sim.transport.RouteProjectionPlanner;
import dev.livingrealms.sim.transport.TerrainCorridorPlanner;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.worldgen.StarterRegionalRoutePlanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Immutable actual-terrain geometry for deterministic starter regional routes.
 *
 * <p>Built once from the active chunk generator without requesting chunks. Generation workers only
 * query the precomputed current-chunk slice, so route geometry cannot depend on chunk order.</p>
 */
public final class StarterRegionalRouteGeometryIndex {
    public record PlannedPoint(int x, int z, int dx, int dz, int deckY, boolean water) {}

    public record RouteSlice(
            StarterRegionalRoutePlanner.RoutePlan route,
            List<PlannedPoint> points,
            boolean engineeredFallback
    ) {
        public RouteSlice {
            route = Objects.requireNonNull(route, "route");
            points = List.copyOf(Objects.requireNonNull(points, "points"));
        }
    }

    public record ChunkSlice(List<RouteSlice> routes) {
        public static final ChunkSlice EMPTY = new ChunkSlice(List.of());

        public ChunkSlice {
            routes = List.copyOf(Objects.requireNonNull(routes, "routes"));
        }

        public boolean isEmpty() { return routes.isEmpty(); }
    }

    private record PlannedRoute(
            StarterRegionalRoutePlanner.RoutePlan route,
            List<PlannedPoint> points,
            boolean engineeredFallback
    ) {}

    private static final class MutableRouteSlice {
        final StarterRegionalRoutePlanner.RoutePlan route;
        final boolean engineeredFallback;
        final LinkedHashMap<Long, PlannedPoint> points = new LinkedHashMap<>();

        MutableRouteSlice(StarterRegionalRoutePlanner.RoutePlan route, boolean engineeredFallback) {
            this.route = route;
            this.engineeredFallback = engineeredFallback;
        }

        void add(PlannedPoint point) {
            long key = ((long) point.x() << 32) ^ (point.z() & 0xffffffffL);
            points.putIfAbsent(key, point);
        }

        RouteSlice freeze() {
            return new RouteSlice(route, List.copyOf(points.values()), engineeredFallback);
        }
    }

    private final Map<Long, ChunkSlice> byChunk;
    private final Map<Long, List<PlannedPoint>> pointsByRoute;
    private final int plannedRouteCount;
    private final int unresolvedRouteCount;

    private StarterRegionalRouteGeometryIndex(
            Map<Long, ChunkSlice> byChunk,
            Map<Long, List<PlannedPoint>> pointsByRoute,
            int plannedRouteCount,
            int unresolvedRouteCount) {
        this.byChunk = Map.copyOf(byChunk);
        this.pointsByRoute = Map.copyOf(pointsByRoute);
        this.plannedRouteCount = plannedRouteCount;
        this.unresolvedRouteCount = unresolvedRouteCount;
    }

    public static StarterRegionalRouteGeometryIndex build(
            ServerLevel level,
            List<StarterRegionalRoutePlanner.RoutePlan> routes) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(routes, "routes");
        if (routes.isEmpty()) {
            return new StarterRegionalRouteGeometryIndex(Map.of(), Map.of(), 0, 0);
        }

        int workers = routePlanningWorkers(routes.size());
        List<PlannedRoute> plannedRoutes = workers <= 1
                ? planRoutesSequential(level, routes)
                : planRoutesParallel(level, routes, workers);

        // Merge in the original route order. Route calculations are independent, so parallel
        // execution changes only wall-clock time; chunk slice ordering and deterministic geometry
        // remain identical to the single-threaded implementation.
        Map<Long, LinkedHashMap<Long, MutableRouteSlice>> mutable = new HashMap<>();
        Map<Long, List<PlannedPoint>> fullRoutes = new HashMap<>();
        int unresolved = 0;
        for (PlannedRoute planned : plannedRoutes) {
            if (planned.engineeredFallback()) unresolved++;
            if (planned.points().isEmpty()) continue;

            StarterRegionalRoutePlanner.RoutePlan route = planned.route();
            fullRoutes.put(route.stableRouteId(), planned.points());
            int halfWidth = route.rural() ? 0 : 2;
            for (PlannedPoint point : planned.points()) {
                int minChunkX = Math.floorDiv(point.x() - halfWidth, 16);
                int maxChunkX = Math.floorDiv(point.x() + halfWidth, 16);
                int minChunkZ = Math.floorDiv(point.z() - halfWidth, 16);
                int maxChunkZ = Math.floorDiv(point.z() + halfWidth, 16);
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                        long chunkKey = pack(cx, cz);
                        var byRoute = mutable.computeIfAbsent(
                                chunkKey, ignored -> new LinkedHashMap<>());
                        byRoute.computeIfAbsent(
                                route.stableRouteId(),
                                ignored -> new MutableRouteSlice(
                                        route, planned.engineeredFallback())).add(point);
                    }
                }
            }
        }

        Map<Long, ChunkSlice> frozen = new HashMap<>(mutable.size() * 2);
        for (var entry : mutable.entrySet()) {
            List<RouteSlice> slices = new ArrayList<>(entry.getValue().size());
            for (MutableRouteSlice route : entry.getValue().values()) slices.add(route.freeze());
            frozen.put(entry.getKey(), new ChunkSlice(slices));
        }
        return new StarterRegionalRouteGeometryIndex(
                frozen, fullRoutes, routes.size(), unresolved);
    }

    static int routePlanningWorkers(int routeCount) {
        if (routeCount <= 1) return 1;
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        // Keep one hardware thread free for JVM/Minecraft housekeeping and cap startup fan-out so
        // large servers do not create hundreds of short-lived planning threads.
        return Math.max(1, Math.min(routeCount, Math.min(12, processors - 1)));
    }

    private static List<PlannedRoute> planRoutesSequential(
            ServerLevel level,
            List<StarterRegionalRoutePlanner.RoutePlan> routes) {
        List<PlannedRoute> planned = new ArrayList<>(routes.size());
        for (StarterRegionalRoutePlanner.RoutePlan route : routes) {
            planned.add(planRoute(level, route));
        }
        return List.copyOf(planned);
    }

    private static List<PlannedRoute> planRoutesParallel(
            ServerLevel level,
            List<StarterRegionalRoutePlanner.RoutePlan> routes,
            int workers) {
        ExecutorService executor = Executors.newFixedThreadPool(workers, runnable -> {
            Thread thread = new Thread(runnable, "LivingRealms-Route-Precompute");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<PlannedRoute>> futures = new ArrayList<>(routes.size());
            for (StarterRegionalRoutePlanner.RoutePlan route : routes) {
                futures.add(executor.submit(() -> planRoute(level, route)));
            }

            // Consume futures in submission order so downstream map/chunk ordering is stable even
            // though the expensive terrain searches complete in arbitrary order.
            List<PlannedRoute> planned = new ArrayList<>(routes.size());
            for (Future<PlannedRoute> future : futures) {
                try {
                    planned.add(future.get());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Interrupted while preparing starter regional routes", interrupted);
                } catch (ExecutionException failed) {
                    Throwable cause = failed.getCause() == null ? failed : failed.getCause();
                    throw new IllegalStateException(
                            "Failed to prepare starter regional route geometry", cause);
                }
            }
            return List.copyOf(planned);
        } finally {
            executor.shutdownNow();
        }
    }

    private static PlannedRoute planRoute(
            ServerLevel level,
            StarterRegionalRoutePlanner.RoutePlan route) {
        var chunkSource = level.getChunkSource();
        var generator = chunkSource.getGenerator();
        var randomState = chunkSource.randomState();

        // Route-local caches avoid cross-thread synchronization. Generator base-height sampling is
        // the same immutable operation Minecraft worldgen workers use concurrently.
        Map<Long, Integer> surfaceCache = new HashMap<>();
        Map<Long, Integer> floorCache = new HashMap<>();
        TerrainCorridorPlanner.TerrainSample terrain = new TerrainCorridorPlanner.TerrainSample() {
            @Override
            public int height(int x, int z) {
                long key = pack(x, z);
                return surfaceCache.computeIfAbsent(key, ignored ->
                        generator.getBaseHeight(
                                x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState) - 1);
            }

            @Override
            public boolean water(int x, int z) {
                long key = pack(x, z);
                int surface = height(x, z);
                int floor = floorCache.computeIfAbsent(key, ignored ->
                        generator.getBaseHeight(
                                x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState) - 1);
                return surface > floor;
            }

            @Override
            public boolean blocked(int x, int z) {
                // Registered structure pieces are protected later by WorldgenFabricBlockWriter.
                // Keeping this sampler generator-only is what makes the corridor order-independent.
                return false;
            }
        };

        double distance = route.from().distanceTo(route.to());
        if (distance < 1.0) return new PlannedRoute(route, List.of(), false);

        SimPosition observer = route.from().lerp(route.to(), 0.5);
        int maxPoints = Math.min(
                16_384, Math.max(512, (int) Math.ceil(distance * 2.0) + 512));
        List<RouteProjectionPlanner.RoutePoint> projected = RouteProjectionPlanner.plan(
                route.asTransportRoute(),
                route.from(),
                route.to(),
                List.of(observer),
                distance + 1024.0,
                maxPoints,
                terrain);

        boolean engineeredFallback = false;
        if (projected.isEmpty()) {
            // Terrain search exhausted its bounded pass/waypoint hierarchy. Fall back to a
            // deterministic engineered alignment, not a raw surface road: the grade profile
            // below turns peaks into cuts/tunnels and valleys/water into supported deck.
            engineeredFallback = true;
            projected = RouteProjectionPlanner.plan(
                    route.asTransportRoute(),
                    route.from(),
                    route.to(),
                    List.of(observer),
                    distance + 1024.0,
                    maxPoints);
        }
        if (projected.isEmpty()) {
            return new PlannedRoute(route, List.of(), engineeredFallback);
        }
        return new PlannedRoute(
                route, gradeProfile(projected, terrain), engineeredFallback);
    }

    private static List<PlannedPoint> gradeProfile(
            List<RouteProjectionPlanner.RoutePoint> projected,
            TerrainCorridorPlanner.TerrainSample terrain) {
        int size = projected.size();
        int[] raw = new int[size];
        int[] deck = new int[size];
        boolean[] water = new boolean[size];
        for (int i = 0; i < size; i++) {
            var point = projected.get(i);
            raw[i] = terrain.height(point.x(), point.z());
            deck[i] = raw[i];
            water[i] = terrain.water(point.x(), point.z());
        }

        // Preserve endpoints at their gate terrain. Repeated forward/backward relaxation keeps
        // every interior step within one block while remaining as close as practical to terrain.
        for (int pass = 0; pass < 6; pass++) {
            for (int i = 1; i < size - 1; i++) {
                deck[i] = clamp(deck[i], deck[i - 1] - 1, deck[i - 1] + 1);
                if (water[i]) deck[i] = Math.max(deck[i], raw[i]);
            }
            for (int i = size - 2; i > 0; i--) {
                deck[i] = clamp(deck[i], deck[i + 1] - 1, deck[i + 1] + 1);
                if (water[i]) deck[i] = Math.max(deck[i], raw[i]);
            }
        }

        List<PlannedPoint> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            var point = projected.get(i);
            out.add(new PlannedPoint(
                    point.x(), point.z(), point.dx(), point.dz(), deck[i], water[i]));
        }
        return List.copyOf(out);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public ChunkSlice query(int chunkX, int chunkZ) {
        return byChunk.getOrDefault(pack(chunkX, chunkZ), ChunkSlice.EMPTY);
    }

    public List<PlannedPoint> pointsForRoute(long routeId) {
        return pointsByRoute.getOrDefault(routeId, List.of());
    }

    public int indexedChunkCount() { return byChunk.size(); }
    public int plannedRouteCount() { return plannedRouteCount; }
    public int unresolvedRouteCount() { return unresolvedRouteCount; }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
