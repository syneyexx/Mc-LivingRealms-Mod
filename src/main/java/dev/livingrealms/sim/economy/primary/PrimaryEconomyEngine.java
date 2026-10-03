package dev.livingrealms.sim.economy.primary;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.SimulationState;

/** Daily production from completed, terrain-suitable primary-industry structures. */
public final class PrimaryEconomyEngine {
    public void simulateDay(SimulationState state){
        for(Faction faction:state.factions())for(Settlement settlement:faction.settlements()){
            int mines=count(settlement,"mine:");int lumber=count(settlement,"lumber_camp:");int fisheries=count(settlement,"fishery:");
            double labor=Math.max(10,settlement.population())*(.45+.55*settlement.employment());
            double tech=.75+.5*Math.min(1.5,faction.technology());
            if(lumber>0){double s=PrimaryEconomySuitability.score(state,settlement,PrimaryEconomyKind.LUMBER_CAMP);faction.stockpile().add(ResourceType.WOOD,labor*.018*lumber*s*tech);}
            if(fisheries>0){double s=PrimaryEconomySuitability.score(state,settlement,PrimaryEconomyKind.FISHERY);faction.stockpile().add(ResourceType.FOOD,labor*.026*fisheries*s*tech);}
            if(mines>0){double s=PrimaryEconomySuitability.score(state,settlement,PrimaryEconomyKind.MINE);double base=labor*.008*mines*s*tech;faction.stockpile().add(ResourceType.STONE,base*1.9);faction.stockpile().add(ResourceType.IRON,base*.72);faction.stockpile().add(ResourceType.COAL,base*.52);faction.stockpile().add(ResourceType.COPPER,base*.27);faction.stockpile().add(ResourceType.GOLD,base*.035);}
        }
    }
    private static int count(Settlement settlement,String prefix){return (int)settlement.completedConstruction().stream().filter(k->k.startsWith(prefix)).count();}
}
