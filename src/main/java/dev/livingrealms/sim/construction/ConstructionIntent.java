package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.world.SimPosition;
import java.util.List;
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
        int parcelDepth,
        List<SimPosition> path
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
        path = path == null ? List.of() : List.copyOf(path);
        if (!path.isEmpty() && role != StructureRole.ROAD) {
            throw new IllegalArgumentException("polyline path is only valid for ROAD intents");
        }
    }

    /** Backward-compatible constructor for parcel-bound/non-polyline intents. */
    public ConstructionIntent(
            String key, long factionId, long settlementId, StructureRole role, SimPosition center,
            int width, int depth, int rotationQuarterTurns, int priority,
            String parcelId, int parcelWidth, int parcelDepth
    ) {
        this(key, factionId, settlementId, role, center, width, depth, rotationQuarterTurns, priority,
                parcelId, parcelWidth, parcelDepth, List.of());
    }

    /** Backward-compatible constructor for ordinary intents. */
    public ConstructionIntent(
            String key, long factionId, long settlementId, StructureRole role, SimPosition center,
            int width, int depth, int rotationQuarterTurns, int priority
    ) {
        this(key, factionId, settlementId, role, center, width, depth, rotationQuarterTurns, priority,
                "", 0, 0, List.of());
    }

    /** Road-intent constructor carrying a graph-authored world-space centerline. */
    public ConstructionIntent(
            String key, long factionId, long settlementId, StructureRole role, SimPosition center,
            int width, int depth, int rotationQuarterTurns, int priority, List<SimPosition> path
    ) {
        this(key, factionId, settlementId, role, center, width, depth, rotationQuarterTurns, priority,
                "", 0, 0, path);
    }

    public boolean hasParcel() { return !parcelId.isBlank(); }
    public boolean hasPath() { return !path.isEmpty(); }

    public ConstructionIntent withCenter(SimPosition newCenter) {
        Objects.requireNonNull(newCenter, "newCenter");
        List<SimPosition> shifted = path;
        if (!path.isEmpty()) {
            double dx = newCenter.x() - center.x();
            double dz = newCenter.z() - center.z();
            shifted = path.stream().map(p -> new SimPosition(p.x() + dx, p.z() + dz)).toList();
        }
        return new ConstructionIntent(key, factionId, settlementId, role, newCenter, width, depth,
                rotationQuarterTurns, priority, parcelId, parcelWidth, parcelDepth, shifted);
    }

    public ConstructionIntent withPriority(int newPriority) {
        return new ConstructionIntent(key, factionId, settlementId, role, center, width, depth,
                rotationQuarterTurns, newPriority, parcelId, parcelWidth, parcelDepth, path);
    }

    private static boolean hasParcelRaw(String id) { return id != null && !id.isBlank(); }
}
