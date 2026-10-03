package dev.livingrealms.sim.logistics.projection;

import java.util.List;
import java.util.Objects;

public record CaravanProjectionPlan(List<CaravanProjectionSpawn> spawns, List<CaravanProjectionDespawn> despawns) {
    public CaravanProjectionPlan {
        spawns = List.copyOf(Objects.requireNonNull(spawns, "spawns"));
        despawns = List.copyOf(Objects.requireNonNull(despawns, "despawns"));
    }
}
