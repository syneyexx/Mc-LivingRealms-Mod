package dev.livingrealms.sim.economy.primary;

import dev.livingrealms.sim.construction.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Plans resource sites only when the discovered environment supports them. */
public final class PrimaryEconomyPlanner {
    private PrimaryEconomyPlanner(){}
    public static List<ConstructionIntent> plan(SimulationState state,Faction faction,Settlement settlement){
        List<ConstructionIntent> out=new ArrayList<>();int tier=settlement.tier().ordinal();
        if(tier>=Settlement.Tier.HAMLET.ordinal()&&PrimaryEconomySuitability.score(state,settlement,PrimaryEconomyKind.LUMBER_CAMP)>=.30)add(out,faction,settlement,StructureRole.LUMBER_CAMP,0,-180,120,13,11,58);
        if(tier>=Settlement.Tier.VILLAGE.ordinal()&&PrimaryEconomySuitability.score(state,settlement,PrimaryEconomyKind.MINE)>=.30)add(out,faction,settlement,StructureRole.MINE,0,210,-150,13,11,59);
        if(tier>=Settlement.Tier.VILLAGE.ordinal()&&PrimaryEconomySuitability.score(state,settlement,PrimaryEconomyKind.FISHERY)>=.42)add(out,faction,settlement,StructureRole.FISHERY,0,185,130,15,9,57);
        if(settlement.population()>=1800&&PrimaryEconomySuitability.score(state,settlement,PrimaryEconomyKind.MINE)>=.55)add(out,faction,settlement,StructureRole.MINE,1,330,-250,15,13,48);
        if(settlement.population()>=1600&&PrimaryEconomySuitability.score(state,settlement,PrimaryEconomyKind.LUMBER_CAMP)>=.55)add(out,faction,settlement,StructureRole.LUMBER_CAMP,1,-310,220,15,13,47);
        return List.copyOf(out);
    }
    public static List<ConstructionIntent> pending(SimulationState state,Faction faction,Settlement settlement){return plan(state,faction,settlement).stream().filter(i->!settlement.isConstructionCompleted(i.key())).toList();}
    private static void add(List<ConstructionIntent> out,Faction faction,Settlement settlement,StructureRole role,int index,double dx,double dz,int width,int depth,int priority){
        int bonus=switch(settlement.developmentPriority()){
            case FOOD -> role==StructureRole.FISHERY?35:0;
            case INDUSTRY -> (role==StructureRole.MINE||role==StructureRole.LUMBER_CAMP)?35:0;
            default -> 0;
        };
        out.add(new ConstructionIntent(key(role,index),faction.id(),settlement.id(),role,new SimPosition(settlement.position().x()+dx,settlement.position().z()+dz),width,depth,index,Math.min(200,priority+bonus)));
    }
    private static String key(StructureRole role,int index){return role.name().toLowerCase(Locale.ROOT)+":"+index;}
}
