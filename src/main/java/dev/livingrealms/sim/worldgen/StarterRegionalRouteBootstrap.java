package dev.livingrealms.sim.worldgen;

import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Main-thread canonical consumer of the pure starter regional route plan. */
public final class StarterRegionalRouteBootstrap {
    private StarterRegionalRouteBootstrap() {}

    public static int ensure(SimulationState state) {
        Objects.requireNonNull(state, "state");
        return ensure(state, StarterCivilizationLayoutPlanner.plan(state.seed()));
    }

    public static int ensure(
            SimulationState state,
            StarterCivilizationLayoutPlanner.Layout layout) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(layout, "layout");
        var plans = StarterRegionalRoutePlanner.plan(layout);
        Set<Long> existingIds = new HashSet<>();
        for (TransportRoute route : state.routes()) existingIds.add(route.id());

        int added = 0;
        for (StarterRegionalRoutePlanner.RoutePlan plan : plans) {
            if (existingIds.contains(plan.stableRouteId())) continue;
            boolean samePhysicalRoute = state.routes().stream().anyMatch(route ->
                    route.ownerFactionId() == plan.factionId()
                            && route.mode() == plan.mode()
                            && sameEnds(route, plan.fromSettlementId(), plan.toSettlementId()));
            if (samePhysicalRoute) continue;
            state.addRoute(plan.asTransportRoute());
            existingIds.add(plan.stableRouteId());
            state.liveness().onRouteBuilt();
            added++;
        }
        // Reserve the complete starter-route range. Dynamic nextId()-allocated routes must never
        // land in an unused starter slot and be misclassified as worldgen-owned.
        state.restoreNextId(Math.max(state.peekNextId(), StarterRegionalRoutePlanner.STARTER_ROUTE_ID_LIMIT));
        return added;
    }

    private static boolean sameEnds(TransportRoute route, long a, long b) {
        return (route.fromSettlementId() == a && route.toSettlementId() == b)
                || (route.fromSettlementId() == b && route.toSettlementId() == a);
    }
}
