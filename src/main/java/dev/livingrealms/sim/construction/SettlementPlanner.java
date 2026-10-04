package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic settlement planner built around walkable street blocks instead of decorative
 * free-form splines. Every archetype uses straight connected streets; variation comes from ward
 * spacing, block size, civic placement and density rather than roads that cut diagonally through
 * houses, trees and mountains. Minecraft terrain adaptation remains the responsibility of the
 * construction adapter.
 */
public final class SettlementPlanner {
    private SettlementPlanner() {}

    public static List<ConstructionIntent> plan(Faction faction, Settlement settlement) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        Layout layout=layout(settlement);
        List<ConstructionIntent> out=new ArrayList<>();
        int tier=settlement.tier().ordinal();
        int baseRotation=Math.floorMod((int)mix(settlement.id()^0x4F1BBCDCBFA54001L),2);

        boolean capital=faction.settlements().stream().max(Comparator.comparingInt(Settlement::population).thenComparingLong(Settlement::id)).map(s->s.id()==settlement.id()).orElse(false);
        int keepW=capital&&tier>=Settlement.Tier.CITY.ordinal()?31:capital?23:tier>=Settlement.Tier.TOWN.ordinal()?19:15;
        int keepD=capital&&tier>=Settlement.Tier.CITY.ordinal()?27:capital?21:tier>=Settlement.Tier.TOWN.ordinal()?17:15;
        SimPosition keep=civicPoint(settlement,layout,StructureRole.KEEP);
        addAt(out,faction,settlement,StructureRole.KEEP,0,keep,keepW,keepD,baseRotation,capital?190:120);

        addRoadNetwork(out,faction,settlement,layout,baseRotation);
        addHousing(out,faction,settlement,layout,baseRotation);
        addFarms(out,faction,settlement,layout,baseRotation);
        addPastures(out,faction,settlement,layout,baseRotation);

