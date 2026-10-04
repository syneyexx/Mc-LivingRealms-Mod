package dev.livingrealms;

import dev.livingrealms.sim.civilization.CivicEvent;
import dev.livingrealms.sim.civilization.CivicEventType;
import dev.livingrealms.sim.civilization.CivicFestivalDecorationPlanner;
import dev.livingrealms.sim.civilization.CivilizationCalendar;
import dev.livingrealms.sim.civilization.FaithCatalog;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/**
 * Guards the holy-day / civic-event cadence: religious rites must fire on actual holy days,
 * not only on the 30-day festival polling cadence.
 */
public final class CivicHolyDayFestivalTest {
    private CivicHolyDayFestivalTest() {}

    public static void main(String[] args) {
        holyDayCreatesReligiousRitualOnExactDay();
        nonHolyDayDoesNotSpamRitualsEveryDay();
        festivalPlannerProducesDeterministicDecorations();
        System.out.println("PASS civic holy-day festivals: exact-day rites + bounded cadence + decoration planner");
    }

    private static void holyDayCreatesReligiousRitualOnExactDay() {
        SimulationState state = seededTempleRealm(0xC1C1C001L);
        Faction faction = state.factions().getFirst();
        FaithCatalog.FaithProfile faith = FaithCatalog.of(state.ensureFactionCivilization(faction.id()).faithName());
        int holyDoy = faith.holyDaysOfYear()[0];
        long target = holyDoy;
        // Avoid landing on day 0 edge; step within first year.
        if (target == 0) target = faith.holyDaysOfYear().length > 1 ? faith.holyDaysOfYear()[1] : 30;
        // advanceDays simulates day D then increments the clock, so the holy day is opened while clock is D-1.
        state.advanceDays((int) target - 1);
        check(faith.isHolyDay(state.clock().day() + 1), "next simulated day must be the faith holy day");
        state.advanceDays(1);
        CivicEvent rite = state.civicEvents().stream()
                .filter(e -> e.type() == CivicEventType.RELIGIOUS_RITUAL)
                .filter(e -> e.startDay() == state.clock().day() - 1 || e.startDay() == state.clock().day())
                .findFirst().orElse(null);
        check(rite != null, "holy day must open a visible RELIGIOUS_RITUAL civic event (not only on day%30)");
        check(rite.title().contains("Rite") || rite.title().contains(faith.primaryDeity()),
                "ritual title should name deity/rite: " + rite.title());
        check(Math.abs(CivilizationCalendar.dayOfYear(rite.startDay()) - CivilizationCalendar.dayOfYear(target)) <= 1
                        || Math.abs(rite.startDay() - target) <= 1,
                "ritual must be scheduled on/adjacent to the holy day, got start=" + rite.startDay() + " target=" + target);
    }

    private static void nonHolyDayDoesNotSpamRitualsEveryDay() {
        SimulationState state = seededTempleRealm(0xC1C1C002L);
        Faction faction = state.factions().getFirst();
        FaithCatalog.FaithProfile faith = FaithCatalog.of(state.ensureFactionCivilization(faction.id()).faithName());
        int opened = 0;
        for (int i = 0; i < 29; i++) {
            state.advanceDays(1);
            long day = state.clock().day();
            if (faith.isHolyDay(day) || day % 30 == 0) continue;
            boolean newRitual = state.civicEvents().stream()
                    .anyMatch(e -> e.type() == CivicEventType.RELIGIOUS_RITUAL && e.startDay() == day);
            if (newRitual) opened++;
        }
        check(opened == 0, "non-holy ordinary days must not spam religious rituals: opened=" + opened);
    }

    private static void festivalPlannerProducesDeterministicDecorations() {
        SimulationState state = seededTempleRealm(0xC1C1C003L);
        Settlement settlement = state.factions().getFirst().settlements().getFirst();
        CivicEvent fair = new CivicEvent(state.nextId(), state.factions().getFirst().id(), settlement.id(),
                state.clock().day(), state.clock().day() + 2, CivicEventType.MARKET_FAIR, settlement.name() + " Market Fair", .8);
        state.addCivicEvent(fair);
        var a = CivicFestivalDecorationPlanner.planActive(state);
        var b = CivicFestivalDecorationPlanner.planActive(state);
        check(!a.isEmpty(), "active festival must plan decorations");
        check(a.size() == b.size(), "planner must be deterministic in size");
        for (int i = 0; i < a.size(); i++) {
            check(a.get(i).eventId() == b.get(i).eventId(), "event id stable");
            check(a.get(i).offsetX() == b.get(i).offsetX() && a.get(i).offsetZ() == b.get(i).offsetZ(), "offsets stable");
            check(a.get(i).kind() == b.get(i).kind(), "kind stable");
        }
        fair.finish();
        check(CivicFestivalDecorationPlanner.planActive(state).isEmpty(), "finished festival must plan zero decorations");
    }

    private static SimulationState seededTempleRealm(long seed) {
        SimulationState state = new SimulationState(seed);
        Faction faction = new Faction(state.nextId(), "Temple March", "Abbot");
        Settlement settlement = new Settlement(state.nextId(), "Riteford", new SimPosition(120, -40), 420, 480);
        settlement.markConstructionCompleted("temple:0");
        settlement.markConstructionCompleted("market:0");
        settlement.markConstructionCompleted("tavern:0");
        settlement.markConstructionCompleted("farm:0");
        settlement.setFoodSecurity(.8);
        settlement.setPublicOrder(.75);
        faction.addSettlement(settlement);
        faction.stockpile().add(ResourceType.FOOD, 400);
        faction.addTreasury(800);
        state.addFaction(faction);
        state.ensureFactionCivilization(faction.id());
        return state;
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
