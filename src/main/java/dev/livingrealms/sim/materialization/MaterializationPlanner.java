package dev.livingrealms.sim.materialization;

import dev.livingrealms.sim.ecology.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Computes how much of each aggregate cohort should be represented by real Minecraft entities.
 * It never mutates population state; spawning/despawning is handled by the Minecraft adapter.
 */
public final class MaterializationPlanner {
    private final MaterializationConfig config;
    public MaterializationPlanner(MaterializationConfig config) { this.config = Objects.requireNonNull(config); }

    public List<MaterializationRequest> plan(Collection<PopulationGroup> groups,
                                             Map<String, SpeciesDefinition> species,
                                             Collection<SimPosition> players) {
        if (groups == null || species == null || players == null) throw new IllegalArgumentException("null input");
        if (players.isEmpty()) return groups.stream()
                .map(g -> new MaterializationRequest(g.id(), g.speciesId(), SimulationLod.ABSTRACT, 0, Double.POSITIVE_INFINITY))
                .toList();

        List<Candidate> candidates = new ArrayList<>();
        for (PopulationGroup g : groups) {
            SpeciesDefinition sp = species.get(g.speciesId());
            if (sp == null || g.extinct()) continue;
            double distance = nearestDistance(g.position(), players);
            SimulationLod lod = distance <= config.physicalRadiusBlocks() ? SimulationLod.PHYSICAL
                    : distance <= config.regionalRadiusBlocks() ? SimulationLod.REGIONAL : SimulationLod.ABSTRACT;
            int desired = 0;
            if (lod == SimulationLod.PHYSICAL) {
                int populationCap = (int)Math.min(Integer.MAX_VALUE, Math.floor(g.population()));
                int socialCap = (int)Math.max(1, Math.ceil(sp.maxGroup()));
                desired = Math.min(populationCap, Math.min(config.maxAnimalsPerGroup(), socialCap));
            }
            candidates.add(new Candidate(g, lod, desired, distance));
        }

        // Spend the global physical-entity budget on closest cohorts first.
        candidates.sort(Comparator.comparingDouble(Candidate::distance).thenComparingLong(c -> c.group().id()));
        int totalBudget = Math.max(config.maxAnimalsPerPlayer(), config.maxAnimalsPerPlayer() * players.size());
        List<MaterializationRequest> out = new ArrayList<>(candidates.size());
        for (Candidate c : candidates) {
            int allowed = Math.min(c.desired(), Math.max(0, totalBudget));
            totalBudget -= allowed;
            out.add(new MaterializationRequest(c.group().id(), c.group().speciesId(), c.lod(), allowed, c.distance()));
        }
        out.sort(Comparator.comparingLong(MaterializationRequest::populationGroupId));
        return List.copyOf(out);
    }

    private static double nearestDistance(SimPosition origin, Collection<SimPosition> players) {
        double best = Double.POSITIVE_INFINITY;
        for (SimPosition p : players) best = Math.min(best, origin.distanceTo(p));
        return best;
    }

    private record Candidate(PopulationGroup group, SimulationLod lod, int desired, double distance) {}
}
