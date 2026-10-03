package dev.livingrealms.sim.industry;

import dev.livingrealms.sim.construction.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;

/** Deterministically maps strategic industry kinds onto completed workshop/factory structures. */
public final class IndustrySitePlanner {
    public record Site(long factionId,long settlementId,IndustryKind kind,SimPosition center,StructureRole hostRole,int slot){public Site{Objects.requireNonNull(kind);Objects.requireNonNull(center);Objects.requireNonNull(hostRole);if(factionId<=0||settlementId<=0||slot<0)throw new IllegalArgumentException("site");}}
    private IndustrySitePlanner(){}

    public static List<Site> plan(Faction faction){
        Objects.requireNonNull(faction,"faction");List<Site> out=new ArrayList<>();
        for(Settlement settlement:faction.settlements()){
            Map<StructureRole,ConstructionIntent> hosts=new EnumMap<>(StructureRole.class);for(ConstructionIntent i:SettlementPlanner.plan(faction,settlement))if(i.role()==StructureRole.WORKSHOP||i.role()==StructureRole.FACTORY)hosts.putIfAbsent(i.role(),i);
            int slot=0;for(IndustryKind kind:IndustryKind.values()){
                StructureRole role=host(kind);ConstructionIntent host=hosts.get(role);if(host==null||!settlement.isConstructionCompleted(host.key())||!unlocked(faction,settlement,kind))continue;
                int lane=slot%4,row=slot/4;double x=host.center().x()+host.width()/2.0+4+row*4;double z=host.center().z()-6+lane*4;SimPosition center=new SimPosition(x,z);out.add(new Site(faction.id(),settlement.id(),kind,center,role,slot++));
            }
        }
        return List.copyOf(out);
    }
    private static StructureRole host(IndustryKind kind){return switch(kind){case SAWMILL,STONEWORKS->StructureRole.WORKSHOP;default->StructureRole.FACTORY;};}
    private static boolean unlocked(Faction faction,Settlement settlement,IndustryKind kind){
        int tier=settlement.tier().ordinal();double tech=faction.technology();return switch(kind){
            case SAWMILL->tier>=Settlement.Tier.VILLAGE.ordinal()&&tech>=.05;
            case STONEWORKS->tier>=Settlement.Tier.VILLAGE.ordinal()&&tech>=.08;
            case COKEWORKS->tier>=Settlement.Tier.TOWN.ordinal()&&tech>=.18;
            case METALWORKS->tier>=Settlement.Tier.TOWN.ordinal()&&tech>=.20;
            case TOOLWORKS->tier>=Settlement.Tier.TOWN.ordinal()&&tech>=.22;
            case TEXTILE_MILL->tier>=Settlement.Tier.TOWN.ordinal()&&tech>=.25;
            case FUEL_REFINERY->tier>=Settlement.Tier.CITY.ordinal()&&tech>=.35;
            case MACHINERY_WORKS->tier>=Settlement.Tier.CITY.ordinal()&&tech>=.40;
            case MUNITIONS->tier>=Settlement.Tier.CITY.ordinal()&&tech>=.45;
        };}
}
