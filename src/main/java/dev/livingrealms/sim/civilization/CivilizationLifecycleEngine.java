package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimulationState;
import java.util.Objects;

/**
 * Deep civilisation lifecycle layered on the existing canonical faction/settlement/economy/law systems.
 * This class owns no duplicate population/economy authority: it creates persistent social records and
 * writes consequences back through Settlement, Faction, CrimeLedger, GovernmentState and WorldHistory.
 * Day cadence is preserved exactly; domain work lives in focused engines under this package.
 */
public final class CivilizationLifecycleEngine {
    public void simulateDay(SimulationState state,DeterministicRng rng){
        Objects.requireNonNull(state);Objects.requireNonNull(rng);
        DynastyLifecycleEngine.ensureCulturalPoliciesAndDynasties(state);
        HouseholdLifecycleEngine.ensureHouseholds(state);
        EpidemicEngine.simulateEpidemics(state,rng);
        MigrationEngine.advanceMigrationGroups(state,rng);
        PiracyEngine.simulatePiracy(state,rng);
        CivicEventEngine.simulateCivicEvents(state,rng);
        IntelligencePropagandaEngine.simulateIntelligenceOperations(state,rng);
        IntelligencePropagandaEngine.simulatePropagandaCampaigns(state,rng);
        HistoricalLegacyEngine.simulateRuins(state,rng);
        AssistanceBoardEngine.simulateAssistanceTasks(state);
        long day=state.clock().day();
        if(day%7==0){KnowledgeEngine.diffuseKnowledge(state);EpidemicEngine.spreadDiseaseAlongTrade(state,rng);LawLifecycleEngine.advanceJusticeCases(state);}
        if(day%30==0){HouseholdLifecycleEngine.simulateHouseholdLifecycle(state,rng);KnowledgeEngine.trainApprentices(state);HouseholdLifecycleEngine.payWages(state);LawLifecycleEngine.spawnNpcCrime(state,rng);HiddenCacheEngine.simulateHiddenCaches(state);DynastyLifecycleEngine.simulatePoliticalMarriages(state,rng);DynastyLifecycleEngine.refreshDynasties(state);}
        if(day%90==0)CultureEvolutionEngine.evolveCultureAndLaw(state);
    }

    /** Called by CivilizationEngine instead of instantaneous source->target population teleportation. */
    public void considerMigration(SimulationState state){
        MigrationEngine.considerMigration(state);
    }
}
