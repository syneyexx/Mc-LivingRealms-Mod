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
        int priority,
        String parcelId,
        int parcelWidth,
        int parcelDepth
) {
    public ConstructionIntent {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key");
        if (factionId <= 0 || settlementId <= 0) throw new IllegalArgumentException("owner ids");
        role = Objects.requireNonNull(role, "role");
        center = Objects.requireNonNull(center, "center");
        if (width <= 0 || depth <= 0) throw new IllegalArgumentException("footprint");
        rotationQuarterTurns = Math.floorMod(rotationQuarterTurns, 4);
        parcelId = parcelId == null ? "" : parcelId;
        if (hasParcelRaw(parcelId) && (parcelWidth <= 0 || parcelDepth <= 0)) {
            throw new IllegalArgumentException("parcel footprint");
        }
        if (!hasParcelRaw(parcelId)) {
            parcelWidth = 0;
            parcelDepth = 0;
        }
    }

    public ConstructionIntent(
            String key, long factionId, long settlementId, StructureRole role, SimPosition center,
            int width, int depth, int rotationQuarterTurns, int priority
    ) {
        this(key, factionId, settlementId, role, center, width, depth, rotationQuarterTurns, priority, "", 0, 0);
    }

    public boolean hasParcel() { return !parcelId.isBlank(); }

    public ConstructionIntent withCenter(SimPosition newCenter) {
        return new ConstructionIntent(key, factionId, settlementId, role, newCenter, width, depth,
                rotationQuarterTurns, priority, parcelId, parcelWidth, parcelDepth);
    }

    public ConstructionIntent withPriority(int newPriority) {
        return new ConstructionIntent(key, factionId, settlementId, role, center, width, depth,
                rotationQuarterTurns, newPriority, parcelId, parcelWidth, parcelDepth);
    }

    private static boolean hasParcelRaw(String id) { return id != null && !id.isBlank(); }
}
