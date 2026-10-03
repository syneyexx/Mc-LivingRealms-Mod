package dev.livingrealms.sim.materialization;

/** One deterministic materialization slot that should exist physically. */
public record ProjectionSpawn(long populationGroupId, String speciesId, int slot) {
    public ProjectionSpawn {
        if (populationGroupId <= 0) throw new IllegalArgumentException("populationGroupId");
        if (speciesId == null || speciesId.isBlank()) throw new IllegalArgumentException("speciesId");
        if (slot < 0) throw new IllegalArgumentException("slot");
    }
}
