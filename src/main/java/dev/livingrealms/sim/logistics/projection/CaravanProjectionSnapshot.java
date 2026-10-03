package dev.livingrealms.sim.logistics.projection;

import java.util.Objects;

public record CaravanProjectionSnapshot(String entityKey, long shipmentId) {
    public CaravanProjectionSnapshot {
        if (shipmentId <= 0) throw new IllegalArgumentException("shipmentId");
        if (Objects.requireNonNull(entityKey, "entityKey").isBlank()) throw new IllegalArgumentException("entityKey");
    }
}
