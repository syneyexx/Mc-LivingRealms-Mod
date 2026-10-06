package dev.livingrealms.sim.world;

import dev.livingrealms.sim.civilization.CivilizationCalendar;
import dev.livingrealms.sim.civilization.FactionCivilizationState;
import dev.livingrealms.sim.civilization.SettlementCivilizationState;
import dev.livingrealms.sim.diplomacy.WarState;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
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
    /** Minimum blocks between independent roadside sites; keeps the road layer sparse. */
    public static final double MIN_SITE_SPACING = 280.0;
    /** Desired maximum gap between meaningful civilization fabric along an inhabited road. */
    public static final double TARGET_CORRIDOR_SPACING = 380.0;
    /** Prevent one extreme route from consuming the global roadside-site budget. */
    public static final int MAX_CORRIDOR_ANCHORS_PER_ROUTE = 10;
    private static final RoadsideSite.Type[] SITE_TYPES = RoadsideSite.Type.values();
    private static final RoadsideSite.Type[] CORRIDOR_TYPES = {
            RoadsideSite.Type.WAYSTATION,
            RoadsideSite.Type.MILESTONE,
            RoadsideSite.Type.SHRINE,
            RoadsideSite.Type.TRAVELER_CAMP,
            RoadsideSite.Type.TOLL_POST
    };

    private RoadLifeEngine() {}

    public static void simulateDay(SimulationState state, DeterministicRng rng) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rng, "rng");
        ensureCorridorSites(state);
        advanceJourneys(state);
        spawnCausalJourneys(state, rng);
        maybeSpawnRoadsideSite(state, rng);
    }

    /**
     * Deterministically fills long inhabited transport corridors with sparse physical/canonical
     * roadside anchors. End-point settlements count as civilization fabric; intermediate anchors
     * keep the remaining gaps near 300–450 blocks without creating extra settlements.
     *
     * <p>This is idempotent, bounded by the global site cap and per-route anchor cap, and does not
     * depend on players or loaded chunks. Block materialization remains chunk-driven.</p>
     */
    public static int ensureCorridorSites(SimulationState state) {
        Objects.requireNonNull(state, "state");
        if (state.roadsideSites().size() >= SimulationState.MAX_ROADSIDE_SITES) return 0;
        int added = 0;
        List<TransportRoute> routes = state.routes().stream()
                .filter(TransportRoute::operational)
                .filter(r -> r.mode() == dev.livingrealms.sim.transport.TransportMode.ROAD
                        || r.mode() == dev.livingrealms.sim.transport.TransportMode.CARAVAN)
                .sorted(Comparator.comparingLong(TransportRoute::id))
                .toList();

        for (TransportRoute route : routes) {
            if (state.roadsideSites().size() >= SimulationState.MAX_ROADSIDE_SITES) break;
            Settlement from = state.findSettlement(route.fromSettlementId()).orElse(null);
            Settlement to = state.findSettlement(route.toSettlementId()).orElse(null);
            if (from == null || to == null) continue;
            double distance = from.position().distanceTo(to.position());
            if (distance < TARGET_CORRIDOR_SPACING * 1.35) continue;

            int segments = Math.max(2, (int) Math.ceil(distance / TARGET_CORRIDOR_SPACING));
            int anchors = Math.min(MAX_CORRIDOR_ANCHORS_PER_ROUTE, segments - 1);
            for (int slot = 1; slot <= anchors; slot++) {
                if (state.roadsideSites().size() >= SimulationState.MAX_ROADSIDE_SITES) break;
                double t = slot / (double) (anchors + 1);
                SimPosition pos = from.position().lerp(to.position(), t);

                boolean alreadyRepresented = state.roadsideSites().stream().anyMatch(site ->
                        site.active() && site.relatedRouteId() == route.id()
                                && site.position().distanceTo(pos) < 120.0);
                if (alreadyRepresented || tooCloseSite(state, pos)) continue;

                int typeIndex = Math.floorMod(Long.hashCode(
                        state.seed() ^ route.id() * 0x9E3779B97F4A7C15L ^ slot * 0xD1B54A32D192ED03L),
                        CORRIDOR_TYPES.length);
                RoadsideSite.Type type = CORRIDOR_TYPES[typeIndex];
                long relatedSettlementId = t <= .5 ? from.id() : to.id();
                RoadsideSite site = new RoadsideSite(state.nextId(), type, pos,
                        RoadsideSite.defaultName(type, pos), relatedSettlementId, route.id(), state.clock().day());
                state.addRoadsideSite(site);
                added++;
            }
        }
        return added;
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
            List<CitizenJourney.Purpose> candidates = causalPurposeCandidates(state, owner, from, to, route);
            if (candidates.isEmpty()) continue;
            // Causal gate: prosperity/order plus active causes (war, bandits, harvest) encourage travel.
            double pressure = .05 + from.prosperity() * .06 + to.prosperity() * .04
                    + route.security() * .04 + route.quality() * .03;
            if (candidates.contains(CitizenJourney.Purpose.COURIER)
                    || candidates.contains(CitizenJourney.Purpose.DIPLOMAT)) {
                pressure += .12; // war / dispatch pressure
            }
            if (candidates.contains(CitizenJourney.Purpose.PATROL)) {
                pressure += .08;
            }
            if (candidates.contains(CitizenJourney.Purpose.SEASONAL_WORKER)) {
                pressure += .05;
            }
            if (from.unrest() > .55) pressure *= .45;
            if (!rng.chance(Math.min(.38, pressure))) continue;
            boolean already = state.citizenJourneys().stream().anyMatch(j -> j.active()
                    && j.originSettlementId() == from.id() && j.targetSettlementId() == to.id());
            if (already) continue;
            CitizenJourney.Purpose purpose = candidates.get(rng.nextInt(candidates.size()));
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

    /**
     * Builds the causal purpose slate for a would-be traveler.
     * Randomness (caller) only picks among these valid candidates — never across the full enum.
     */
    public static List<CitizenJourney.Purpose> causalPurposeCandidates(SimulationState state, Faction owner,
                                                                Settlement from, Settlement to,
                                                                TransportRoute route) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(route, "route");
        List<CitizenJourney.Purpose> candidates = new ArrayList<>();
        boolean atWar = state.wars().stream().anyMatch(WarState::active)
                && state.wars().stream().anyMatch(w -> w.active() && w.involves(owner.id()));
        SettlementCivilizationState fromCiv = state.findSettlementCivilization(from.id()).orElse(null);
        FactionCivilizationState factionCiv = state.findFactionCivilization(owner.id()).orElse(null);
        CivilizationCalendar.Season season = CivilizationCalendar.season(state.clock().day());
        double bandit = fromCiv == null ? 0 : fromCiv.banditPressure();
        double religious = factionCiv == null ? .42 : factionCiv.religiousInfluence();
        double mercantile = factionCiv == null ? .35 : factionCiv.mercantileTradition();
        double agrarian = factionCiv == null ? .4 : factionCiv.agrarianTradition();
        double food = from.stockpile().get(ResourceType.FOOD) + from.stockpile().get(ResourceType.GRAIN);

        // War → dispatches and envoys on the road.
        if (atWar) {
            candidates.add(CitizenJourney.Purpose.COURIER);
            candidates.add(CitizenJourney.Purpose.DIPLOMAT);
        }
        // Bandits / unsafe roads → patrols.
        if (bandit > .35 || route.security() < .45) {
            candidates.add(CitizenJourney.Purpose.PATROL);
        }
        // Harvest / field season → seasonal workers.
        if ((season == CivilizationCalendar.Season.SUMMER || season == CivilizationCalendar.Season.AUTUMN)
                && (food > 40 || from.prosperity() > .45 || agrarian > .45)) {
            candidates.add(CitizenJourney.Purpose.SEASONAL_WORKER);
        }
        // Faith pressure → pilgrims.
        if (religious > .55) {
            candidates.add(CitizenJourney.Purpose.PILGRIM);
        }
        // Trade corridors → peddlers.
        if (mercantile > .4 || route.quality() > .55 || from.prosperity() > .5) {
            candidates.add(CitizenJourney.Purpose.PEDDLER);
        }
        // Fiscal calendar → tax collectors mid-season.
        int dayOfSeason = CivilizationCalendar.dayOfSeason(state.clock().day());
        if (dayOfSeason >= 20 && dayOfSeason <= 40 && owner.treasury() > 50) {
            candidates.add(CitizenJourney.Purpose.TAX_COLLECTOR);
        }
        // Pastoral hinterland → shepherds.
        if (agrarian > .5 && from.population() < 1200) {
            candidates.add(CitizenJourney.Purpose.SHEPHERD);
        }
        // Sparse / insecure hinterland → hunters.
        if (from.population() < 900 || bandit > .25) {
            candidates.add(CitizenJourney.Purpose.HUNTER);
        }
        // Long poorly known road → explorers.
        if (route.distanceBlocks() > 1600 && route.quality() < .55) {
            candidates.add(CitizenJourney.Purpose.EXPLORER);
        }
        // Quiet peacetime baseline: keep roads alive without inventing causes.
        if (candidates.isEmpty()) {
            if (route.security() >= .5) candidates.add(CitizenJourney.Purpose.PEDDLER);
            candidates.add(CitizenJourney.Purpose.COURIER);
        }
        return List.copyOf(candidates);
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
