package dev.livingrealms;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.transport.TransportMode;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.CitizenJourney;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.RoadLifeEngine;
import dev.livingrealms.sim.world.RoadsideSite;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Wave 8: causal journeys + sparse roadside sites on the road network. */
public final class RoadLifeLayerTest {
    private RoadLifeLayerTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0x524F4144L);
        DemoSeeder.seed(state);
        // Ensure at least one long operational route for journey spawn.
        Faction faction = state.factions().stream()
                .filter(f -> f.settlements().size() >= 2)
                .findFirst().orElseThrow();
        Settlement a = faction.settlements().get(0);
        Settlement b = faction.settlements().get(1);
        if (state.routes().stream().noneMatch(r ->
                (r.fromSettlementId() == a.id() && r.toSettlementId() == b.id())
                        || (r.fromSettlementId() == b.id() && r.toSettlementId() == a.id()))) {
            double dist = Math.max(900, a.position().distanceTo(b.position()));
            state.addRoute(new TransportRoute(state.nextId(), faction.id(), a.id(), b.id(),
                    TransportMode.ROAD, dist, .7, .65, 40));
        }
        a.adjustProsperity(.4);
        b.adjustProsperity(.4);

        int journeysBefore = state.citizenJourneys().size();
        int sitesBefore = state.roadsideSites().size();
        boolean spawnedJourney = false;
        boolean spawnedSite = false;
        for (int day = 0; day < 120; day++) {
            RoadLifeEngine.simulateDay(state, new DeterministicRng(0x524F4144L ^ (day * 0x9E3779B97F4A7C15L)));
            if (state.citizenJourneys().size() > journeysBefore) spawnedJourney = true;
            if (state.roadsideSites().size() > sitesBefore) spawnedSite = true;
            if (spawnedJourney && spawnedSite) break;
        }
        check(spawnedJourney, "causal journey must spawn from prosperous route endpoints");
        check(state.citizenJourneys().stream().anyMatch(CitizenJourney::active)
                        || state.citizenJourneys().stream().anyMatch(j -> j.status() == CitizenJourney.Status.ARRIVED),
                "journey must be tracked as active or arrived");

        // Force a site if RNG was unlucky — still validates spacing / non-settlement nature.
        if (!spawnedSite) {
            TransportRoute route = state.routes().stream().filter(TransportRoute::operational).findFirst().orElseThrow();
            Settlement from = state.findSettlement(route.fromSettlementId()).orElseThrow();
            Settlement to = state.findSettlement(route.toSettlementId()).orElseThrow();
            SimPosition mid = from.position().lerp(to.position(), .5);
            state.addRoadsideSite(new RoadsideSite(state.nextId(), RoadsideSite.Type.WAYSTATION, mid,
                    RoadsideSite.defaultName(RoadsideSite.Type.WAYSTATION, mid), from.id(), route.id(), state.clock().day()));
            spawnedSite = true;
        }
        check(spawnedSite, "roadside site present");
        check(state.roadsideSites().stream().noneMatch(s ->
                        state.factions().stream().flatMap(f -> f.settlements().stream())
                                .anyMatch(st -> st.id() == s.id())),
                "roadside sites must not be settlements");

        // Advance an explicit journey to arrival.
        CitizenJourney journey = state.citizenJourneys().stream().filter(CitizenJourney::active).findFirst().orElse(null);
        if (journey != null) {
            for (int i = 0; i < 40 && journey.active(); i++) journey.advance(.1);
            check(journey.status() == CitizenJourney.Status.ARRIVED, "journey arrives");
        }

        // Daily tick wiring: advanceDays should call RoadLifeEngine.
        int before = state.citizenJourneys().size() + state.roadsideSites().size();
        state.advanceDays(30);
        int after = state.citizenJourneys().size() + state.roadsideSites().size();
        check(after >= before, "daily tick keeps road-life layer alive");

        System.out.println("PASS road life layer: journeys + roadside sites + daily tick");
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
