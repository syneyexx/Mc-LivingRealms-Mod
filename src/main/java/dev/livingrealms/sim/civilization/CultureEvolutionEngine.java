package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimulationState;

/**
 * Long-cadence culture trait and law-policy evolution.
 */
public final class CultureEvolutionEngine {
    private CultureEvolutionEngine() {}

    public static void evolveCultureAndLaw(SimulationState state){
        for(Faction faction:state.factions()){
            FactionCivilizationState c=state.ensureFactionCivilization(faction.id());double trade=faction.relations().values().stream().filter(DiplomaticRelation::tradeAgreement).count()/Math.max(1.0,faction.relations().size());double war=state.wars().stream().anyMatch(w->w.active()&&(w.attackerFactionId()==faction.id()||w.defenderFactionId()==faction.id()))?1:0;double farm=faction.settlements().stream().filter(s->CivilizationSupport.has(s,"farm:")).count()/Math.max(1.0,faction.settlements().size());double art=faction.settlements().stream().filter(s->CivilizationSupport.has(s,"monument:")||CivilizationSupport.has(s,"temple:")).count()/Math.max(1.0,faction.settlements().size());c.setCultureTraits(CivilizationSupport.lerp(c.mercantileTradition(),trade,.04),CivilizationSupport.lerp(c.martialTradition(),war,.025),CivilizationSupport.lerp(c.agrarianTradition(),farm,.03),CivilizationSupport.lerp(c.artisticTradition(),art,.025),c.religiousTolerance(),CivilizationSupport.lerp(c.openness(),trade,.02));double unrest=faction.settlements().stream().mapToDouble(Settlement::unrest).average().orElse(0);c.setLawPolicy(CivilizationSupport.lerp(c.lawSeverity(),Mathx.clamp(faction.government().lawEnforcement()*.7+unrest*.3,0,1),.03),CivilizationSupport.lerp(c.dueProcess(),Mathx.clamp(.35+c.education()*.45-faction.government().corruption()*.25,0,1),.03),CivilizationSupport.lerp(c.refugeeAcceptance(),Mathx.clamp(.65+c.openness()*.25-unrest*.4,0,1),.03));
        }
    }
}
