package dev.livingrealms.sim.world;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.util.DeterministicRng;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Sparse road-life layer: causal citizen journeys on routes plus occasional roadside sites.
 * Sites are never canonical settlements and do not participate in settlement spacing.
 */
public final class RoadLifeEngine {
    /** Soft cap of active journeys worldwide. */
    public static final int MAX_ACTIVE_JOURNEYS = 96;
    /** Minimum blocks between roadside sites. */
    public static final double MIN_SITE_SPACING = 420.0;
    private static final CitizenJourney.Purpose[] PURPOSES = CitizenJourney.Purpose.values();
    private static final RoadsideSite.Type[] SITE_TYPES = RoadsideSite.Type.values();

    private RoadLifeEngine() {}

    public static void simulateDay(SimulationState state, DeterministicRng rng) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rng, "rng");
        advanceJourneys(state);
        spawnCausalJourneys(state, rng);
        maybeSpawnRoadsideSite(state, rng);
    }

    private static void advanceJourneys(SimulationState state) {
        for (CitizenJourney journey : state.citizenJourneys()) {
            if (!journey.active()) continue;
            TransportRoute route = journey.routeId() > 0
                    ? state.routes().stream().filter(r -> r.id() == journey.routeId()).findFirst().orElse(null)
                    : null;
            double delta;
            if (route != null && route.distanceBlocks() > 1) {
                delta = Math.min(.35, route.speedBlocksPerDay() / route.distanceBlocks());
            } else {
                delta = .12;
            }
            if (journey.advance(delta)) {
                state.history().add(new WorldEvent(state.clock().day(), "citizen_journey_arrived",
                        "journey=" + journey.id() + ", purpose=" + journey.purpose().name()
                                + ", to=" + journey.targetSettlementId()));
            }
        }
    }

    private static void spawnCausalJourneys(SimulationState state, DeterministicRng rng) {
        long active = state.citizenJourneys().stream().filter(CitizenJourney::active).count();
        if (active >= MAX_ACTIVE_JOURNEYS) return;
        List<TransportRoute> routes = state.routes().stream()
                .filter(TransportRoute::operational)
                .filter(r -> r.distanceBlocks() >= 400)
                .sorted(Comparator.comparingLong(TransportRoute::id))
                .toList();
        if (routes.isEmpty()) return;
        // Low daily pressure: a few routes may emit travelers when settlements are lively.
        int attempts = Math.min(3, Math.max(1, routes.size() / 12));
        for (int i = 0; i < attempts && active < MAX_ACTIVE_JOURNEYS; i++) {
            TransportRoute route = routes.get(rng.nextInt(routes.size()));
            Settlement from = state.findSettlement(route.fromSettlementId()).orElse(null);
            Settlement to = state.findSettlement(route.toSettlementId()).orElse(null);
            if (from == null || to == null) continue;
            Faction owner = state.findFaction(route.ownerFactionId()).orElse(null);
            if (owner == null) continue;
            // Causal gate: prosperity/order and route security encourage travel.
            double pressure = .04 + from.prosperity() * .05 + to.prosperity() * .03
                    + route.security() * .04 + route.quality() * .03;
            if (from.unrest() > .55) pressure *= .45;
            if (!rng.chance(Math.min(.22, pressure))) continue;
            boolean already = state.citizenJourneys().stream().anyMatch(j -> j.active()
                    && j.originSettlementId() == from.id() && j.targetSettlementId() == to.id());
            if (already) continue;
            CitizenJourney.Purpose purpose = PURPOSES[rng.nextInt(PURPOSES.length)];
            long citizenId = state.socialCitizens().stream()
                    .filter(c -> c.alive() && c.settlementId() == from.id())
                    .mapToLong(c -> c.id()).findFirst().orElse(0L);
            CitizenJourney journey = new CitizenJourney(state.nextId(), citizenId, owner.id(),
                    from.id(), to.id(), route.id(), purpose, state.clock().day());
            journey.setPayload(journey.purposeLabel());
            state.addCitizenJourney(journey);
            state.history().add(new WorldEvent(state.clock().day(), "citizen_journey_departed",
                    owner.name() + " " + journey.purposeLabel() + " left " + from.name() + " for " + to.name()));
            active++;
        }
    }

    private static void maybeSpawnRoadsideSite(SimulationState state, DeterministicRng rng) {
        if (state.roadsideSites().size() >= SimulationState.MAX_ROADSIDE_SITES) return;
        if (!rng.chance(.08)) return;
        List<TransportRoute> routes = new ArrayList<>(state.routes().stream()
                .filter(TransportRoute::operational)
                .filter(r -> r.distanceBlocks() >= 800)
                .toList());
        if (routes.isEmpty()) return;
        TransportRoute route = routes.get(rng.nextInt(routes.size()));
        Settlement from = state.findSettlement(route.fromSettlementId()).orElse(null);
        Settlement to = state.findSettlement(route.toSettlementId()).orElse(null);
        if (from == null || to == null) return;
        double t = .25 + rng.nextDouble() * .5;
        SimPosition pos = from.position().lerp(to.position(), t);
        if (tooCloseSite(state, pos)) return;
        RoadsideSite.Type type = SITE_TYPES[rng.nextInt(SITE_TYPES.length)];
        RoadsideSite site = new RoadsideSite(state.nextId(), type, pos,
                RoadsideSite.defaultName(type, pos), from.id(), route.id(), state.clock().day());
        state.addRoadsideSite(site);
        state.history().add(new WorldEvent(state.clock().day(), "roadside_site_formed",
                site.name() + " (" + type.name() + ") along route " + route.id()));
    }

    private static boolean tooCloseSite(SimulationState state, SimPosition pos) {
        for (RoadsideSite site : state.roadsideSites()) {
            if (site.active() && site.position().distanceTo(pos) < MIN_SITE_SPACING) return true;
        }
        return false;
    }
}
