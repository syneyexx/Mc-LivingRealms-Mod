package dev.livingrealms.sim.world.projection;

import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.CitizenJourney;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure LOD planner for road travelers. Planning never mutates canonical journey state —
 * dematerialization for distance is distinct from physical journey death.
 */
public final class CitizenJourneyMaterializationPlanner {
    public enum RemovalReason {
        OUTSIDE_PHYSICAL_RADIUS,
        JOURNEY_FINISHED,
        DUPLICATE
    }

    public record Snapshot(String entityKey, long journeyId) {
        public Snapshot {
            if (entityKey == null || entityKey.isBlank()) throw new IllegalArgumentException("entityKey");
            if (journeyId <= 0) throw new IllegalArgumentException("journeyId");
        }
    }

    public record Spawn(long journeyId, SimPosition position) {
        public Spawn {
            if (journeyId <= 0) throw new IllegalArgumentException("journeyId");
            Objects.requireNonNull(position, "position");
        }
    }

    public record Despawn(String entityKey, long journeyId, RemovalReason reason) {
        public Despawn {
            if (entityKey == null || entityKey.isBlank()) throw new IllegalArgumentException("entityKey");
            if (journeyId <= 0) throw new IllegalArgumentException("journeyId");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public record Plan(List<Spawn> spawns, List<Despawn> despawns) {
        public Plan {
            Objects.requireNonNull(spawns, "spawns");
            Objects.requireNonNull(despawns, "despawns");
            spawns = List.copyOf(spawns);
            despawns = List.copyOf(despawns);
        }
    }

    private final double radiusBlocks;
    private final int budget;

    public CitizenJourneyMaterializationPlanner(double radiusBlocks, int budget) {
        if (!Double.isFinite(radiusBlocks) || radiusBlocks <= 0 || budget < 0) {
            throw new IllegalArgumentException("projection config");
        }
        this.radiusBlocks = radiusBlocks;
        this.budget = budget;
    }

    public List<CitizenJourneyProjection> plan(SimulationState state, Collection<SimPosition> players) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(players, "players");
        if (players.isEmpty() || budget == 0) return List.of();
        List<CitizenJourneyProjection> candidates = new ArrayList<>();
        for (CitizenJourney journey : state.citizenJourneys()) {
            if (!journey.active()) continue;
            SimPosition position = positionOf(state, journey);
            if (position == null) continue;
            double nearest = nearest(position, players);
            if (nearest > radiusBlocks) continue;
            candidates.add(new CitizenJourneyProjection(
                    journey.id(), journey.factionId(), journey.originSettlementId(),
                    journey.citizenId(), journey.purpose(), position, nearest));
        }
        candidates.sort(Comparator.comparingDouble(CitizenJourneyProjection::nearestPlayerDistance)
                .thenComparingLong(CitizenJourneyProjection::journeyId));
        if (candidates.size() > budget) {
            return List.copyOf(candidates.subList(0, budget));
        }
        return List.copyOf(candidates);
    }

    public Plan reconcile(Collection<CitizenJourneyProjection> desired,
                          Collection<Snapshot> actual,
                          Set<Long> activeJourneyIds) {
        Objects.requireNonNull(desired, "desired");
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(activeJourneyIds, "activeJourneyIds");

        Set<Long> wanted = new HashSet<>();
        List<Spawn> spawns = new ArrayList<>();
        for (CitizenJourneyProjection request : desired) {
            if (!wanted.add(request.journeyId())) continue;
            boolean present = actual.stream().anyMatch(s -> s.journeyId() == request.journeyId());
            if (!present) spawns.add(new Spawn(request.journeyId(), request.position()));
        }

        List<Despawn> despawns = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        List<Snapshot> ordered = new ArrayList<>(actual);
        ordered.sort(Comparator.comparingLong(Snapshot::journeyId).thenComparing(Snapshot::entityKey));
        for (Snapshot snapshot : ordered) {
            if (!seen.add(snapshot.journeyId())) {
                despawns.add(new Despawn(snapshot.entityKey(), snapshot.journeyId(), RemovalReason.DUPLICATE));
                continue;
            }
            boolean keep = wanted.contains(snapshot.journeyId());
            if (keep) continue;
            RemovalReason reason = activeJourneyIds.contains(snapshot.journeyId())
                    ? RemovalReason.OUTSIDE_PHYSICAL_RADIUS
                    : RemovalReason.JOURNEY_FINISHED;
            despawns.add(new Despawn(snapshot.entityKey(), snapshot.journeyId(), reason));
        }
        spawns.sort(Comparator.comparingLong(Spawn::journeyId));
        despawns.sort(Comparator.comparingLong(Despawn::journeyId).thenComparing(Despawn::entityKey));
        return new Plan(spawns, despawns);
    }

    /** Route progress position; never invents settlement anchors. */
    public static SimPosition positionOf(SimulationState state, CitizenJourney journey) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(journey, "journey");
        Settlement from = state.findSettlement(journey.originSettlementId()).orElse(null);
        Settlement to = state.findSettlement(journey.targetSettlementId()).orElse(null);
        if (from == null || to == null) return null;
        return from.position().lerp(to.position(), journey.progress());
    }

    private static double nearest(SimPosition position, Collection<SimPosition> players) {
        double best = Double.POSITIVE_INFINITY;
        for (SimPosition player : players) best = Math.min(best, position.distanceTo(player));
        return best;
    }
}
