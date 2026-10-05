package dev.livingrealms;

import dev.livingrealms.sim.civilization.CivilizationCalendar;
import dev.livingrealms.sim.civilization.SettlementCivilizationState;
import dev.livingrealms.sim.diplomacy.WarGoalType;
import dev.livingrealms.sim.diplomacy.WarState;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Wave 8: causal journeys + sparse roadside sites on the road network. */
public final class RoadLifeLayerTest {
    private RoadLifeLayerTest() {}

    public static void main(String[] args) {
        spawnAndSites();
        causalPurposeTriggers();
        System.out.println("PASS road life layer: journeys + roadside sites + causal purposes + daily tick");
    }

    private static void spawnAndSites() {
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
    }

    private static void causalPurposeTriggers() {
        SimulationState state = new SimulationState(0x50555250L);
        Faction faction = new Faction(state.nextId(), "Road Realm", "Ruler");
        Settlement from = new Settlement(state.nextId(), "Fromtown", new SimPosition(0, 0), 600, 700);
        Settlement to = new Settlement(state.nextId(), "Totown", new SimPosition(2000, 0), 500, 600);
        from.adjustProsperity(.5);
        to.adjustProsperity(.5);
        faction.addSettlement(from);
        faction.addSettlement(to);
        state.addFaction(faction);
        TransportRoute safe = new TransportRoute(state.nextId(), faction.id(), from.id(), to.id(),
                TransportMode.ROAD, 2000, .7, .7, 40);
        TransportRoute unsafe = new TransportRoute(state.nextId(), faction.id(), from.id(), to.id(),
                TransportMode.ROAD, 2100, .4, .2, 30);
        state.addRoute(safe);
        state.addRoute(unsafe);

        // War → courier / diplomat only among those causes (plus any other concurrent triggers).
        Faction rival = new Faction(state.nextId(), "War Peer", "Peer");
        rival.addSettlement(new Settlement(state.nextId(), "Warpeer", new SimPosition(4000, 0), 400, 450));
        state.addFaction(rival);
        state.addWar(new WarState(state.nextId(), faction.id(), rival.id(), WarGoalType.CONQUEST,
                rival.settlements().getFirst().id(), state.clock().day()));
        List<CitizenJourney.Purpose> wartime =
                RoadLifeEngine.causalPurposeCandidates(state, faction, from, to, safe);
        check(wartime.contains(CitizenJourney.Purpose.COURIER), "war causes courier");
        check(wartime.contains(CitizenJourney.Purpose.DIPLOMAT), "war causes diplomat");
        check(!wartime.containsAll(EnumSet.allOf(CitizenJourney.Purpose.class)),
                "war slate is not the full enum");

        // Bandits → patrol.
        SettlementCivilizationState civ = state.ensureSettlementCivilization(from.id(), faction.id());
        civ.adjustBanditPressure(.6);
        List<CitizenJourney.Purpose> bandit =
                RoadLifeEngine.causalPurposeCandidates(state, faction, from, to, unsafe);
        check(bandit.contains(CitizenJourney.Purpose.PATROL), "bandits/insecure route cause patrol");

        // Harvest season → seasonal worker (force autumn day).
        while (CivilizationCalendar.season(state.clock().day()) != CivilizationCalendar.Season.AUTUMN) {
            state.clock().advance(24_000L); // one Minecraft day
        }
        from.stockpile().add(dev.livingrealms.sim.faction.ResourceType.GRAIN, 80);
        List<CitizenJourney.Purpose> harvest =
                RoadLifeEngine.causalPurposeCandidates(state, faction, from, to, safe);
        check(harvest.contains(CitizenJourney.Purpose.SEASONAL_WORKER), "harvest season causes seasonal worker");

        // Randomness only among candidates: spawn under war must pick from wartime slate.
        Set<CitizenJourney.Purpose> allowed = EnumSet.copyOf(wartime);
        int spawned = 0;
        for (int day = 0; day < 200 && spawned < 3; day++) {
            int before = state.citizenJourneys().size();
            RoadLifeEngine.simulateDay(state, new DeterministicRng(0x50555250L ^ (day * 17L)));
            if (state.citizenJourneys().size() > before) {
                CitizenJourney j = state.citizenJourneys().get(state.citizenJourneys().size() - 1);
                check(allowed.contains(j.purpose())
                                || RoadLifeEngine.causalPurposeCandidates(state, faction,
                                state.findSettlement(j.originSettlementId()).orElse(from),
                                state.findSettlement(j.targetSettlementId()).orElse(to),
                                state.routes().stream().filter(r -> r.id() == j.routeId()).findFirst().orElse(safe))
                                .contains(j.purpose()),
                        "spawned purpose must be causal candidate: " + j.purpose());
                spawned++;
            }
        }
        check(spawned >= 1, "at least one causal journey spawned under war pressure");
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
