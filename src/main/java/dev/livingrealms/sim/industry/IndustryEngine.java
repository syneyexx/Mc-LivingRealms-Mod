package dev.livingrealms.sim.industry;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.*;

/**
 * Authoritative industrial simulation. Create contraptions in loaded chunks are disposable projections
 * of persistent IndustrialSite records; production never depends on chunks being loaded.
 */
public final class IndustryEngine {
    private final Map<IndustryKind,IndustrialProcess> byKind;

    public IndustryEngine(){this(IndustryCatalog.defaults());}
    public IndustryEngine(List<IndustrialProcess> processes){
        Objects.requireNonNull(processes,"processes");
        EnumMap<IndustryKind,IndustrialProcess> map=new EnumMap<>(IndustryKind.class);
        for(IndustrialProcess process:processes){if(map.put(process.kind(),process)!=null)throw new IllegalArgumentException("duplicate industry kind "+process.kind());}
        byKind=Map.copyOf(map);
    }

    /** Stateful production path used by SimulationState. */
    public Map<Long,IndustryReport> simulateDay(SimulationState state,DeterministicRng rng){
        Objects.requireNonNull(state,"state");Objects.requireNonNull(rng,"rng");
        reconcileSites(state);
        Map<Long,IndustryReport> reports=new LinkedHashMap<>();
        List<IndustrialSite> ordered=new ArrayList<>(state.industrialSites());ordered.sort(Comparator.comparingLong(IndustrialSite::id));
        for(IndustrialSite site:ordered){
            Faction faction=state.findFaction(site.factionId()).orElse(null);Settlement settlement=state.findSettlement(site.settlementId()).orElse(null);
            if(faction==null||settlement==null)continue;
            reports.put(site.id(),simulateSite(state,faction,settlement,site,rng));
        }
        return Collections.unmodifiableMap(reports);
    }

    /** Compatibility path for callers that intentionally do not own a SimulationState. */
    public IndustryReport simulateDay(Faction faction){
        Objects.requireNonNull(faction,"faction");double capacity=legacyMechanicalCapacity(faction);if(capacity<=0)return IndustryReport.empty(0);
        double remaining=capacity,used=0;int cycles=0;EnumMap<ResourceType,Double> produced=new EnumMap<>(ResourceType.class);
        for(IndustrialProcess process:byKind.values()){
            if(faction.technology()<process.minimumTechnology()||remaining<process.stressCost())continue;int sites=legacySiteCount(faction,process.kind());
            for(int site=0;site<sites&&remaining>=process.stressCost();site++){
                int run=maxCycles(faction,process,Math.min(4.0,remaining/process.stressCost()));if(run<=0)continue;
                consumeInputs(faction,process,run);produceOutputs(faction,process,run,1.0,produced);double stress=process.stressCost()*run;remaining-=stress;used+=stress;cycles+=run;
            }
        }
        return new IndustryReport(capacity,used,produced,cycles);
    }

    public void reconcileSites(SimulationState state){
        Map<Long,Faction> ownerBySettlement=new HashMap<>();Map<Long,Settlement> settlements=new HashMap<>();
        for(Faction faction:state.factions())for(Settlement settlement:faction.settlements()){ownerBySettlement.put(settlement.id(),faction);settlements.put(settlement.id(),settlement);}

        Set<String> seen=new HashSet<>();List<Long> remove=new ArrayList<>();
        for(IndustrialSite site:state.industrialSites()){
            Faction owner=ownerBySettlement.get(site.settlementId());Settlement settlement=settlements.get(site.settlementId());
            if(owner==null||settlement==null||!eligible(settlement,site.kind())){remove.add(site.id());continue;}
            String key=site.settlementId()+":"+site.kind();if(!seen.add(key)){remove.add(site.id());continue;}
            if(site.factionId()!=owner.id())site.transferTo(owner.id());site.upgradeTo(desiredLevel(settlement,owner));
        }
        remove.forEach(state::removeIndustrialSite);

        for(Faction faction:state.factions())for(Settlement settlement:faction.settlements())for(IndustryKind kind:eligibleKinds(settlement)){
            String key=settlement.id()+":"+kind;if(seen.contains(key))continue;
            IndustrialProcess process=byKind.get(kind);if(process==null||faction.technology()<process.minimumTechnology())continue;
            IndustrialSite site=new IndustrialSite(state.nextId(),faction.id(),settlement.id(),kind,desiredLevel(settlement,faction));state.addIndustrialSite(site);seen.add(key);
            state.history().add(new WorldEvent(state.clock().day(),"industry_site_opened",faction.name()+" opened "+kind+" at "+settlement.name()));
        }
    }

