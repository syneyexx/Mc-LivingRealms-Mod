package dev.livingrealms.sim.materialization;

/** Why a physical representation should be removed without changing aggregate population. */
public enum ProjectionRemovalReason {
    OUTSIDE_PHYSICAL_LOD,
    OVER_BUDGET,
    DUPLICATE_SLOT,
    ORPHANED_GROUP,
    SPECIES_MISMATCH,
    INVALID_SLOT
}
