package dev.livingrealms;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.CitizenJourney;
import dev.livingrealms.sim.world.RoadLifeEngine;
import dev.livingrealms.sim.world.RoadsideSite;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.projection.CitizenJourneyMaterializationPlanner;
import dev.livingrealms.sim.world.projection.CitizenJourneyProjection;
import dev.livingrealms.sim.world.projection.RoadsideSiteMaterializationPlanner;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wave 8 physicalization gates: LOD despawn ≠ journey death; roadside sites are not settlements;
 * site spacing is independent of the 2000-block settlement clearance.
 */
public final class RoadLifeMaterializationTest {
    private RoadLifeMaterializationTest() {}

    public static void main(String[] args) {
        despawnDoesNotKillJourney();
        physicalDeathAbortsOnce();
        sitesAreNotSettlementsAndUseOwnSpacing();
        System.out.println("PASS road life materialization: despawn≠death + sites≠settlements + spacing");
    }

    private static void despawnDoesNotKillJourney() {
        SimulationState state = seeded();
        CitizenJourney journey = new CitizenJourney(state.nextId(), 0, state.factions().getFirst().id(),
                state.factions().getFirst().settlements().get(0).id(),
                state.factions().getFirst().settlements().get(1).id(),
                0, CitizenJourney.Purpose.COURIER, state.clock().day());
        journey.advance(.4);
        state.addCitizenJourney(journey);

        CitizenJourneyMaterializationPlanner planner = new CitizenJourneyMaterializationPlanner(500, 8);
        // Near player → desired.
        List<CitizenJourneyProjection> near = planner.plan(state, List.of(new SimPosition(800, 0)));
        check(!near.isEmpty(), "active journey near player must be planned");
        check(Math.abs(near.getFirst().position().x() - 800) < 1, "position follows route progress");

        // Far player → empty plan, but reconcile of existing entity must be OUTSIDE radius, not finished.
        List<CitizenJourneyProjection> far = planner.plan(state, List.of(new SimPosition(50_000, 0)));
        check(far.isEmpty(), "far player must not keep journey physical");
        Set<Long> active = new HashSet<>();
        active.add(journey.id());
        var delta = planner.reconcile(far,
                List.of(new CitizenJourneyMaterializationPlanner.Snapshot("e1", journey.id())),
                active);
        check(delta.despawns().size() == 1, "far projection must despawn");
        check(delta.despawns().getFirst().reason()
                        == CitizenJourneyMaterializationPlanner.RemovalReason.OUTSIDE_PHYSICAL_RADIUS,
                "LOD despawn reason must be outside radius, not journey finished");
        check(journey.active(), "canonical journey must survive LOD dematerialization");
        check(journey.status() == CitizenJourney.Status.ACTIVE, "status still ACTIVE after despawn plan");
    }

    private static void physicalDeathAbortsOnce() {
        SimulationState state = seeded();
        CitizenJourney journey = new CitizenJourney(state.nextId(), 0, state.factions().getFirst().id(),
                state.factions().getFirst().settlements().get(0).id(),
                state.factions().getFirst().settlements().get(1).id(),
                0, CitizenJourney.Purpose.PATROL, state.clock().day());
        state.addCitizenJourney(journey);
        check(state.recordPhysicalJourneyDeath(journey.id(), "test_kill"), "physical death maps once");
        check(journey.status() == CitizenJourney.Status.ABORTED, "journey aborted by physical death");
        check(!journey.active(), "aborted journey not active");
        check(!state.recordPhysicalJourneyDeath(journey.id(), "again"), "second death must not mint state");
        // Finished journeys despawn with JOURNEY_FINISHED, still without resurrecting.
        CitizenJourneyMaterializationPlanner planner = new CitizenJourneyMaterializationPlanner(500, 8);
        var delta = planner.reconcile(List.of(),
                List.of(new CitizenJourneyMaterializationPlanner.Snapshot("e2", journey.id())),
                Set.of());
        check(delta.despawns().getFirst().reason()
                        == CitizenJourneyMaterializationPlanner.RemovalReason.JOURNEY_FINISHED,
                "dead/finished journey entity cleans up as finished");
    }

