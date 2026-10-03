package dev.livingrealms.sim.logistics.projection;

import dev.livingrealms.sim.world.SimPosition;
import java.util.Objects;

public record CaravanProjectionRequest(long shipmentId, SimPosition position, double nearestPlayerDistance) {
    public CaravanProjectionRequest {
        if (shipmentId <= 0) throw new IllegalArgumentException("shipmentId");
        Objects.requireNonNull(position, "position");
        if (!Double.isFinite(nearestPlayerDistance) || nearestPlayerDistance < 0) throw new IllegalArgumentException("nearestPlayerDistance");
    }
}
