package dev.livingrealms;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.presentation.CivicChoreographyPlanner;
import dev.livingrealms.sim.presentation.SeasonalFarmPresentation;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Wave 12–13: presentation cues read canonical state and never mint economy. */
public final class CivicChoreographyTest {
    private CivicChoreographyTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0xC10C1CL);
        Faction faction = new Faction(1, "Cue Realm", "Cue");
        Settlement town = new Settlement(2, "Marketville", new SimPosition(100, 100), 800, 900);
        // Force village+ tier via population/housing.
        town.addPopulation(200);
        town.addHousing(200);
        town.setFoodSecurity(0.2);
        faction.addSettlement(town);
        state.addFaction(faction);

        // Advance clock to a market day (day % 7 == 0) without running full engines.
        while (state.clock().day() % 7 != 0) {
            state.clock().advance(dev.livingrealms.sim.world.SimClock.TICKS_PER_DAY);
        }

        var events = CivicChoreographyPlanner.planSettlement(state, faction, town);
        check(!events.isEmpty(), "expected presentation events");
        check(events.stream().anyMatch(e -> e.kind() == CivicChoreographyPlanner.EventKind.MARKET_DAY
                        || e.kind() == CivicChoreographyPlanner.EventKind.FAMINE_QUEUE),
                "market or famine cue present");

        double foodBefore = town.stockpile().get(dev.livingrealms.sim.faction.ResourceType.GRAIN);
        var farm = SeasonalFarmPresentation.forSettlement(state, town);
        check(farm.look() != null, "farm look");
        check(town.stockpile().get(dev.livingrealms.sim.faction.ResourceType.GRAIN) == foodBefore,
                "presentation must not mutate stockpile");

        System.out.println("PASS CivicChoreographyTest");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
