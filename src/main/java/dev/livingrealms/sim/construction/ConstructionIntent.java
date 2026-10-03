package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.world.SimPosition;
import java.util.Objects;

/** Deterministic desired physical structure for a settlement. */
public record ConstructionIntent(
        String key,
        long factionId,
        long settlementId,
        StructureRole role,
        SimPosition center,
        int width,
        int depth,
        int rotationQuarterTurns,
        int priority
) {
    public ConstructionIntent {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key");
        if (factionId <= 0 || settlementId <= 0) throw new IllegalArgumentException("owner ids");
        role = Objects.requireNonNull(role, "role");
        center = Objects.requireNonNull(center, "center");
        if (width <= 0 || depth <= 0) throw new IllegalArgumentException("footprint");
        rotationQuarterTurns = Math.floorMod(rotationQuarterTurns, 4);
    }
}