    private static void sitesAreNotSettlementsAndUseOwnSpacing() {
        check(RoadLifeEngine.MIN_SITE_SPACING == 420.0, "roadside site spacing pin");
        check(SettlementDensitySeeder.MIN_SETTLEMENT_SPACING == 2000.0, "settlement spacing unchanged at 2000");
        check(RoadLifeEngine.MIN_SITE_SPACING < SettlementDensitySeeder.MIN_SETTLEMENT_SPACING,
                "sites denser than settlements");

        SimulationState state = seeded();
        Settlement a = state.factions().getFirst().settlements().get(0);
        Settlement b = state.factions().getFirst().settlements().get(1);
        SimPosition mid = a.position().lerp(b.position(), .5);
        RoadsideSite site = new RoadsideSite(state.nextId(), RoadsideSite.Type.WAYSTATION, mid,
                RoadsideSite.defaultName(RoadsideSite.Type.WAYSTATION, mid), a.id(), 0, state.clock().day());
        state.addRoadsideSite(site);

        check(!RoadsideSiteMaterializationPlanner.isCanonicalSettlement(state, site),
                "roadside site must not be a canonical settlement");
        check(state.findSettlement(site.id()).isEmpty(), "site id must not resolve as settlement");

        // Site spacing: another site too close is rejected; settlement spacing ignores roadside sites.
        SimPosition nearSite = new SimPosition(mid.x() + 100, mid.z());
        check(RoadsideSiteMaterializationPlanner.violatesSiteSpacing(state, nearSite),
                "sites enforce MIN_SITE_SPACING among themselves");
        SimPosition farSite = new SimPosition(mid.x() + RoadLifeEngine.MIN_SITE_SPACING + 50, mid.z());
        check(!RoadsideSiteMaterializationPlanner.violatesSiteSpacing(state, farSite),
                "sites beyond MIN_SITE_SPACING are allowed");

        // A settlement candidate next to a roadside site (but far from towns) is NOT blocked by the site.
        // SettlementDuration uses only settlement positions — prove roadside list is orthogonal.
        boolean anySettlementNearSite = state.factions().stream()
                .flatMap(f -> f.settlements().stream())
                .anyMatch(s -> s.position().distanceTo(mid) < SettlementDensitySeeder.MIN_SETTLEMENT_SPACING);
        // Midpoint between settlements 2000 apart is ~1000 from each — under 2000, so expected.
        check(anySettlementNearSite, "fixture: midpoint is inside settlement clearance of endpoints");
        // Critical: roadsideSites() are never consulted by SettlementDensitySeeder.tooCloseAny —
        // adding many sites does not change settlement count / clearance constant.
        int settlementCount = state.factions().stream().mapToInt(f -> f.settlements().size()).sum();
        for (int i = 0; i < 5; i++) {
            SimPosition p = new SimPosition(mid.x() + (i + 2) * (RoadLifeEngine.MIN_SITE_SPACING + 10), mid.z() + 20);
            if (RoadsideSiteMaterializationPlanner.violatesSiteSpacing(state, p)) continue;
            state.addRoadsideSite(new RoadsideSite(state.nextId(), RoadsideSite.Type.MILESTONE, p,
                    "Mile " + i, a.id(), 0, state.clock().day()));
        }
        check(state.factions().stream().mapToInt(f -> f.settlements().size()).sum() == settlementCount,
                "adding roadside sites must not create settlements");
        check(SettlementDensitySeeder.MIN_SETTLEMENT_SPACING == 2000.0,
                "settlement spacing pin still 2000 after site spawn");

        RoadsideSiteMaterializationPlanner planner = new RoadsideSiteMaterializationPlanner(600, 6);
        List<?> near = planner.plan(state, List.of(mid));
        check(!near.isEmpty(), "active roadside site near player is planned for physicalization");
    }

    private static SimulationState seeded() {
        SimulationState state = new SimulationState(0x524F41444D4154L);
        Faction faction = new Faction(state.nextId(), "Road Realm", "Ruler");
        Settlement from = new Settlement(state.nextId(), "Fromtown", new SimPosition(0, 0), 600, 700);
        Settlement to = new Settlement(state.nextId(), "Totown", new SimPosition(2000, 0), 500, 600);
        faction.addSettlement(from);
        faction.addSettlement(to);
        state.addFaction(faction);
        return state;
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
