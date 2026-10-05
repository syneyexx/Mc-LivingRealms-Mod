package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.social.CitizenMemory;
import dev.livingrealms.sim.social.FamilyBond;
import dev.livingrealms.sim.social.MemoryType;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Canonical demographic authority for ordinary settlement growth, aggregate monthly
 * births/deaths, and named-citizen mortality. Household child births remain in
 * {@link HouseholdLifecycleEngine}; epidemic mortality remains in {@link EpidemicEngine}.
 */
public final class DemographyEngine {
    private DemographyEngine() {}

    /**
     * Healthy settlements with spare housing attract a small bounded stream of ordinary settlers.
     */
    public static void simulateSettlementAttraction(SimulationState state) {
        long day = state.clock().day();
        for (Faction owner : state.factions()) for (Settlement settlement : owner.settlements()) {
            int free = settlement.housing() - settlement.population();
            if (free < 8 || settlement.foodSecurity() <= .62 || settlement.publicOrder() <= .45) continue;
            SettlementCivilizationState civilization =
                    state.ensureSettlementCivilization(settlement.id(), owner.id());
            if (civilization.diseasePressure() >= .60
                    || Math.floorMod(day + settlement.id(), 5L) != 0L) continue;
            int arrivals = Math.min(4, Math.max(1, free / 24));
            arrivals = Math.min(arrivals, free);
            if (arrivals <= 0) continue;
            settlement.addPopulation(arrivals);
            civilization.adjustRefugeePressure(-.01);
            state.history().add(new WorldEvent(day, "settlers_arrived",
                    "settlement=" + settlement.id() + ", people=" + arrivals));
        }
    }

    /**
     * Monthly aggregate births/deaths. This is the population-count authority; named household
     * births are separate social records and do not replace aggregate settlement population.
     */
    public static void simulateMonthlyDemography(SimulationState state) {
        long day = state.clock().day();
        for (Faction owner : state.factions()) for (Settlement settlement : owner.settlements()) {
            if (settlement.population() <= 0) continue;
            SettlementCivilizationState civilization =
                    state.ensureSettlementCivilization(settlement.id(), owner.id());
            double freeHousing = Mathx.clamp(
                    (settlement.housing() - settlement.population())
                            / (double) Math.max(20, settlement.population()),
                    0, 1);
            double monthlyBirthRate = .0010
                    + .0014 * settlement.foodSecurity()
                    + .0008 * Math.min(.5, freeHousing)
                    + .00045 * civilization.culturalCohesion()
                    - .0012 * civilization.diseasePressure();
            double monthlyDeathRate = .00045
                    + .0022 * civilization.diseasePressure()
                    + .0016 * (1 - settlement.foodSecurity())
                    + .0006 * (1 - civilization.waterSecurity());
            int births = (int) Math.floor(settlement.population() * Math.max(0, monthlyBirthRate));
            int deaths = (int) Math.floor(settlement.population() * Math.max(0, monthlyDeathRate));
            if (settlement.housingShortage() > 0) births = Math.min(births, Math.max(0, deaths));
            births = Math.min(births,
                    Math.max(0, settlement.housing() - settlement.population() + deaths));
            int delta = births - deaths;
            if (delta != 0) settlement.addPopulation(delta);
            if (births + deaths > 0) {
                state.history().add(new WorldEvent(day, "demography",
                        "settlement=" + settlement.id()
                                + ", births=" + births
                                + ", deaths=" + deaths
                                + ", population=" + settlement.population()));
            }
            if (civilization.education() > .55) {
                owner.advanceTechnology(.00015 * civilization.education()
                        * Math.sqrt(Math.max(1, settlement.population())));
            }
        }
    }

    /**
     * Applies disease/age health drift to named citizens and records canonical deaths.
     */
    public static void updateNamedPeople(SimulationState state, DeterministicRng rng) {
        long day = state.clock().day();
        List<SocialCitizen> deaths = new ArrayList<>();
        for (SocialCitizen citizen : state.socialCitizens()) if (citizen.alive()) {
            Faction owner = state.findSettlementOwner(citizen.settlementId()).orElse(null);
            if (owner == null) continue;
            SettlementCivilizationState civilization =
                    state.ensureSettlementCivilization(citizen.settlementId(), owner.id());
            boolean healer = citizen.role() == CitizenRole.HEALER
                    || CivilizationSupport.specialistShare(
                            state, citizen.settlementId(), CitizenRole.HEALER) > 0;
            citizen.adjustHealth(-civilization.diseasePressure() * .00055
                    + (healer ? .00018 : 0)
                    + civilization.sanitation() * .00008);
            if (day % 30 == Math.floorMod(citizen.id(), 30L)) {
                int age = citizen.ageYears(day);
                double oldAge = Math.max(0, age - 68) * .0008
                        + Math.max(0, age - 82) * .003;
                double illness = civilization.diseasePressure()
                        * .012 * (1 - citizen.health());
                if (citizen.health() <= .02 || rng.chance(oldAge + illness)) deaths.add(citizen);
            }
        }
        for (SocialCitizen citizen : deaths) dieNamedCitizen(state, citizen, "age_or_illness");
    }

    private static void dieNamedCitizen(
            SimulationState state, SocialCitizen person, String cause) {
        if (!person.alive()) return;
        String key = "citizen:" + person.id();
        SocialCitizen heir = null;
        for (var entry : person.relationships().entrySet()) {
            FamilyBond bond = entry.getValue().familyBond();
            if (bond != FamilyBond.PARTNER
                    && bond != FamilyBond.CHILD
                    && bond != FamilyBond.ADOPTED_CHILD) continue;
            long id = CivilizationSupport.parseCitizenKey(entry.getKey());
            if (id <= 0) continue;
            heir = state.findSocialCitizen(id).filter(SocialCitizen::alive).orElse(null);
            if (heir != null) break;
        }
        if (heir != null && person.money() > 0) {
            double inherited = person.money() * .8;
            person.addMoney(-inherited);
            heir.addMoney(inherited);
            Settlement home = state.findSettlement(person.settlementId()).orElse(null);
            if (home != null) {
                heir.remember(new CitizenMemory(
                        state.clock().day(),
                        MemoryType.FAMILY_EVENT,
                        key,
                        "self",
                        "I inherited possessions after " + person.name() + " died.",
                        home.position(),
                        .7,
                        1));
            }
        }
        state.recordPhysicalCitizenDeath(person.settlementId(), person.id(), cause);
    }
}
