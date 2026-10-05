package dev.livingrealms;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.civilization.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.*;

/**
 * Smoke gate for Waves 4–5 architecture extractions: SimulationEngine façade and
 * focused civilization lifecycle engines remain callable without changing day cadence.
 */
public final class ArchitectureLifecycleEnginesTest {
    private ArchitectureLifecycleEnginesTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0xA4C417EC7L);
        Faction realm = new Faction(state.nextId(), "Arch Reach", "Lady Vera");
        Settlement town = new Settlement(state.nextId(), "Halden", new SimPosition(0, 0), 180, 220);
        town.setFoodSecurity(.55);
        town.setPublicOrder(.6);
        town.markConstructionCompleted("well:0");
        town.markConstructionCompleted("clinic:0");
        town.markConstructionCompleted("farm:0");
        town.markConstructionCompleted("school:0");
        town.markConstructionCompleted("temple:0");
        realm.addSettlement(town);
        realm.stockpile().add(ResourceType.FOOD, 400);
        realm.addTreasury(800);
        state.addFaction(realm);

        SocialCitizen a = state.ensureSocialCitizen(realm.id(), town.id(), 0, CitizenRole.FARMER);
        SocialCitizen b = state.ensureSocialCitizen(realm.id(), town.id(), 1, CitizenRole.HEALER);
        a.relationship("citizen:" + b.id()).setFamilyBond(dev.livingrealms.sim.social.FamilyBond.PARTNER);
        b.relationship("citizen:" + a.id()).setFamilyBond(dev.livingrealms.sim.social.FamilyBond.PARTNER);

        DeterministicRng rng = new DeterministicRng(state.seed() ^ 0xD1B54A32D192ED03L);

        HouseholdLifecycleEngine.ensureHouseholds(state);
        check(state.households().stream().anyMatch(h -> h.active() && h.memberIds().contains(a.id())), "household ensure");

        SettlementCivilizationState civ = state.ensureSettlementCivilization(town.id(), realm.id());
        civ.adjustDisease(.7);
        EpidemicEngine.simulateEpidemics(state, rng);
        check(state.epidemics().stream().anyMatch(e -> e.active() && e.settlementId() == town.id())
                || civ.diseasePressure() > .5, "epidemic or disease pressure path");

        civ.adjustRefugeePressure(.7);
        civ.adjustBanditPressure(.8);
        town.setFoodSecurity(.3);
        MigrationEngine.considerMigration(state);
        // May or may not spawn depending on cadence helpers; ensure call does not throw and state stays valid.
        check(state.migrationGroups() != null, "migration engine callable");

        KnowledgeEngine.diffuseKnowledge(state);
        KnowledgeEngine.trainApprentices(state);
        DynastyLifecycleEngine.ensureCulturalPoliciesAndDynasties(state);
        check(state.findFactionCivilization(realm.id()).isPresent(), "dynasty/culture bootstrap");

        CultureEvolutionEngine.evolveCultureAndLaw(state);
        HistoricalLegacyEngine.simulateRuins(state, rng);
        LawLifecycleEngine.advanceJusticeCases(state);

        // Full day pipeline through SimulationState façade → SimulationEngine
        long dayBefore = state.clock().day();
        state.advanceDays(1);
        check(state.clock().day() == dayBefore + 1, "SimulationEngine advanceDays clock");
        state.advancePresentationPulse(0.25);

        new CivilizationLifecycleEngine().simulateDay(state, new DeterministicRng(state.seed() ^ state.clock().day() ^ 0xD1B54A32D192ED03L));

        System.out.println("PASS architecture lifecycle engines: household/epidemic/migration/knowledge/dynasty + SimulationEngine façade");
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
