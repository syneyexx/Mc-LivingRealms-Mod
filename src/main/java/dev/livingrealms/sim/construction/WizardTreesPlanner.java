package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.WizardTreesSeeder;
import java.util.*;

/** Dedicated underground construction graph for the Wizard Trees faction. */
public final class WizardTreesPlanner {
    private WizardTreesPlanner(){}
    public static List<ConstructionIntent> plan(Faction faction,Settlement settlement){
        Objects.requireNonNull(faction);Objects.requireNonNull(settlement);
        if(!WizardTreesSeeder.isWizardTrees(faction))return List.of();
        List<ConstructionIntent> out=new ArrayList<>();SimPosition c=settlement.position();
        add(out,faction,settlement,StructureRole.WIZARD_HALL,0,c,17,17,0,120);
        add(out,faction,settlement,StructureRole.WIZARD_GROVE,0,offset(c,25,0),19,19,0,112);
        int homes=Math.min(18,Math.max(4,(int)Math.ceil(settlement.population()/70.0)));
        for(int i=0;i<homes;i++){double a=i*2.399963229728653;double r=26+Math.sqrt(i+1)*9;add(out,faction,settlement,StructureRole.WIZARD_HOME,i,offset(c,Math.cos(a)*r,Math.sin(a)*r),9,9,i&3,86);}
        for(int i=0;i<6;i++){double a=i*Math.PI/3.0;SimPosition p=offset(c,Math.cos(a)*24,Math.sin(a)*24);add(out,faction,settlement,StructureRole.WIZARD_TUNNEL,i,p,5,30,quarter(a),98);}
        out.sort(Comparator.comparingInt(ConstructionIntent::priority).reversed().thenComparing(ConstructionIntent::key));return List.copyOf(out);
    }
    public static List<ConstructionIntent> pending(Faction faction,Settlement settlement){return plan(faction,settlement).stream().filter(i->!settlement.isConstructionCompleted(i.key())).toList();}
    private static void add(List<ConstructionIntent> out,Faction f,Settlement s,StructureRole role,int index,SimPosition c,int w,int d,int rotation,int priority){out.add(new ConstructionIntent(role.name().toLowerCase(Locale.ROOT)+":"+index,f.id(),s.id(),role,c,w,d,rotation,priority));}
    private static SimPosition offset(SimPosition p,double x,double z){return new SimPosition(p.x()+x,p.z()+z);}
    private static int quarter(double a){return Math.floorMod((int)Math.round(a/(Math.PI/2.0)),4);}
}
