package dev.livingrealms.sim.military;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Strategic objective assignment. Tactical combat stays in FactionEngine. */
public final class MilitaryCommandEngine {
    public void simulateDay(SimulationState state){
        cleanup(state);
        for(Faction faction:state.factions())for(Army army:faction.armies())if(state.objectives().stream().noneMatch(o->!o.complete()&&o.armyId()==army.id())){
            Target target=bestTarget(faction,army,state);if(target==null)continue;
            state.addObjective(new MilitaryObjective(state.nextId(),army.id(),faction.id(),target.type,target.enemyId,target.settlementId,target.position,state.clock().day(),target.priority));
        }
    }
    private static Target bestTarget(Faction owner,Army army,SimulationState state){
        Target best=null;double bestScore=Double.NEGATIVE_INFINITY;
        for(var rel:owner.relations().entrySet())if(rel.getValue().status()==RelationStatus.WAR){Faction enemy=state.findFaction(rel.getKey()).orElse(null);if(enemy==null)continue;for(Settlement settlement:enemy.settlements()){
            double d=army.position().distanceTo(settlement.position());double strategic=settlement.population()*.03+settlement.infrastructure()*20-d*.012;int priority=(int)Math.round(100+strategic);
            if(strategic>bestScore){bestScore=strategic;best=new Target(MilitaryObjectiveType.CAPTURE_SETTLEMENT,enemy.id(),settlement.id(),settlement.position(),priority);}
        }}
        if(best!=null)return best;
        Settlement home=owner.settlements().stream().min(Comparator.comparingDouble(s->s.position().distanceTo(army.position()))).orElse(null);
        return home==null?null:new Target(MilitaryObjectiveType.DEFEND,0,home.id(),home.position(),30);
    }
    private static void cleanup(SimulationState state){
        for(MilitaryObjective o:state.objectives()){
            if(o.complete())continue;
            Army army=state.findArmy(o.armyId()).orElse(null);Faction owner=state.findFaction(o.ownerFactionId()).orElse(null);
            if(army==null||owner==null){o.markComplete();continue;}
            if(o.type()==MilitaryObjectiveType.DEFEND){
                if(o.targetSettlementId()>0&&owner.settlements().stream().noneMatch(s->s.id()==o.targetSettlementId()))o.markComplete();
                continue;
            }
            if(o.type()==MilitaryObjectiveType.CAPTURE_SETTLEMENT||o.type()==MilitaryObjectiveType.SIEGE||o.type()==MilitaryObjectiveType.RAID){
                Faction target=state.findFaction(o.targetFactionId()).orElse(null);
                DiplomaticRelation relation=target==null?null:owner.relations().get(target.id());
                if(target==null||relation==null||relation.status()!=RelationStatus.WAR){o.markComplete();continue;}
                if(o.targetSettlementId()>0&&target.settlements().stream().noneMatch(s->s.id()==o.targetSettlementId()))o.markComplete();
            }
        }
    }
    private record Target(MilitaryObjectiveType type,long enemyId,long settlementId,SimPosition position,int priority){}
}
