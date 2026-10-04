package dev.livingrealms.sim.military;

import dev.livingrealms.sim.diplomacy.WarGoalType;
import dev.livingrealms.sim.diplomacy.WarState;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Campaign-plan selection then objective derivation. Tactical combat stays in FactionEngine. */
public final class MilitaryCommandEngine {
    public void simulateDay(SimulationState state){
        cleanup(state);
        refreshCampaignPlans(state);
        for(Faction faction:state.factions())for(Army army:faction.armies())if(state.objectives().stream().noneMatch(o->!o.complete()&&o.armyId()==army.id())){
            CampaignPlan plan=bestPlan(faction,state);Target target=plan==null?fallbackTarget(faction,army,state):deriveTarget(faction,army,plan,state);
            if(target==null)continue;
            state.addObjective(new MilitaryObjective(state.nextId(),army.id(),faction.id(),target.type,target.enemyId,target.settlementId,target.position,state.clock().day(),target.priority));
        }
    }

    private static void refreshCampaignPlans(SimulationState state){
        long day=state.clock().day();
        for(CampaignPlan plan:state.campaignPlans()){
            if(!plan.active())continue;
            WarState war=state.wars().stream().filter(w->w.id()==plan.warId()).findFirst().orElse(null);
            if(war==null||!war.active()||!war.involves(plan.factionId())){plan.markComplete();continue;}
            if(plan.targetSettlementId()>0&&state.findSettlement(plan.targetSettlementId()).isEmpty())plan.markComplete();
        }
        for(WarState war:state.wars()){
            if(!war.active())continue;
            ensurePlan(state,war,war.attackerFactionId(),day);
            ensurePlan(state,war,war.defenderFactionId(),day);
        }
    }

    private static void ensurePlan(SimulationState state,WarState war,long factionId,long day){
        if(state.campaignPlans().stream().anyMatch(p->p.active()&&p.warId()==war.id()&&p.factionId()==factionId))return;
        Faction self=state.findFaction(factionId).orElse(null);if(self==null||self.armies().isEmpty())return;
        long enemyId=war.attackerFactionId()==factionId?war.defenderFactionId():war.attackerFactionId();
        Faction enemy=state.findFaction(enemyId).orElse(null);if(enemy==null)return;
        CampaignPlanType type;long targetSettlement;int priority;
        double ownStrength=self.armies().stream().mapToInt(Army::totalPersonnel).sum()+1;
        double enemyStrength=enemy.armies().stream().mapToInt(Army::totalPersonnel).sum()+1;
        double ratio=ownStrength/enemyStrength;
        double avgSupply=self.armies().stream().mapToDouble(Army::supply).average().orElse(.5);
        Optional<SiegeState> ownSiege=state.sieges().stream().filter(SiegeState::active).filter(s->s.defenderFactionId()==factionId).findFirst();
        Optional<SiegeState> enemySiege=state.sieges().stream().filter(SiegeState::active).filter(s->s.attackerFactionId()==factionId).findFirst();
        Settlement enemyCapital=enemy.settlements().stream().max(Comparator.comparingInt(Settlement::population)).orElse(null);
        Settlement border=enemy.settlements().stream().min(Comparator.comparingDouble(s->nearestArmyDistance(self,s.position()))).orElse(null);
        if(avgSupply<.28||ratio<.45){type=CampaignPlanType.RETREAT;targetSettlement=homeId(self);priority=40;}
        else if(ownSiege.isPresent()){type=CampaignPlanType.RELIEVE_SIEGE;targetSettlement=ownSiege.get().settlementId();priority=140;}
        else if(ratio<.7){type=CampaignPlanType.DEFEND_BORDER;targetSettlement=homeId(self);priority=90;}
        else if(war.goal()==WarGoalType.DEFENSE){type=CampaignPlanType.DEFEND_BORDER;targetSettlement=homeId(self);priority=100;}
        else if(enemySiege.isPresent()||(enemyCapital!=null&&war.targetSettlementId()==enemyCapital.id()&&ratio>1.15)){
            type=CampaignPlanType.CAPTURE_CAPITAL;targetSettlement=war.targetSettlementId()>0?war.targetSettlementId():(enemyCapital==null?0:enemyCapital.id());priority=130;
        }else if(war.goal()==WarGoalType.REPARATIONS||war.goal()==WarGoalType.HUMILIATION){
            type=CampaignPlanType.RAID_ECONOMY;targetSettlement=border==null?war.targetSettlementId():border.id();priority=95;
        }else if(avgSupply<.45){type=CampaignPlanType.CUT_SUPPLY;targetSettlement=border==null?0:border.id();priority=85;}
        else if(ratio>1.4&&enemy.settlements().size()>1){type=CampaignPlanType.CAPTURE_TOWN;targetSettlement=border==null?war.targetSettlementId():border.id();priority=110;}
        else if(ratio>1.1){type=CampaignPlanType.BLOCKADE;targetSettlement=war.targetSettlementId()>0?war.targetSettlementId():(border==null?0:border.id());priority=100;}
        else if(day-war.startDay()<8){type=CampaignPlanType.REORGANIZE;targetSettlement=homeId(self);priority=50;}
        else{type=CampaignPlanType.CONSOLIDATE;targetSettlement=homeId(self);priority=60;}
        if(state.campaignPlans().size()>=SimulationState.MAX_CAMPAIGN_PLANS)return;
        state.addCampaignPlan(new CampaignPlan(state.nextId(),factionId,war.id(),type,targetSettlement,priority,day));
    }

