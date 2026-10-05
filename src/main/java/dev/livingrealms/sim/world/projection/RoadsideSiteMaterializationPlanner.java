package dev.livingrealms.sim.world.projection;

import dev.livingrealms.sim.world.RoadLifeEngine;
import dev.livingrealms.sim.world.RoadsideSite;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure LOD planner for roadside sites. Sites are never settlements and use
 * {@link RoadLifeEngine#MIN_SITE_SPACING}, not settlement clearance.
 */
public final class RoadsideSiteMaterializationPlanner {
    private final double radiusBlocks;
    private final int budget;

    public RoadsideSiteMaterializationPlanner(double radiusBlocks, int budget) {
        if (!Double.isFinite(radiusBlocks) || radiusBlocks <= 0 || budget < 0) {
            throw new IllegalArgumentException("projection config");
        }
        this.radiusBlocks = radiusBlocks;
        this.budget = budget;
    }

    public List<RoadsideSiteProjection> plan(SimulationState state, Collection<SimPosition> players) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(players, "players");
        if (players.isEmpty() || budget == 0) return List.of();
        List<RoadsideSiteProjection> candidates = new ArrayList<>();
        for (RoadsideSite site : state.roadsideSites()) {
            if (!site.active() && site.lifecycle() != RoadsideSite.Lifecycle.RUINED
                    && site.lifecycle() != RoadsideSite.Lifecycle.ABANDONED) {
                continue;
            }
            // Ruined/abandoned may still show sparse remnants when marked inactive-but-present.
            if (!site.active() && site.lifecycle() == RoadsideSite.Lifecycle.ACTIVE) continue;
            SimPosition position = site.position();
            double nearest = nearest(position, players);
            if (nearest > radiusBlocks) continue;
            candidates.add(new RoadsideSiteProjection(
                    site.id(), site.type(), site.lifecycle(), position, nearest));
        }
        candidates.sort(Comparator.comparingDouble(RoadsideSiteProjection::nearestPlayerDistance)
                .thenComparingLong(RoadsideSiteProjection::siteId));
        if (candidates.size() > budget) {
            return List.copyOf(candidates.subList(0, budget));
        }
        return List.copyOf(candidates);
    }

    /**
     * True when {@code candidate} would violate roadside site spacing (not settlement spacing).
     * Settlement clearance is intentionally ignored — roadside sites may sit between towns.
     */
    public static boolean violatesSiteSpacing(SimulationState state, SimPosition candidate) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(candidate, "candidate");
        for (RoadsideSite site : state.roadsideSites()) {
            if (!site.active()) continue;
            if (site.position().distanceTo(candidate) < RoadLifeEngine.MIN_SITE_SPACING) return true;
        }
        return false;
    }

    /** Roadside sites must never appear in the settlement roster or share settlement ids. */
    public static boolean isCanonicalSettlement(SimulationState state, RoadsideSite site) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(site, "site");
        return state.findSettlement(site.id()).isPresent()
                || state.factions().stream().flatMap(f -> f.settlements().stream())
                .anyMatch(s -> s.id() == site.id());
    }

    private static double nearest(SimPosition position, Collection<SimPosition> players) {
        double best = Double.POSITIVE_INFINITY;
        for (SimPosition player : players) best = Math.min(best, position.distanceTo(player));
        return best;
    }
}
