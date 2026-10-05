package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
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
        if (!path.isEmpty() && path.size() < 2) throw new IllegalArgumentException("path");
    }

    /** Compatibility constructor for ordinary axis-aligned intents and parcel-bound buildings. */
    public ConstructionIntent(
            String key, long factionId, long settlementId, StructureRole role, SimPosition center,
            int width, int depth, int rotationQuarterTurns, int priority,
            String parcelId, int parcelWidth, int parcelDepth
    ) {
        this(key, factionId, settlementId, role, center, width, depth, rotationQuarterTurns, priority,
                parcelId, parcelWidth, parcelDepth, List.of());
    }

    public ConstructionIntent(
            String key, long factionId, long settlementId, StructureRole role, SimPosition center,
            int width, int depth, int rotationQuarterTurns, int priority
    ) {
        this(key, factionId, settlementId, role, center, width, depth, rotationQuarterTurns, priority,
                "", 0, 0, List.of());
    }

    public boolean hasParcel() { return !parcelId.isBlank(); }
    public boolean hasPath() { return !path.isEmpty(); }

    public ConstructionIntent withCenter(SimPosition newCenter) {
        Objects.requireNonNull(newCenter, "newCenter");
        if (path.isEmpty()) {
            return new ConstructionIntent(key, factionId, settlementId, role, newCenter, width, depth,
                    rotationQuarterTurns, priority, parcelId, parcelWidth, parcelDepth, path);
        }
        double dx = newCenter.x() - center.x(), dz = newCenter.z() - center.z();
        List<SimPosition> shifted = new ArrayList<>(path.size());
        for (SimPosition point : path) shifted.add(new SimPosition(point.x() + dx, point.z() + dz));
        return new ConstructionIntent(key, factionId, settlementId, role, newCenter, width, depth,
                rotationQuarterTurns, priority, parcelId, parcelWidth, parcelDepth, shifted);
    }

    public ConstructionIntent withPriority(int newPriority) {
        return new ConstructionIntent(key, factionId, settlementId, role, center, width, depth,
                rotationQuarterTurns, newPriority, parcelId, parcelWidth, parcelDepth, path);
    }

    private static boolean hasParcelRaw(String id) { return id != null && !id.isBlank(); }
}
