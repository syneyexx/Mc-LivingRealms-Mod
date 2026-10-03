package dev.livingrealms.sim.biome;

import java.util.Set;

public record EcoBiome(
        String id,
        ClimateBand climate,
        double basePlantBiomass,
        double plantRegrowthPerDay,
        double waterAvailability,
        double shelter,
        Set<String> tags
) {
    public EcoBiome {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
        tags = Set.copyOf(tags);
        if (basePlantBiomass < 0 || plantRegrowthPerDay < 0) throw new IllegalArgumentException("biomass");
    }
}