    public double mechanicalCapacity(SimulationState state,Faction faction){
        double total=0;for(IndustrialSite site:state.industrialSites())if(site.factionId()==faction.id()){Settlement settlement=state.findSettlement(site.settlementId()).orElse(null);IndustrialProcess process=byKind.get(site.kind());if(settlement!=null&&process!=null)total+=siteCapacity(faction,settlement,site,process);}return total;
    }

    private IndustryReport simulateSite(SimulationState state,Faction faction,Settlement settlement,IndustrialSite site,DeterministicRng rng){
        IndustrialProcess process=byKind.get(site.kind());if(process==null||faction.technology()<process.minimumTechnology()){site.setDayResult(IndustrialSiteStatus.OFFLINE,0,0);return IndustryReport.empty(0);}
        site.tickDowntime();performMaintenanceAndRepair(faction,site);
        if(site.downtimeDays()>0||site.condition()<=.08){site.setDayResult(IndustrialSiteStatus.OFFLINE,0,0);return IndustryReport.empty(0);}

        double capacity=siteCapacity(faction,settlement,site,process);double maxByStress=capacity/process.stressCost();int max=(int)Math.floor(Math.min(4.0*site.level(),maxByStress)+1e-9);int run=maxCycles(faction,process,max);
        if(run<=0){site.setDayResult(IndustrialSiteStatus.STARVED,0,0);site.damage(.0015,0);return IndustryReport.empty(capacity);}

        consumeInputs(faction,process,run);double utilization=Mathx.clamp((process.stressCost()*run)/Math.max(.0001,capacity),0,1);double yield=.72+.23*site.condition()+.05*settlement.prosperity();EnumMap<ResourceType,Double> produced=new EnumMap<>(ResourceType.class);produceOutputs(faction,process,run,yield,produced);
        IndustrialSiteStatus status=site.condition()<.45?IndustrialSiteStatus.DAMAGED:IndustrialSiteStatus.ACTIVE;site.setDayResult(status,run,utilization);

        double failureChance=.0005+Math.pow(1-site.condition(),2)*.035+utilization*.0015;
        if(rng.chance(failureChance)){
            double damage=rng.between(.035,.14);int downtime=1+rng.nextInt(Math.max(1,site.level()));site.damage(damage,downtime);
            state.history().add(new WorldEvent(state.clock().day(),"industry_breakdown","site="+site.id()+", kind="+site.kind()+", damage="+String.format(Locale.ROOT,"%.3f",damage)+", downtime="+downtime));
        }
        return new IndustryReport(capacity,process.stressCost()*run,produced,run);
    }

    private static void performMaintenanceAndRepair(Faction faction,IndustrialSite site){
        double toolsNeed=.025*site.level();double machineryNeed=advanced(site.kind())?.008*site.level():0;
        double tools=faction.stockpile().take(ResourceType.TOOLS,toolsNeed);double machinery=machineryNeed<=0?machineryNeed:faction.stockpile().take(ResourceType.MACHINERY,machineryNeed);
        double maintenanceRatio=Math.min(toolsNeed<=0?1:tools/toolsNeed,machineryNeed<=0?1:machinery/machineryNeed);
        if(maintenanceRatio<.75)site.damage(.0045*(1-maintenanceRatio)*(1+.15*site.level()),0);
        else if(site.condition()<1)site.repair(.0015+.0004*site.level());

        if(site.condition()<.62){
            double repairTools=.16*site.level(),repairIron=.22*site.level(),repairMachinery=advanced(site.kind())?.05*site.level():0;
            if(has(faction,ResourceType.TOOLS,repairTools)&&has(faction,ResourceType.IRON,repairIron)&&has(faction,ResourceType.MACHINERY,repairMachinery)){
                faction.stockpile().take(ResourceType.TOOLS,repairTools);faction.stockpile().take(ResourceType.IRON,repairIron);if(repairMachinery>0)faction.stockpile().take(ResourceType.MACHINERY,repairMachinery);site.repair(.045+.012*site.level());
            }
        }
    }

