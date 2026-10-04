package dev.livingrealms.sim.construction;

/** Shared rules for which blueprint slots are load-bearing vs decorative. */
public final class StructureGeometryRules {
    private StructureGeometryRules() {}

    /**
     * Required geometry must be applied or already-correct before a structure intent may be marked
     * canonically complete. Decorative lamps/glass/fences and soft detail do not block completion.
     */
    public static boolean isRequiredGeometry(PaletteSlot slot, ConstructionPhase phase) {
        if (slot == null || phase == null) return true;
        return switch (slot) {
            case LIGHT, REDSTONE_LIGHT, FENCE, GLASS, CROP, STORAGE, MACHINE, METAL -> false;
            case AIR -> phase != ConstructionPhase.DETAIL;
            case FOUNDATION, FLOOR, WALL, BEAM, ROOF, DOOR, PATH, FARMLAND, RUNWAY, WATER -> true;
        };
    }

    /** Minimum fraction of required ops that must be satisfied for canonical completion. */
    public static double requiredCompletionThreshold(StructureRole role) {
        if (role == null) return 0.98D;
        return switch (role) {
            case ROAD -> 0.90D;
            case WALL, GATE -> 0.92D;
            case FARM -> 0.85D;
            default -> 0.98D;
        };
    }

    /** Door ops are hard-required when present: a building without its entrance is incomplete. */
    public static boolean requiresAllDoors(StructureRole role) {
        return role != StructureRole.ROAD && role != StructureRole.WALL && role != StructureRole.FARM;
    }
}
