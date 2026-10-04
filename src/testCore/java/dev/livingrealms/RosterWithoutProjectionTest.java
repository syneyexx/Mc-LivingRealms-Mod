package dev.livingrealms;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.Stockpile;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.social.SocialPopulationEngine;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimulationState;

/** Named settlement rosters are seeded canonically without Minecraft projection entities. */
public final class RosterWithoutProjectionTest {
    private RosterWithoutProjectionTest() {}

    public static void main(String[] args) {
        rosterCapsAfterDemoSeed();
        economyUnchangedWithoutPhysicalProjection();
        rulerNameNotDuplicatedAfterDynasty();
        System.out.println("PASS roster without projection: tier caps + canonical economy + single ruler name");
    }

    private static void rosterCapsAfterDemoSeed() {
        SimulationState state = new SimulationState(808080L);
        DemoSeeder.seed(state);
        for (Faction faction : state.factions()) {
            for (Settlement settlement : faction.settlements()) {
                int cap = SocialPopulationEngine.namedRosterCap(settlement.tier());
                long named = state.socialCitizens().stream()
                        .filter(SocialCitizen::alive)
                        .filter(c -> c.settlementId() == settlement.id() && c.projectionSlot() < cap)
                        .count();
                check(named == cap, "settlement " + settlement.name() + " tier " + settlement.tier() + " named roster");
            }
        }
    }

    private static void economyUnchangedWithoutPhysicalProjection() {
        SimulationState withRoster = new SimulationState(7L);
        DemoSeeder.seed(withRoster);
        SimulationState baseline = new SimulationState(7L);
        DemoSeeder.seed(baseline);
        for (int day = 0; day < 30; day++) {
            withRoster.advanceDays(1);
            baseline.advanceDays(1);
        }
        Faction a = withRoster.factions().iterator().next();
        Faction b = baseline.factions().iterator().next();
        Stockpile sa = a.stockpile();
        Stockpile sb = b.stockpile();
        for (ResourceType type : ResourceType.values()) {
            check(Math.abs(sa.get(type) - sb.get(type)) < 1e-9, "economy unchanged for " + type);
        }
    }

    private static void rulerNameNotDuplicatedAfterDynasty() {
        SimulationState state = new SimulationState(909090L);
        DemoSeeder.seed(state);
        state.advanceDays(2);
        for (Faction faction : state.factions()) {
            long namedRulers = state.socialCitizens().stream()
                    .filter(SocialCitizen::alive)
                    .filter(c -> c.factionId() == faction.id() && c.name().equals(faction.rulerName()))
                    .count();
            check(namedRulers <= 1, "at most one citizen named like ruler for " + faction.name());
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
