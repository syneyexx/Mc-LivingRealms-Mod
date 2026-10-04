package dev.livingrealms.sim.government;

import dev.livingrealms.sim.civilization.LegendRecord;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.ArrayList;

/** Advances monumental projects when treasury, materials and labor allow. */
public final class GrandProjectEngine {
    public void simulateDay(SimulationState state,DeterministicRng rng){
        maybeStart(state,rng);
        for(GrandProject project:new ArrayList<>(state.grandProjects())){
            if(!project.active()||project.complete())continue;
            Faction faction=state.findFaction(project.sponsorFactionId()).orElse(null);
            Settlement settlement=state.findSettlement(project.settlementId()).orElse(null);
            if(faction==null||settlement==null){project.setPauseReason("missing_site");continue;}
            boolean war=state.wars().stream().anyMatch(w->w.active()&&w.involves(faction.id()));
            if(faction.treasury()<8){project.setPauseReason("treasury");continue;}
            if(war&&project.type()!=GrandProjectType.FORTRESS&&project.type()!=GrandProjectType.CITY_WALLS){project.setPauseReason("war");continue;}
            if(settlement.unrest()>.78){project.setPauseReason("unrest");continue;}
            double food=Math.min(4,settlement.stockpile().get(ResourceType.FOOD)+faction.stockpile().get(ResourceType.FOOD)*.1);
            double timber=Math.min(3,settlement.stockpile().get(ResourceType.WOOD)+faction.stockpile().get(ResourceType.WOOD)*.15);
            double stone=Math.min(3,settlement.stockpile().get(ResourceType.STONE)+faction.stockpile().get(ResourceType.STONE)*.15);
            double iron=Math.min(1.2,settlement.stockpile().get(ResourceType.IRON)+faction.stockpile().get(ResourceType.IRON)*.1);
            if(food<.5||timber<.4||stone<.4){project.setPauseReason("shortage");continue;}
            double spend=Math.min(faction.treasury()*.02,Math.max(2,project.cost()/400.0));
            int labor=Math.max(1,(int)Math.round(2+settlement.population()*.002*(1-settlement.unrest())));
            take(settlement,faction,ResourceType.FOOD,food);take(settlement,faction,ResourceType.WOOD,timber);
            take(settlement,faction,ResourceType.STONE,stone);take(settlement,faction,ResourceType.IRON,iron);
            faction.addTreasury(-spend);
            boolean wasIncomplete=!project.complete();
            project.advance(spend,food,timber,stone,iron,labor);
            if(wasIncomplete&&project.complete())onComplete(state,faction,settlement,project);
        }
    }

    private static void maybeStart(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();
        if(day%45!=0)return;
        for(Faction faction:state.factions()){
            if(faction.treasury()<280||faction.settlements().isEmpty())continue;
            if(state.grandProjects().stream().anyMatch(p->p.active()&&p.sponsorFactionId()==faction.id()))continue;
            if(state.grandProjects().size()>=SimulationState.MAX_GRAND_PROJECTS)return;
            Settlement site=faction.settlements().stream().max(java.util.Comparator.comparingInt(Settlement::population)).orElse(null);
            if(site==null||site.population()<220)continue;
            GrandProjectType[] types=GrandProjectType.values();
            GrandProjectType type=types[Math.floorMod((int)(faction.id()+day/45),types.length)];
            if(rng.chance(.55))continue;
            GrandProject project=new GrandProject(state.nextId(),faction.id(),site.id(),type,day);
            state.addGrandProject(project);
            state.history().add(new WorldEvent(day,"grand_project_started","faction="+faction.id()+", settlement="+site.id()+", type="+type));
        }
    }

    private static void onComplete(SimulationState state,Faction faction,Settlement settlement,GrandProject project){
        if(project.effectInfrastructure())settlement.improveInfrastructure(.08+project.type().baseCost()*.00004);
        if(project.effectProsperity())settlement.adjustProsperity(.05);settlement.adjustUnrest(-.04);
        if(project.effectLegitimacy())faction.government().adjustLegitimacy(.04);
        if(project.effectLegend()&&state.legends().size()<SimulationState.MAX_LEGENDS){
            state.addLegend(new LegendRecord(state.nextId(),state.clock().day(),faction.id(),settlement.id(),"project:"+project.id(),"The "+pretty(project.type()),"Completion of the "+pretty(project.type())+" in "+settlement.name()+".",.55));
        }
        state.history().add(new WorldEvent(state.clock().day(),"grand_project_complete","faction="+faction.id()+", settlement="+settlement.id()+", type="+project.type()));
    }

    private static void take(Settlement settlement,Faction faction,ResourceType type,double amount){
        double local=settlement.stockpile().take(type,amount);if(local<amount)faction.stockpile().take(type,amount-local);
    }
    private static String pretty(GrandProjectType type){return type.name().toLowerCase(java.util.Locale.ROOT).replace('_',' ');}
}
