package dev.livingrealms.sim.logistics.projection;

import dev.livingrealms.sim.world.SimPosition;
import java.util.Objects;

public record CaravanProjectionSpawn(long shipmentId, SimPosition position) {
    public CaravanProjectionSpawn {
        if (shipmentId <= 0) throw new IllegalArgumentException("shipmentId");
        Objects.requireNonNull(position, "position");
    }
}
