package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.util.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Hidden wealth caches: bury, intelligence discovery, recover/raid.
 */
public final class HiddenCacheEngine {
    private HiddenCacheEngine() {}

    public static void simulateHiddenCaches(SimulationState state){
        long day=state.clock().day();
        for(Faction faction:state.factions())for(Settlement settlement:faction.settlements()){
            SettlementCivilizationState c=state.ensureSettlementCivilization(settlement.id(),faction.id());
            boolean threat=c.banditPressure()>.65||state.wars().stream().anyMatch(w->w.active()&&(w.attackerFactionId()==faction.id()||w.defenderFactionId()==faction.id())&&(w.targetSettlementId()==0||w.targetSettlementId()==settlement.id()));
            HiddenCache existing=state.hiddenCaches().stream().filter(x->!x.recovered()&&x.settlementId()==settlement.id()).findFirst().orElse(null);
            if(threat&&existing==null&&(faction.stockpile().get(ResourceType.GOLD)>12||faction.stockpile().get(ResourceType.TOOLS)>40)){
                EnumMap<ResourceType,Double> goods=new EnumMap<>(ResourceType.class);
                double gold=faction.stockpile().take(ResourceType.GOLD,Math.min(24,faction.stockpile().get(ResourceType.GOLD)*.08));
                double tools=faction.stockpile().take(ResourceType.TOOLS,Math.min(30,faction.stockpile().get(ResourceType.TOOLS)*.05));
                goods.put(ResourceType.GOLD,gold);goods.put(ResourceType.TOOLS,tools);
                double angle=Math.toRadians(Math.floorMod(Objects.hash(settlement.id(),day),360));
                SimPosition pos=new SimPosition(settlement.position().x()+Math.cos(angle)*95,settlement.position().z()+Math.sin(angle)*95);
                HiddenCache cache=new HiddenCache(state.nextId(),faction.id(),settlement.id(),day,pos,goods);state.addHiddenCache(cache);
                state.history().add(new WorldEvent(day,"wealth_hidden","cache="+cache.id()+", settlement="+settlement.id()));
                existing=cache;
            }
            if(existing!=null&&!existing.recovered()&&!existing.discovered())discoverHiddenCacheByIntelligence(state,existing,faction,day);
            if(!threat&&existing!=null&&!existing.recovered()&&day-existing.createdDay()>45){
                for(var e:existing.goods().entrySet())faction.stockpile().add(e.getKey(),e.getValue());existing.recover();
                state.history().add(new WorldEvent(day,"hidden_cache_recovered","cache="+existing.id()+", settlement="+settlement.id()));
            }else if(existing!=null&&existing.discovered()&&!existing.recovered()&&day-existing.createdDay()>15){
                Faction finder=state.findFaction(existing.discoveredByFactionId()).orElse(null);
                if(finder!=null){DiplomaticRelation rel=finder.relations().get(faction.id());double hostility=rel!=null&&(rel.status()==RelationStatus.HOSTILE||rel.status()==RelationStatus.WAR)?1:.2;double spy=state.ensureFactionCivilization(finder.id()).spyStrength(faction.id());double roll=CivilizationSupport.unit(state.seed()^existing.id()*31L^finder.id()*131L^day*17L,7);if(roll<(.015+.09*spy)*hostility){
                    double gold=existing.goods().getOrDefault(ResourceType.GOLD,0.0),tools=existing.goods().getOrDefault(ResourceType.TOOLS,0.0);finder.stockpile().add(ResourceType.GOLD,gold);finder.stockpile().add(ResourceType.TOOLS,tools);existing.recover();state.history().add(new WorldEvent(day,"hidden_cache_raided","cache="+existing.id()+", owner="+faction.id()+", finder="+finder.id()+", value="+Math.round(existing.value())));
                }}
            }
        }
    }

    public static void discoverHiddenCacheByIntelligence(SimulationState state,HiddenCache cache,Faction owner,long day){
        for(Faction finder:state.factions()){
            if(finder.id()==owner.id())continue;FactionCivilizationState intel=state.ensureFactionCivilization(finder.id());double network=intel.spyStrength(owner.id());if(network<.08)continue;
            double chance=.015+network*.13+intel.intelligence()*.025;double roll=CivilizationSupport.unit(state.seed()^cache.id()*97L^finder.id()*193L^day*29L,11);if(roll>=chance)continue;
            cache.discover(finder.id());state.history().add(new WorldEvent(day,"hidden_cache_compromised","cache="+cache.id()+", owner="+owner.id()+", finder="+finder.id()));break;
        }
    }
}
