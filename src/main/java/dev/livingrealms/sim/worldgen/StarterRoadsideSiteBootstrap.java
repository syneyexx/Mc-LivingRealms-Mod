package dev.livingrealms.sim.worldgen;

import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Main-thread canonical consumer of the pure starter roadside-site plan. */
public final class StarterRoadsideSiteBootstrap {
    private StarterRoadsideSiteBootstrap() {}

    public static int ensure(SimulationState state) {
        Objects.requireNonNull(state, "state");
        return ensure(state, StarterCivilizationLayoutPlanner.plan(state.seed()));
    }

    public static int ensure(
            SimulationState state,
            StarterCivilizationLayoutPlanner.Layout layout) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(layout, "layout");
        var plans = StarterRoadsideSitePlanner.plan(layout);
        Set<Long> existingIds = new HashSet<>();
        for (var site : state.roadsideSites()) existingIds.add(site.id());

        int added = 0;
        for (StarterRoadsideSitePlanner.SitePlan plan : plans) {
            if (existingIds.contains(plan.stableSiteId())) continue;
            boolean sameAnchor = state.roadsideSites().stream().anyMatch(site ->
                    site.relatedRouteId() == plan.relatedRouteId()
                            && site.position().distanceTo(plan.position()) < 1.0);
            if (sameAnchor) continue;
            state.addRoadsideSite(plan.asRoadsideSite());
            existingIds.add(plan.stableSiteId());
            added++;
        }
        // Keep later simulation-created sites outside every unused starter slot as well.
        state.restoreNextId(Math.max(
                state.peekNextId(), StarterRoadsideSitePlanner.STARTER_ROADSIDE_SITE_ID_LIMIT));
        return added;
    }
}
