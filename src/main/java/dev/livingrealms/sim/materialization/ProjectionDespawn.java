package dev.livingrealms.sim.materialization;

import java.util.Objects;

/** Physical entity that should be dematerialized. This is not an ecological death. */
public record ProjectionDespawn(String entityKey, long populationGroupId, int slot, ProjectionRemovalReason reason) {
    public ProjectionDespawn {
        if (entityKey == null || entityKey.isBlank()) throw new IllegalArgumentException("entityKey");
        if (populationGroupId <= 0) throw new IllegalArgumentException("populationGroupId");
        if (slot < 0) throw new IllegalArgumentException("slot");
        reason = Objects.requireNonNull(reason, "reason");
    }
}
