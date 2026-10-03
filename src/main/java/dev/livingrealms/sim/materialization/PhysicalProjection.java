package dev.livingrealms.sim.materialization;

import java.util.Objects;

/** Snapshot of one physical Minecraft entity representing a slot in an aggregate population cohort. */
public record PhysicalProjection(
        String entityKey,
        long populationGroupId,
        String speciesId,
        int slot
) {
    public PhysicalProjection {
        if (entityKey == null || entityKey.isBlank()) throw new IllegalArgumentException("entityKey");
        if (populationGroupId <= 0) throw new IllegalArgumentException("populationGroupId");
        speciesId = Objects.requireNonNull(speciesId, "speciesId");
        if (speciesId.isBlank()) throw new IllegalArgumentException("speciesId");
        if (slot < 0) throw new IllegalArgumentException("slot");
    }
}
