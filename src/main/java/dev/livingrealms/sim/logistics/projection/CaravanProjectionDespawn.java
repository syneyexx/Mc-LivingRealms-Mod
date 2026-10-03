package dev.livingrealms.sim.logistics.projection;

import java.util.Objects;

public record CaravanProjectionDespawn(String entityKey, long shipmentId, CaravanProjectionRemovalReason reason) {
    public CaravanProjectionDespawn {
        if (shipmentId <= 0) throw new IllegalArgumentException("shipmentId");
        if (Objects.requireNonNull(entityKey, "entityKey").isBlank()) throw new IllegalArgumentException("entityKey");
        Objects.requireNonNull(reason, "reason");
    }
}
