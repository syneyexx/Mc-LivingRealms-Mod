package dev.livingrealms.sim.construction;

import java.util.Objects;

/** One relative block operation in a structure blueprint. */
public record BlockPlacement(
        int dx,
        int dy,
        int dz,
        PaletteSlot slot,
        ConstructionPhase phase
) {
    public BlockPlacement {
        slot = Objects.requireNonNull(slot, "slot");
        phase = Objects.requireNonNull(phase, "phase");
    }
}
