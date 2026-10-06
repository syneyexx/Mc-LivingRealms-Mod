package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.sim.worldgen.StarterRegionalRoutePlanner;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Immutable vertical profile for deterministic starter regional roads.
 *
 * <p>The horizontal route remains the shared pure gate-to-gate centerline. On the server thread,
 * before worker worldgen consumes the plan, this index samples the active ChunkGenerator's base
 * terrain without loading chunks. Workers then interpolate only frozen heights, making road grade
 * independent from chunk generation order.</p>
 */
public final class StarterRegionalRouteHeightIndex {
    private static final double SAMPLE_SPACING = 8.0D;
    private static final int MAX_SAMPLE_STEP = 2;

    private static final class Profile {
        private final double fromX;
        private final double fromZ;
        private final double dx;
        private final double dz;
        private final double lengthSquared;
        private final int[] heights;

        private Profile(
                double fromX,
                double fromZ,
                double toX,
                double toZ,
                int[] heights) {
            this.fromX = fromX;
            this.fromZ = fromZ;
            this.dx = toX - fromX;
            this.dz = toZ - fromZ;
            this.lengthSquared = Math.max(1.0D, dx * dx + dz * dz);
            this.heights = heights.clone();
        }

        private int deckY(int x, int z) {
            if (heights.length == 0) return Integer.MIN_VALUE;
            if (heights.length == 1) return heights[0];
            double t = ((x - fromX) * dx + (z - fromZ) * dz) / lengthSquared;
            t = Math.max(0.0D, Math.min(1.0D, t));
            double sample = t * (heights.length - 1);
            int low = Math.min(heights.length - 1, (int) Math.floor(sample));
            int high = Math.min(heights.length - 1, low + 1);
            double f = sample - low;
            return (int) Math.round(heights[low] + (heights[high] - heights[low]) * f);
        }
    }

    private final Map<Long, Profile> byRoute;

    private StarterRegionalRouteHeightIndex(Map<Long, Profile> byRoute) {
        this.byRoute = Map.copyOf(byRoute);
    }

    public static StarterRegionalRouteHeightIndex build(
            ServerLevel level,
            List<StarterRegionalRoutePlanner.RoutePlan> routes) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(routes, "routes");

        var chunkSource = level.getChunkSource();
        var generator = chunkSource.getGenerator();
        var randomState = chunkSource.randomState();
        Map<Long, Profile> profiles = new HashMap<>(Math.max(16, routes.size() * 2));

        for (StarterRegionalRoutePlanner.RoutePlan route : routes) {
            double dx = route.to().x() - route.from().x();
            double dz = route.to().z() - route.from().z();
            double distance = Math.hypot(dx, dz);
            int segments = Math.max(1, (int) Math.ceil(distance / SAMPLE_SPACING));
            int[] raw = new int[segments + 1];
            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments;
                int x = (int) Math.round(route.from().x() + dx * t);
                int z = (int) Math.round(route.from().z() + dz * t);
                raw[i] = generator.getBaseHeight(
                        x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState) - 1;
            }
            int[] smoothed = smooth(raw);
            profiles.put(route.stableRouteId(), new Profile(
                    route.from().x(), route.from().z(),
                    route.to().x(), route.to().z(),
                    smoothed));
        }
        return new StarterRegionalRouteHeightIndex(profiles);
    }

    public int deckY(long routeId, int x, int z, int fallbackY) {
        Profile profile = byRoute.get(routeId);
        if (profile == null) return fallbackY;
        int resolved = profile.deckY(x, z);
        return resolved == Integer.MIN_VALUE ? fallbackY : resolved;
    }

    public int routeCount() {
        return byRoute.size();
    }

    private static int[] smooth(int[] raw) {
        if (raw.length <= 1) return raw.clone();
        int[] out = raw.clone();

        for (int i = 1; i < out.length; i++) {
            out[i] = clamp(out[i], out[i - 1] - MAX_SAMPLE_STEP, out[i - 1] + MAX_SAMPLE_STEP);
        }
        // Anchor the destination terrain too, then propagate its feasible grade backwards.
        out[out.length - 1] = raw[raw.length - 1];
        for (int i = out.length - 2; i >= 0; i--) {
            out[i] = clamp(out[i], out[i + 1] - MAX_SAMPLE_STEP, out[i + 1] + MAX_SAMPLE_STEP);
        }
        // One final forward pass makes both constraints hold after the backward correction.
        for (int i = 1; i < out.length; i++) {
            out[i] = clamp(out[i], out[i - 1] - MAX_SAMPLE_STEP, out[i - 1] + MAX_SAMPLE_STEP);
        }
        return out;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
