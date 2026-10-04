package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;

/** Applies faith calendars to local consumption, church tithe and holy-day cohesion. */
public final class FaithEconomyHooks {
    private FaithEconomyHooks() {}

    public static double foodNeedMultiplier(SimulationState state, Faction faction, Settlement settlement, long day) {
        FaithCatalog.FaithProfile faith = FaithCatalog.of(state.ensureFactionCivilization(faction.id()).faithName());
        if (!faith.isFastingDay(day)) return 1.0;
        // Fasting lowers meat/food demand; temples make observance stronger (and less unrest).
        boolean temple = settlement.completedConstruction().stream().anyMatch(k -> k.startsWith("temple:"));
        return temple ? 0.72 : 0.85;
    }

    public static void applyHolyDayAndTithe(SimulationState state, Faction faction, Settlement settlement, long day) {
        FactionCivilizationState civ = state.ensureFactionCivilization(faction.id());
        FaithCatalog.FaithProfile faith = FaithCatalog.of(civ.faithName());
        boolean temple = settlement.completedConstruction().stream().anyMatch(k -> k.startsWith("temple:"));
        if (faith.isHolyDay(day)) {
            SettlementCivilizationState sc = state.ensureSettlementCivilization(settlement.id(), faction.id());
            sc.adjustCohesion(.004 + (temple ? .006 : 0));
            civ.approach(civ.culturalInfluence(), Math.min(1, civ.religiousInfluence() + .01), civ.education(),
                    civ.propaganda(), civ.intelligence(), .003);
            settlement.adjustUnrest(-.002);
            if (temple && Math.floorMod(settlement.id() + day, 17) == 0) {
                state.history().add(new WorldEvent(day, "holy_day",
                        settlement.name() + " observes " + faith.name() + " (" + faith.primaryDeity()
                                + ") • order=" + faith.holyOrder()));
            }
        }
        if (!temple) return;
        // Church tithe: small surplus food levy that funds religious influence, not free faction minting.
        double local = settlement.stockpile().get(ResourceType.FOOD);
        double reserve = settlement.population() * .20 * 40;
        double surplus = Math.max(0, local - reserve);
        double take = surplus * Mathx.clamp(faith.churchTitheShare() * (.5 + civ.religiousInfluence()), .01, .12);
        if (take <= 0) return;
        settlement.stockpile().take(ResourceType.FOOD, take);
        // Ritual consumption + alms: part vanishes as charity/ceremony, part returns as prosperity.
        settlement.adjustProsperity(Math.min(.002, take / Math.max(50, settlement.population()) * .01));
        civ.approach(civ.culturalInfluence(), Math.min(1, civ.religiousInfluence() + .002), civ.education(),
                civ.propaganda(), civ.intelligence(), .001);
    }
}
