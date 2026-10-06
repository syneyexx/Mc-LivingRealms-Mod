package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.worldgen.StarterRoadsideSitePlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Aligns pure starter roadside anchors to the resolved terrain-aware regional route geometry. */
public final class StarterRoadsideRouteResolver {
    private StarterRoadsideRouteResolver() {}

    public static List<StarterRoadsideSitePlanner.SitePlan> resolve(
            List<StarterRoadsideSitePlanner.SitePlan> sites,
            StarterRegionalRouteGeometryIndex geometry) {
        Objects.requireNonNull(sites, "sites");
        Objects.requireNonNull(geometry, "geometry");
        List<StarterRoadsideSitePlanner.SitePlan> out = new ArrayList<>(sites.size());

        for (StarterRoadsideSitePlanner.SitePlan site : sites) {
            var points = geometry.pointsForRoute(site.relatedRouteId());
            if (points.isEmpty()) {
                out.add(site);
                continue;
            }

            StarterRegionalRouteGeometryIndex.PlannedPoint nearest = null;
            double best = Double.POSITIVE_INFINITY;
            for (var point : points) {
                double dx = site.position().x() - point.x();
                double dz = site.position().z() - point.z();
                double d2 = dx * dx + dz * dz;
                if (d2 < best) {
                    best = d2;
                    nearest = point;
                }
            }
            if (nearest == null) {
                out.add(site);
                continue;
            }

            int x = nearest.x();
            int z = nearest.z();
            if (!nearest.water()) {
                int nx = nearest.dz() == 0 ? 0 : Integer.signum(nearest.dz());
                int nz = nearest.dx() == 0 ? 0 : -Integer.signum(nearest.dx());
                if (nx == 0 && nz == 0) nx = 1;
                int side = (site.stableSiteId() & 1L) == 0L ? 1 : -1;
                x += nx * 4 * side;
                z += nz * 4 * side;
            }

            out.add(new StarterRoadsideSitePlanner.SitePlan(
                    site.stableSiteId(),
                    site.stableKey(),
                    site.type(),
                    new SimPosition(x, z),
                    site.relatedSettlementId(),
                    site.relatedRouteId()));
        }
        return List.copyOf(out);
    }
}
