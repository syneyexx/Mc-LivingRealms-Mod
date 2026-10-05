package dev.livingrealms.sim.world.projection;

import dev.livingrealms.sim.world.RoadsideSite;
import dev.livingrealms.sim.world.SimPosition;
import java.util.Objects;

/** Pure LOD request for a sparse roadside feature (never a settlement). */
public record RoadsideSiteProjection(long siteId, RoadsideSite.Type type, RoadsideSite.Lifecycle lifecycle,
                                     SimPosition position, double nearestPlayerDistance) {
    public RoadsideSiteProjection {
        if (siteId <= 0) throw new IllegalArgumentException("siteId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(position, "position");
        if (!Double.isFinite(nearestPlayerDistance) || nearestPlayerDistance < 0) {
            throw new IllegalArgumentException("nearestPlayerDistance");
        }
    }
}