        if(tier>=Settlement.Tier.HAMLET.ordinal())addCivic(out,faction,settlement,layout,baseRotation,StructureRole.WELL,0,5,5,116);
        if(tier>=Settlement.Tier.VILLAGE.ordinal()){
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.IRRIGATION,0,7,31,74);
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.MILL,0,11,11,93);
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.MARKET,0,15,13,122);
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.WAREHOUSE,0,13,11,91);
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.TAVERN,0,13,11,94);
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.TEMPLE,0,13,15,88);
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.BAKERY,0,11,9,90);
        }
        if(tier>=Settlement.Tier.TOWN.ordinal()){
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.BARRACKS,0,15,11,104);
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.WORKSHOP,0,13,11,92);
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.BREWERY,0,13,11,88);
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.CLINIC,0,13,11,90);
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.SCHOOL,0,15,11,86);
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.COURTHOUSE,0,15,13,99);
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.PRISON,0,13,13,96);
        }
        if(tier>=Settlement.Tier.CITY.ordinal()){
            if(faction.technology()>=.55)addCivic(out,faction,settlement,layout,baseRotation,StructureRole.AQUEDUCT,0,7,41,79);
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.ORPHANAGE,0,15,13,79);
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.MONUMENT,0,9,9,76);
            if(faction.technology()>=.55)addCivic(out,faction,settlement,layout,baseRotation,StructureRole.OBSERVATORY,0,15,15,72);
            addCityWalls(out,faction,settlement,baseRotation);
        }
        if(tier>=Settlement.Tier.TOWN.ordinal()&&faction.technology()>=.35)addCivic(out,faction,settlement,layout,baseRotation,StructureRole.FACTORY,0,19,15,78);
        // Coastal / harbour-suitable settlements get a physical dock so naval ports have a visible berth.
        if(tier>=Settlement.Tier.VILLAGE.ordinal()&&settlement.geography().shipSuitable()){
            addCivic(out,faction,settlement,layout,baseRotation,StructureRole.DOCK,0,17,11,118);
        }
        if(tier>=Settlement.Tier.CITY.ordinal()&&faction.technology()>=.75){
            SimPosition edge=local(settlement,baseRotation,132,-96);
            addAt(out,faction,settlement,StructureRole.AIRFIELD,0,edge,25,70,baseRotation,64);
        }

        out.replaceAll(intent->new ConstructionIntent(intent.key(),intent.factionId(),intent.settlementId(),intent.role(),intent.center(),intent.width(),intent.depth(),intent.rotationQuarterTurns(),adjustPriority(settlement.developmentPriority(),intent.role(),intent.priority())));
        out.sort(Comparator.comparingInt(ConstructionIntent::priority).reversed().thenComparing(ConstructionIntent::key));
        return List.copyOf(out);
    }

    public static List<ConstructionIntent> pending(Faction faction,Settlement settlement){return plan(faction,settlement).stream().filter(i->!settlement.isConstructionCompleted(i.key())).toList();}
    public static String layoutArchetype(Settlement settlement){Objects.requireNonNull(settlement,"settlement");return layout(settlement).name().toLowerCase(Locale.ROOT);}

    private static void addRoadNetwork(List<ConstructionIntent> out,Faction faction,Settlement settlement,Layout layout,int baseRotation){
        int tier=settlement.tier().ordinal();
        int spacing=switch(layout){case GRID->44;case MARKET_CROSS->48;case WARDS->52;case BOULEVARD->56;case OLD_TOWN->42;};
        int rings=switch(settlement.tier()){case CAMP->0;case HAMLET->1;case VILLAGE->1;case TOWN->2;case CITY->3;case METROPOLIS->4;};
        int halfLength=Math.max(42,spacing*(rings+1));
        int index=0;
        // Central cross is always present and straight.
        addRoad(out,faction,settlement,index++,local(settlement,baseRotation,0,0),9,halfLength*2+9,baseRotation,132);
        addRoad(out,faction,settlement,index++,local(settlement,baseRotation,0,0),9,halfLength*2+9,baseRotation+1,132);
        for(int ring=1;ring<=rings;ring++){
            int offset=spacing*ring;
            addRoad(out,faction,settlement,index++,local(settlement,baseRotation, offset,0),9,halfLength*2+9,baseRotation,112);
            addRoad(out,faction,settlement,index++,local(settlement,baseRotation,-offset,0),9,halfLength*2+9,baseRotation,112);
            addRoad(out,faction,settlement,index++,local(settlement,baseRotation,0, offset),9,halfLength*2+9,baseRotation+1,112);
            addRoad(out,faction,settlement,index++,local(settlement,baseRotation,0,-offset),9,halfLength*2+9,baseRotation+1,112);
        }
        // Residential side streets halve the distance from every urban lot to a real sidewalk.
        // This prevents houses from appearing as isolated boxes in grass between distant arterials.
        if(tier>=Settlement.Tier.VILLAGE.ordinal()){
            for(int ring=0;ring<rings;ring++){
                int offset=spacing*ring+spacing/2;
                addRoad(out,faction,settlement,index++,local(settlement,baseRotation, offset,0),5,halfLength*2+5,baseRotation,98);
                addRoad(out,faction,settlement,index++,local(settlement,baseRotation,-offset,0),5,halfLength*2+5,baseRotation,98);
                addRoad(out,faction,settlement,index++,local(settlement,baseRotation,0, offset),5,halfLength*2+5,baseRotation+1,98);
                addRoad(out,faction,settlement,index++,local(settlement,baseRotation,0,-offset),5,halfLength*2+5,baseRotation+1,98);
            }
            int offset=Math.max(20,spacing/2);
            addRoad(out,faction,settlement,index++,local(settlement,baseRotation,offset,offset),7,spacing+9,baseRotation,101);
        }
    }

    private static void addHousing(List<ConstructionIntent> out,Faction faction,Settlement settlement,Layout layout,int baseRotation){
        int represented=Math.max(settlement.population(),settlement.housing());
        // No hard 132-house ceiling: cities grow wards as population/housing rise. Soft performance bound only.
        int softCap=switch(settlement.tier()){case CAMP->24;case HAMLET->48;case VILLAGE->96;case TOWN->220;case CITY->480;case METROPOLIS->900;};
        int houses=Math.min(softCap,Math.max(5,(int)Math.ceil(represented/22.0)));
        int spacing=switch(layout){case GRID->44;case MARKET_CROSS->48;case WARDS->52;case BOULEVARD->56;case OLD_TOWN->42;};
        int lotStep=14;
        int emitted=0,scan=0,limit=houses*22+400;
        while(emitted<houses&&scan<limit){
            int[] cell=spiral(scan++);
            int lx=cell[0]*lotStep,lz=cell[1]*lotStep;
            if(Math.abs(lx)<12&&Math.abs(lz)<12)continue;
            int streetStep=settlement.tier().ordinal()>=Settlement.Tier.VILLAGE.ordinal()?Math.max(18,spacing/2):spacing;
            if(distanceToStreet(lx,streetStep)<5||distanceToStreet(lz,streetStep)<5)continue;
            int maxRadius=switch(settlement.tier()){case CAMP->40;case HAMLET->64;case VILLAGE->96;case TOWN->148;case CITY->220;case METROPOLIS->320;};
            // Population pressure expands the built envelope beyond tier defaults.
            maxRadius+=Math.min(120,(int)Math.sqrt(Math.max(0,represented))/2);
            if(Math.abs(lx)>maxRadius||Math.abs(lz)>maxRadius)continue;
            SimPosition center=local(settlement,baseRotation,lx,lz);
            int variant=Math.floorMod((int)mix(settlement.id()^(long)emitted*0x9E3779B97F4A7C15L),7);
            int w,d;
            // Under population pressure, emit denser apartments/townhouses more often so physical
            // cities visibly scale without a 1:1 cottage per household.
            boolean pressure=settlement.housingShortage()>40||settlement.population()>settlement.housing();
            if(settlement.tier().ordinal()>=Settlement.Tier.CITY.ordinal()&&(emitted%5==0||(pressure&&emitted%3==0))){w=13;d=11;}
            else if(settlement.tier().ordinal()>=Settlement.Tier.TOWN.ordinal()&&(emitted%6==0||(pressure&&emitted%4==0))){w=11;d=9;}
            else if(settlement.tier()==Settlement.Tier.METROPOLIS&&emitted%2==0){w=13;d=11;}
            else {w=switch(variant){case 0->7;case 1,4->9;default->7;};d=switch(variant){case 2->9;case 5->7;default->9;};}
            int face=houseFacing(lx,lz,spacing,baseRotation);
            addAt(out,faction,settlement,StructureRole.HOUSE,emitted,center,w,d,face,88);
            emitted++;
        }
    }

    private static void addFarms(List<ConstructionIntent> out,Faction faction,Settlement settlement,Layout layout,int baseRotation){
        int farms=Math.min(72,Math.max(2,(int)Math.ceil(settlement.population()/160.0)));
        int urbanRadius=switch(settlement.tier()){case CAMP->48;case HAMLET->64;case VILLAGE->88;case TOWN->132;case CITY->188;case METROPOLIS->244;};
        for(int i=0;i<farms;i++){
            int side=i&3,band=i/4;double along=(band-(farms/8.0))*32.0;double edge=urbanRadius+34+(band%2)*18;
            double lx=switch(side){case 0->edge;case 1->-edge;default->along;};
            double lz=switch(side){case 2->edge;case 3->-edge;default->along;};
            int size=11+2*Math.floorMod(i+(int)settlement.id(),3);
            addAt(out,faction,settlement,StructureRole.FARM,i,local(settlement,baseRotation,lx,lz),size,size,baseRotation,62);
        }
    }

    private static void addPastures(List<ConstructionIntent> out,Faction faction,Settlement settlement,Layout layout,int baseRotation){
        int pastures=Math.min(24,Math.max(1,(int)Math.ceil(settlement.population()/280.0)));
        int urbanRadius=switch(settlement.tier()){case CAMP->48;case HAMLET->64;case VILLAGE->88;case TOWN->132;case CITY->188;case METROPOLIS->244;};
        for(int i=0;i<pastures;i++){
            int side=(i+1)&3;double along=(i-(pastures/4.0))*28.0;double edge=urbanRadius+58+(i%2)*14;
            double lx=switch(side){case 0->edge;case 1->-edge;default->along;};
            double lz=switch(side){case 2->edge;case 3->-edge;default->along;};
            addAt(out,faction,settlement,StructureRole.PASTURE,i,local(settlement,baseRotation,lx,lz),13,13,baseRotation,58);
        }
    }

    private static void addCityWalls(List<ConstructionIntent> out,Faction faction,Settlement settlement,int baseRotation){
        int radius=settlement.tier()==Settlement.Tier.METROPOLIS?238:182;
        int segment=34,index=0;
        for(int x=-radius;x<=radius;x+=segment){
            addAt(out,faction,settlement,StructureRole.WALL,index++,local(settlement,baseRotation,x,-radius),5,segment+4,baseRotation+1,108);
            addAt(out,faction,settlement,StructureRole.WALL,index++,local(settlement,baseRotation,x, radius),5,segment+4,baseRotation+1,108);
        }
        for(int z=-radius+segment;z<=radius-segment;z+=segment){
            addAt(out,faction,settlement,StructureRole.WALL,index++,local(settlement,baseRotation,-radius,z),5,segment+4,baseRotation,108);
            addAt(out,faction,settlement,StructureRole.WALL,index++,local(settlement,baseRotation, radius,z),5,segment+4,baseRotation,108);
        }
        addAt(out,faction,settlement,StructureRole.GATE,0,local(settlement,baseRotation,0,-radius),11,7,baseRotation,150);
        addAt(out,faction,settlement,StructureRole.GATE,1,local(settlement,baseRotation,0, radius),11,7,baseRotation+2,150);
        addAt(out,faction,settlement,StructureRole.GATE,2,local(settlement,baseRotation,-radius,0),11,7,baseRotation+1,150);
        addAt(out,faction,settlement,StructureRole.GATE,3,local(settlement,baseRotation, radius,0),11,7,baseRotation+3,150);
    }

    private static void addCivic(List<ConstructionIntent> out,Faction faction,Settlement settlement,Layout layout,int baseRotation,StructureRole role,int index,int w,int d,int priority){
        SimPosition center=civicPoint(settlement,layout,role);addAt(out,faction,settlement,role,index,center,w,d,baseRotation+orientationFor(role),priority);
    }

    private static SimPosition civicPoint(Settlement settlement,Layout layout,StructureRole role){
        int r=Math.floorMod((int)mix(settlement.id()^0x4F1BBCDCBFA54001L),2);
        double[] p=switch(role){
            case KEEP->new double[]{-18,-18};case MARKET->new double[]{18,18};case WAREHOUSE->new double[]{-30,28};case BARRACKS->new double[]{30,-30};case WORKSHOP->new double[]{54,18};case FACTORY->new double[]{86,54};case WELL->new double[]{0,18};case IRRIGATION->new double[]{92,44};case AQUEDUCT->new double[]{-126,32};case TAVERN->new double[]{30,28};case TEMPLE->new double[]{-32,-30};case CLINIC->new double[]{54,-18};case SCHOOL->new double[]{-54,18};case COURTHOUSE->new double[]{-18,54};case PRISON->new double[]{54,54};case ORPHANAGE->new double[]{-54,54};case MONUMENT->new double[]{18,0};case OBSERVATORY->new double[]{-96,-78};case MILL->new double[]{72,-28};case BAKERY->new double[]{42,42};case BREWERY->new double[]{-42,42};case DOCK->new double[]{0,72};default->new double[]{0,0};};
        if(layout==Layout.WARDS){p=new double[]{p[0]+Math.signum(p[0])*8,p[1]};}
        else if(layout==Layout.BOULEVARD){p=new double[]{p[0],p[1]+Math.signum(p[1])*8};}
        return local(settlement,r,p[0],p[1]);
    }

    private static int orientationFor(StructureRole role){return switch(role){case BARRACKS,PRISON,OBSERVATORY->1;default->0;};}
    private static void addRoad(List<ConstructionIntent> out,Faction f,Settlement s,int i,SimPosition c,int w,int d,int rot,int p){addAt(out,f,s,StructureRole.ROAD,i,c,w,d,rot,p);}

    private static int houseFacing(int x,int z,int spacing,int baseRotation){
        int dx=signedStreetDelta(x,spacing),dz=signedStreetDelta(z,spacing);
        int local;if(Math.abs(dx)<=Math.abs(dz))local=dx>0?3:1;else local=dz>0?0:2;
        return Math.floorMod(baseRotation+local,4);
    }
    private static int signedStreetDelta(int value,int spacing){int nearest=(int)Math.round(value/(double)spacing)*spacing;return nearest-value;}
    private static int distanceToStreet(int value,int spacing){return Math.abs(signedStreetDelta(value,spacing));}

    /** Square spiral used to fill lots from the civic core outward. */
    private static int[] spiral(int n){
        if(n==0)return new int[]{0,0};int k=(int)Math.ceil((Math.sqrt(n+1)-1)/2.0),t=2*k+1,m=t*t;t--;
        if(n>=m-t)return new int[]{k-(m-n),-k};m-=t;
        if(n>=m-t)return new int[]{-k,-k+(m-n)};m-=t;
        if(n>=m-t)return new int[]{-k+(m-n),k};return new int[]{k,k-(m-t-n)};
    }

    private static SimPosition local(Settlement s,int quarterTurns,double x,double z){
        return switch(Math.floorMod(quarterTurns,4)){case 0->new SimPosition(s.position().x()+x,s.position().z()+z);case 1->new SimPosition(s.position().x()-z,s.position().z()+x);case 2->new SimPosition(s.position().x()-x,s.position().z()-z);default->new SimPosition(s.position().x()+z,s.position().z()-x);};
    }
    private static int adjustPriority(dev.livingrealms.sim.faction.DevelopmentPriority policy,StructureRole role,int base){int bonus=switch(policy){case BALANCED->0;case FOOD->(role==StructureRole.FARM||role==StructureRole.FISHERY||role==StructureRole.IRRIGATION||role==StructureRole.AQUEDUCT||role==StructureRole.WELL||role==StructureRole.MILL||role==StructureRole.BAKERY||role==StructureRole.PASTURE)?35:0;case HOUSING->role==StructureRole.HOUSE?60:0;case INDUSTRY->(role==StructureRole.WORKSHOP||role==StructureRole.FACTORY||role==StructureRole.MINE||role==StructureRole.LUMBER_CAMP||role==StructureRole.BREWERY)?35:0;case DEFENSE->(role==StructureRole.KEEP||role==StructureRole.BARRACKS||role==StructureRole.WALL||role==StructureRole.GATE||role==StructureRole.AIRFIELD)?35:0;};return Math.min(240,base+bonus);}
    private static Layout layout(Settlement settlement){return Layout.values()[Math.floorMod((int)mix(settlement.id()*0x9E3779B97F4A7C15L),Layout.values().length)];}
    private static long mix(long z){z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;return z^(z>>>31);}
    private static void addAt(List<ConstructionIntent> out,Faction faction,Settlement settlement,StructureRole role,int index,SimPosition center,int width,int depth,int rotation,int priority){out.add(new ConstructionIntent(key(settlement,role,index),faction.id(),settlement.id(),role,center,width,depth,Math.floorMod(rotation,4),priority));}
    private static String key(Settlement settlement,StructureRole role,int index){
        String base=role.name().toLowerCase(Locale.ROOT);
        // Roads, defensive envelopes and the keep change geometry as a settlement crosses tiers.
        // Include the tier in their completion key so a VILLAGE road/keep never prevents the
        // longer/larger TOWN or CITY version from being physically projected later.
        return switch(role){
            case ROAD,KEEP,WALL,GATE -> base+":"+settlement.tier().ordinal()+":"+index;
            default -> base+":"+index;
        };
    }

    private enum Layout { GRID, MARKET_CROSS, WARDS, BOULEVARD, OLD_TOWN }
}
