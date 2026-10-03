package dev.livingrealms.sim.ecology;

import dev.livingrealms.sim.biome.EcoBiome;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministically introduces catalog species into compatible ecosystem regions.
 * This is deliberately aggregate-only: it can seed hundreds of species without creating Minecraft entities.
 */
public final class HabitatPopulationSeeder {
    private HabitatPopulationSeeder() {}

    public static int seedMissingSpecies(SimulationState state, EcosystemRegion region, int maxNewSpecies) {
        if (maxNewSpecies < 0) throw new IllegalArgumentException("maxNewSpecies");
        Set<String> existing = new HashSet<>();
        for (PopulationGroup group : region.populations()) if (!group.extinct()) existing.add(group.speciesId());

        List<SpeciesDefinition> candidates = new ArrayList<>(state.species().values());
        candidates.sort(Comparator.comparing(SpeciesDefinition::id));
        int added = 0;
        for (SpeciesDefinition species : candidates) {
            if (added >= maxNewSpecies) break;
            if (existing.contains(species.id())) continue;
            double suitability = suitability(species, region.biome());
            if (suitability < .52) continue;

            long salt = stableHash(species.id());
            DeterministicRng rng = new DeterministicRng(state.seed() ^ region.id() * 0x9E3779B97F4A7C15L ^ salt);
            // Marginal habitat remains possible but rare, while strong habitat is almost always occupied.
            if (!rng.chance(Mathx.clamp(.20 + suitability * .78, 0, .98))) continue;

            double population = initialPopulation(species, region, suitability, rng);
            if (population < .5) continue;
            double radius = Math.max(24.0, Math.sqrt(region.areaKm2()) * 72.0);
            double angle = rng.between(0, Math.PI * 2.0);
            double distance = rng.between(radius * .12, radius * .82);
            SimPosition position = new SimPosition(
                    region.center().x() + Math.cos(angle) * distance,
                    region.center().z() + Math.sin(angle) * distance);
            region.add(new PopulationGroup(state.nextId(), species.id(), region.biome().id(), position, population));
            existing.add(species.id());
            added++;
        }
        return added;
    }

    public static double suitability(SpeciesDefinition species, EcoBiome biome) {
        boolean aquaticBiome = biome.tags().contains("aquatic") || biome.climate().name().equals("AQUATIC");
        boolean wetEdge = biome.tags().contains("wetland") || biome.tags().contains("river") || biome.tags().contains("coast")
                || biome.tags().contains("mangrove") || biome.tags().contains("estuary");
        if (species.locomotion() == LocomotionMode.AQUATIC && !aquaticBiome) return 0;
        if (species.locomotion() == LocomotionMode.TERRESTRIAL && aquaticBiome) return 0;
        if (species.locomotion() == LocomotionMode.AMPHIBIOUS && !(aquaticBiome || wetEdge)) return .15;

        double score = species.climates().contains(biome.climate()) ? .48 : 0;
        long matches = species.habitatTags().stream().filter(biome.tags()::contains).count();
        score += Math.min(.42, matches * .14);
        if (species.locomotion() == LocomotionMode.FLYING) score += .05;
        if (species.locomotion() == LocomotionMode.AMPHIBIOUS && wetEdge) score += .10;
        if (species.climates().stream().anyMatch(c -> c.name().equals("AQUATIC")) && aquaticBiome) score += .10;
        return Mathx.clamp(score, 0, 1);
    }

    private static double initialPopulation(SpeciesDefinition species, EcosystemRegion region, double suitability, DeterministicRng rng) {
        double bodyPenalty = Math.sqrt(Math.max(.08, species.adultMassKg()));
        double trophic = switch (species.diet()) {
            case HERBIVORE, DETRITIVORE, FILTER_FEEDER -> 1.0;
            case OMNIVORE, INSECTIVORE, PISCIVORE -> .62;
            case CARNIVORE -> .34;
        };
        double density = (95.0 / bodyPenalty) * trophic * (.35 + suitability * .85);
        double base = region.areaKm2() * density * rng.between(.72, 1.28);
        double socialFloor = Math.max(1.0, species.minGroup());
        double cap = Math.max(socialFloor, region.areaKm2() * 450.0);
        return Mathx.clamp(base, socialFloor, cap);
    }

    private static long stableHash(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
