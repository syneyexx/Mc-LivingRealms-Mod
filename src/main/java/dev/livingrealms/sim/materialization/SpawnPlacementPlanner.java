package dev.livingrealms.sim.materialization;

import dev.livingrealms.sim.ecology.PopulationGroup;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimPosition;
import java.util.Objects;

/** Stable horizontal spawn placement around a cohort center; Minecraft resolves terrain height. */
public final class SpawnPlacementPlanner {
    private SpawnPlacementPlanner() {}

    public static SimPosition position(long worldSeed, PopulationGroup group, int slot) {
        Objects.requireNonNull(group, "group");
        if (slot < 0) throw new IllegalArgumentException("slot");
        long mixed = worldSeed ^ (group.id() * 0x9E3779B97F4A7C15L) ^ ((long) slot * 0xD1B54A32D192ED03L);
        DeterministicRng rng = new DeterministicRng(mixed);
        double radius = rng.between(4.0, 34.0);
        double angle = rng.between(0.0, Math.PI * 2.0);
        return new SimPosition(
                group.position().x() + Math.cos(angle) * radius,
                group.position().z() + Math.sin(angle) * radius
        );
    }
}
