package dev.livingrealms.sim.construction;

/** Absolute, dimension-agnostic placement resolved from a structure blueprint. */
public record BuildOperation(int x, int y, int z, PaletteSlot slot, ConstructionPhase phase) {}
