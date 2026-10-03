package dev.livingrealms.sim.materialization;

import java.util.List;

/** Immutable delta between desired aggregate projection and currently loaded Minecraft entities. */
public record ProjectionPlan(List<ProjectionSpawn> spawns, List<ProjectionDespawn> despawns) {
    public ProjectionPlan {
        spawns = List.copyOf(spawns);
        despawns = List.copyOf(despawns);
    }

    public static ProjectionPlan empty() { return new ProjectionPlan(List.of(), List.of()); }
    public boolean isEmpty() { return spawns.isEmpty() && despawns.isEmpty(); }
}
