package dev.livingrealms.sim.worldgen;

import dev.livingrealms.sim.world.RoadsideSite;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Pure deterministic day-zero roadside anchors for starter regional routes. */
public final class StarterRoadsideSitePlanner {
    public static final long STARTER_ROADSIDE_SITE_ID_BASE = 3_000_000L;
    private static final int IDS_PER_ROUTE = 32;
    public static final double MIN_SITE_SPACING = 160.0;
    public static final double TARGET_CORRIDOR_SPACING = 380.0;
    public static final int MAX_CORRIDOR_ANCHORS_PER_ROUTE = 16;
    private static final RoadsideSite.Type[] CORRIDOR_TYPES = {
            RoadsideSite.Type.WAYSTATION,
            RoadsideSite.Type.MILESTONE,
            RoadsideSite.Type.SHRINE,
            RoadsideSite.Type.TRAVELER_CAMP,
            RoadsideSite.Type.TOLL_POST
    };

    public record SitePlan(
            long stableSiteId,
            String stableKey,
            RoadsideSite.Type type,
            SimPosition position,
            long relatedSettlementId,
            long relatedRouteId
    ) {
        public SitePlan {
            if (stableSiteId <= 0 || relatedSettlementId <= 0 || relatedRouteId <= 0) {
                throw new IllegalArgumentException("site ids");
            }
            if (stableKey == null || stableKey.isBlank()) throw new IllegalArgumentException("stableKey");
            type = Objects.requireNonNull(type, "type");
            position = Objects.requireNonNull(position, "position");
        }

        public RoadsideSite asRoadsideSite() {
            return new RoadsideSite(
                    stableSiteId, type, position, RoadsideSite.defaultName(type, position),
                    relatedSettlementId, relatedRouteId, 0);
        }
    }

    private StarterRoadsideSitePlanner() {}

    public static List<SitePlan> plan(StarterCivilizationLayoutPlanner.Layout layout) {
        Objects.requireNonNull(layout, "layout");
        List<StarterRegionalRoutePlanner.RoutePlan> routes =
                new ArrayList<>(StarterRegionalRoutePlanner.plan(layout));
        routes.sort(Comparator.comparingLong(StarterRegionalRoutePlanner.RoutePlan::stableRouteId));

        List<SitePlan> out = new ArrayList<>();
        for (StarterRegionalRoutePlanner.RoutePlan route : routes) {
            double distance = route.from().distanceTo(route.to());
            if (distance <= 450.0) continue;

            int segments = Math.max(2, (int) Math.ceil(distance / TARGET_CORRIDOR_SPACING));
            int anchors = Math.min(MAX_CORRIDOR_ANCHORS_PER_ROUTE, segments - 1);
            for (int slot = 1; slot <= anchors; slot++) {
                double t = slot / (double) (anchors + 1);
                SimPosition position = route.from().lerp(route.to(), t);
                if (tooClose(out, position)) continue;

                RoadsideSite.Type type = corridorType(
                        layout.worldSeed(), route.stableRouteId(), slot);
                long routeOrdinal = route.stableRouteId()
                        - StarterRegionalRoutePlanner.STARTER_ROUTE_ID_BASE;
                if (routeOrdinal < 0) throw new IllegalStateException("starter route id");
                long siteId = STARTER_ROADSIDE_SITE_ID_BASE
                        + routeOrdinal * IDS_PER_ROUTE + slot;
                long settlementId = t <= .5
                        ? route.fromSettlementId() : route.toSettlementId();
                out.add(new SitePlan(
                        siteId,
                        route.stableKey() + "/roadside:" + slot,
                        type,
                        position,
                        settlementId,
                        route.stableRouteId()));
            }
        }
        return List.copyOf(out);
    }

    public static boolean isStarterRoadsideSiteId(long siteId) {
        long max = STARTER_ROADSIDE_SITE_ID_BASE + 12L * 100L * IDS_PER_ROUTE;
        return siteId >= STARTER_ROADSIDE_SITE_ID_BASE && siteId < max;
    }

    private static RoadsideSite.Type corridorType(long seed, long routeId, int slot) {
        int index = Math.floorMod(Long.hashCode(
                seed ^ routeId * 0x9E3779B97F4A7C15L ^ slot * 0xD1B54A32D192ED03L),
                CORRIDOR_TYPES.length);
        return CORRIDOR_TYPES[index];
    }

    private static boolean tooClose(List<SitePlan> sites, SimPosition position) {
        for (SitePlan site : sites) {
            if (site.position().distanceTo(position) < MIN_SITE_SPACING) return true;
        }
        return false;
    }
}
