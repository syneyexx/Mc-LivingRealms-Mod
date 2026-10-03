package dev.livingrealms.sim.construction;

/** Ordering guarantee for safe incremental construction. */
public enum ConstructionPhase {
    CLEAR,
    FOUNDATION,
    FRAME,
    SHELL,
    DETAIL
}