    private static CampaignPlan bestPlan(Faction faction,SimulationState state){
        return state.campaignPlans().stream().filter(CampaignPlan::active).filter(p->p.factionId()==faction.id())
                .max(Comparator.comparingInt(CampaignPlan::priority).thenComparingLong(CampaignPlan::createdDay)).orElse(null);
    }

    private static Target deriveTarget(Faction owner,Army army,CampaignPlan plan,SimulationState state){
        WarState war=state.wars().stream().filter(w->w.id()==plan.warId()).findFirst().orElse(null);
        long enemyId=war==null?0:war.attackerFactionId()==owner.id()?war.defenderFactionId():war.attackerFactionId();
        Settlement target=plan.targetSettlementId()>0?state.findSettlement(plan.targetSettlementId()).orElse(null):null;
        return switch(plan.type()){
            case DEFEND_BORDER,CONSOLIDATE,REORGANIZE,RETREAT -> {
                Settlement home=target!=null?target:owner.settlements().stream().min(Comparator.comparingDouble(s->s.position().distanceTo(army.position()))).orElse(null);
                yield home==null?null:new Target(plan.type()==CampaignPlanType.RETREAT?MilitaryObjectiveType.RETREAT:MilitaryObjectiveType.DEFEND,0,home.id(),home.position(),plan.priority());
            }
            case RELIEVE_SIEGE -> target==null?null:new Target(MilitaryObjectiveType.DEFEND,enemyId,target.id(),target.position(),plan.priority());
            case RAID_ECONOMY,CUT_SUPPLY -> target==null?null:new Target(MilitaryObjectiveType.RAID,enemyId,target.id(),target.position(),plan.priority());
            case CAPTURE_TOWN,CAPTURE_CAPITAL -> target==null?null:new Target(MilitaryObjectiveType.CAPTURE_SETTLEMENT,enemyId,target.id(),target.position(),plan.priority());
            case BLOCKADE -> target==null?null:new Target(MilitaryObjectiveType.SIEGE,enemyId,target.id(),target.position(),plan.priority());
        };
    }

    private static Target fallbackTarget(Faction owner,Army army,SimulationState state){
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
            if(o.type()==MilitaryObjectiveType.DEFEND||o.type()==MilitaryObjectiveType.RETREAT){
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

    private static long homeId(Faction f){return f.settlements().stream().max(Comparator.comparingInt(Settlement::population)).map(Settlement::id).orElse(0L);}
    private static double nearestArmyDistance(Faction f,SimPosition p){return f.armies().stream().mapToDouble(a->a.position().distanceTo(p)).min().orElse(1.0e9);}
    private record Target(MilitaryObjectiveType type,long enemyId,long settlementId,SimPosition position,int priority){}
}
