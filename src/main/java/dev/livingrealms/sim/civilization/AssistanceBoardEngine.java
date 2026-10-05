package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Assistance-task pressure board from settlement stress.
 */
public final class AssistanceBoardEngine {
    private AssistanceBoardEngine() {}

    public static void simulateAssistanceTasks(SimulationState state){
        long day=state.clock().day();
        for(Faction faction:state.factions())for(Settlement settlement:faction.settlements()){
            SettlementCivilizationState civ=state.ensureSettlementCivilization(settlement.id(),faction.id());
            EnumMap<AssistanceTaskType,Double> pressure=new EnumMap<>(AssistanceTaskType.class);
            pressure.put(AssistanceTaskType.FOOD_RELIEF,Mathx.clamp((.48-settlement.foodSecurity())/.48,0,1));
            pressure.put(AssistanceTaskType.MEDICAL_AID,Mathx.clamp(Math.max(civ.diseasePressure(),1-civ.sanitation())-.34,0,1));
            pressure.put(AssistanceTaskType.SECURITY_SUPPORT,Mathx.clamp(Math.max(civ.banditPressure(),1-settlement.publicOrder())-.30,0,1));
            pressure.put(AssistanceTaskType.REFUGEE_SUPPORT,Mathx.clamp(civ.refugeePressure()-.28,0,1));
            pressure.put(AssistanceTaskType.WATER_SUPPLY,Mathx.clamp(.50-civ.waterSecurity(),0,1)*2);
            double housingRatio=settlement.housing()<=0?1:Mathx.clamp(settlement.population()/(double)settlement.housing(),0,2);
            pressure.put(AssistanceTaskType.HOUSING_SUPPLIES,Mathx.clamp(housingRatio-.84,0,1));
            double insecureTrade=state.routes().stream().filter(TransportRoute::operational).filter(r->r.fromSettlementId()==settlement.id()||r.toSettlementId()==settlement.id()).mapToDouble(r->Math.max(0,.55-r.security())).max().orElse(0);
            pressure.put(AssistanceTaskType.TRADE_ESCORT,Mathx.clamp(insecureTrade*1.7,0,1));
            pressure.put(AssistanceTaskType.INFRASTRUCTURE_REPAIR,AssistanceContributionEngine.infrastructurePressure(state,settlement));
            double bandit=Mathx.clamp(civ.banditPressure()-.4,0,1);
            pressure.put(AssistanceTaskType.BANDIT_BOUNTY,bandit);
            boolean bridgeNeeded=settlement.geography().riverAdjacent()&&settlement.completedConstruction().stream().noneMatch(k->k.startsWith("bridge:"));
            pressure.put(AssistanceTaskType.BRIDGE_REPAIR,bridgeNeeded?Mathx.clamp(.35+settlement.prosperity()*.2,0,1):0);
            boolean atWar=state.wars().stream().anyMatch(w->w.active()&&w.involves(faction.id()));
            pressure.put(AssistanceTaskType.MILITARY_SUPPLY,atWar?Mathx.clamp(.3+(1-settlement.foodSecurity())*.4,0,1):0);
            pressure.put(AssistanceTaskType.RECONSTRUCTION_AID,Mathx.clamp(settlement.unrest()*.4+(1-settlement.infrastructure())*.5-settlement.prosperity()*.2+dev.livingrealms.sim.construction.BuildingCondition.of(settlement).repairDemand()*.35,0,1));
            long missingCaravans=state.history().recent(12).stream().filter(e->"trade_intercepted".equals(e.type())||"trade_partial_loss".equals(e.type())).count();
            pressure.put(AssistanceTaskType.MISSING_CARAVAN,Mathx.clamp(missingCaravans*.25,0,1));
            for(var entry:pressure.entrySet()){
                AssistanceTaskType type=entry.getKey();double p=entry.getValue();Optional<AssistanceTask> active=state.activeAssistanceTask(settlement.id(),type);
                if(active.isPresent()){active.get().updatePressure(day,p);if(!active.get().active())state.history().add(new WorldEvent(day,"assistance_task_"+active.get().status().name().toLowerCase(Locale.ROOT),"task="+active.get().id()+", settlement="+settlement.id()+", type="+type));continue;}
                if(p<.32||day%7!=Math.floorMod(settlement.id()+type.ordinal()*3,7))continue;
                AssistanceTask task=new AssistanceTask(state.nextId(),faction.id(),settlement.id(),day,day+45,type,"pressure:"+type.name().toLowerCase(Locale.ROOT),p);state.addAssistanceTask(task);
                state.history().add(new WorldEvent(day,"assistance_task_opened","task="+task.id()+", settlement="+settlement.id()+", type="+type+", pressure="+String.format(Locale.ROOT,"%.3f",p)));
            }
        }
    }
}