    private static boolean has(Faction faction,ResourceType resource,double amount){return amount<=0||faction.stockpile().get(resource)+1e-9>=amount;}
    private static int maxCycles(Faction faction,IndustrialProcess process,double requested){double max=Math.max(0,requested);for(var input:process.inputs().entrySet())if(input.getValue()>0)max=Math.min(max,faction.stockpile().get(input.getKey())/input.getValue());return (int)Math.floor(max+1e-9);}
    private static void consumeInputs(Faction faction,IndustrialProcess process,int run){for(var input:process.inputs().entrySet())faction.stockpile().take(input.getKey(),input.getValue()*run);}
    private static void produceOutputs(Faction faction,IndustrialProcess process,int run,double yield,EnumMap<ResourceType,Double> produced){for(var output:process.outputs().entrySet()){double amount=output.getValue()*run*yield;faction.stockpile().add(output.getKey(),amount);produced.merge(output.getKey(),amount,Double::sum);}}

    private static double siteCapacity(Faction faction,Settlement settlement,IndustrialSite site,IndustrialProcess process){double infra=.55+Math.min(1.5,settlement.infrastructure())*.30;double technology=.65+Math.min(2.5,faction.technology())*.32;double condition=.20+.80*site.condition();return process.stressCost()*4.0*site.level()*infra*technology*condition;}
    private static boolean advanced(IndustryKind kind){return kind==IndustryKind.FUEL_REFINERY||kind==IndustryKind.MACHINERY_WORKS||kind==IndustryKind.MUNITIONS;}
    private static boolean eligible(Settlement settlement,IndustryKind kind){return eligibleKinds(settlement).contains(kind);}
    private static EnumSet<IndustryKind> eligibleKinds(Settlement settlement){
        EnumSet<IndustryKind> result=EnumSet.noneOf(IndustryKind.class);int tier=settlement.tier().ordinal();
        if(tier>=Settlement.Tier.VILLAGE.ordinal())Collections.addAll(result,IndustryKind.SAWMILL,IndustryKind.STONEWORKS);
        if(tier>=Settlement.Tier.TOWN.ordinal())Collections.addAll(result,IndustryKind.COKEWORKS,IndustryKind.METALWORKS,IndustryKind.TOOLWORKS,IndustryKind.TEXTILE_MILL);
        if(tier>=Settlement.Tier.CITY.ordinal())Collections.addAll(result,IndustryKind.FUEL_REFINERY,IndustryKind.MACHINERY_WORKS,IndustryKind.MUNITIONS);
        return result;
    }
    private static int desiredLevel(Settlement settlement,Faction faction){int tier=Math.max(1,settlement.tier().ordinal()-Settlement.Tier.HAMLET.ordinal());int tech=1+(int)Math.floor(Math.max(0,faction.technology())*.75);return Math.max(1,Math.min(5,Math.min(tier,tech)));}

    private static double legacyMechanicalCapacity(Faction faction){double capacity=0;for(Settlement s:faction.settlements())capacity+=switch(s.tier()){case CAMP->0;case HAMLET->8;case VILLAGE->28;case TOWN->120;case CITY->400;case METROPOLIS->1000;};return capacity*(.65+Math.min(2.0,faction.technology()));}
    private static int legacySiteCount(Faction faction,IndustryKind kind){int sites=0;for(Settlement s:faction.settlements()){int tier=s.tier().ordinal();sites+=switch(kind){case SAWMILL,STONEWORKS->tier>=Settlement.Tier.VILLAGE.ordinal()?1:0;case COKEWORKS,METALWORKS,TOOLWORKS,TEXTILE_MILL->tier>=Settlement.Tier.TOWN.ordinal()?1:0;case FUEL_REFINERY,MACHINERY_WORKS,MUNITIONS->tier>=Settlement.Tier.CITY.ordinal()?1:0;};}return sites;}
}
