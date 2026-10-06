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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Immutable actual-terrain geometry for deterministic starter regional routes.
 *
 * <p>Built once from the active chunk generator without requesting chunks. Generation workers only
 * query the precomputed current-chunk slice, so route geometry cannot depend on chunk order.</p>
 */
public final class StarterRegionalRouteGeometryIndex {
    public record RouteSlice(
            StarterRegionalRoutePlanner.RoutePlan route,
            List<RouteProjectionPlanner.RoutePoint> points
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

    private static final class MutableRouteSlice {
        final StarterRegionalRoutePlanner.RoutePlan route;
        final LinkedHashMap<Long, RouteProjectionPlanner.RoutePoint> points = new LinkedHashMap<>();

        MutableRouteSlice(StarterRegionalRoutePlanner.RoutePlan route) {
            this.route = route;
        }

        void add(RouteProjectionPlanner.RoutePoint point) {
            long key = ((long) point.x() << 32) ^ (point.z() & 0xffffffffL);
            points.putIfAbsent(key, point);
        }

        RouteSlice freeze() {
            return new RouteSlice(route, List.copyOf(points.values()));
        }
    }

    private final Map<Long, ChunkSlice> byChunk;
    private final int plannedRouteCount;
    private final int unresolvedRouteCount;

    private StarterRegionalRouteGeometryIndex(
            Map<Long, ChunkSlice> byChunk,
            int plannedRouteCount,
            int unresolvedRouteCount) {
        this.byChunk = Map.copyOf(byChunk);
        this.plannedRouteCount = plannedRouteCount;
        this.unresolvedRouteCount = unresolvedRouteCount;
    }

    public static StarterRegionalRouteGeometryIndex build(
            ServerLevel level,
            List<StarterRegionalRoutePlanner.RoutePlan> routes) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(routes, "routes");

        var chunkSource = level.getChunkSource();
        var generator = chunkSource.getGenerator();
        var randomState = chunkSource.randomState();
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

        Map<Long, LinkedHashMap<Long, MutableRouteSlice>> mutable = new HashMap<>();
        int unresolved = 0;
        for (StarterRegionalRoutePlanner.RoutePlan route : routes) {
            double distance = route.from().distanceTo(route.to());
            if (distance < 1.0) continue;
            SimPosition observer = route.from().lerp(route.to(), 0.5);
            int maxPoints = Math.min(16_384, Math.max(512, (int) Math.ceil(distance * 2.0) + 512));
            List<RouteProjectionPlanner.RoutePoint> points = RouteProjectionPlanner.plan(
                    route.asTransportRoute(),
                    route.from(),
                    route.to(),
                    List.of(observer),
                    distance + 1024.0,
                    maxPoints,
                    terrain);
            if (points.isEmpty()) {
                unresolved++;
                continue;
            }

            int halfWidth = route.rural() ? 0 : 2;
            for (RouteProjectionPlanner.RoutePoint point : points) {
                int minChunkX = Math.floorDiv(point.x() - halfWidth, 16);
                int maxChunkX = Math.floorDiv(point.x() + halfWidth, 16);
                int minChunkZ = Math.floorDiv(point.z() - halfWidth, 16);
                int maxChunkZ = Math.floorDiv(point.z() + halfWidth, 16);
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                        long chunkKey = pack(cx, cz);
                        var byRoute = mutable.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>());
                        byRoute.computeIfAbsent(
                                route.stableRouteId(), ignored -> new MutableRouteSlice(route)).add(point);
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
        return new StarterRegionalRouteGeometryIndex(frozen, routes.size(), unresolved);
    }

    public ChunkSlice query(int chunkX, int chunkZ) {
        return byChunk.getOrDefault(pack(chunkX, chunkZ), ChunkSlice.EMPTY);
    }

    public int indexedChunkCount() { return byChunk.size(); }
    public int plannedRouteCount() { return plannedRouteCount; }
    public int unresolvedRouteCount() { return unresolvedRouteCount; }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
