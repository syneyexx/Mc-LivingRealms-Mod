package dev.livingrealms.sim.military;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;

/** Bounded tactical projection plan. One entity can represent multiple strategic personnel. */
public final class MilitaryMaterializationPlanner {
    private MilitaryMaterializationPlanner(){}
    public static List<MilitaryProjection> plan(Collection<Faction> factions,Collection<SimPosition> players,double radius,int globalBudget){
        Objects.requireNonNull(factions);Objects.requireNonNull(players);if(radius<=0||!Double.isFinite(radius)||globalBudget<0)throw new IllegalArgumentException("planner config");if(players.isEmpty()||globalBudget==0)return List.of();
        List<Candidate> candidates=new ArrayList<>();
        for(Faction f:factions)for(Army a:f.armies()){if(a.destroyed())continue;double d=nearest(a.position(),players);if(d<=radius)candidates.add(new Candidate(f,a,d));}
        candidates.sort(Comparator.comparingDouble(Candidate::distance).thenComparingLong(c->c.army().id()));
        List<MilitaryProjection> out=new ArrayList<>();int left=globalBudget;
        for(Candidate c:candidates){int desired=Math.min(32,Math.max(1,(int)Math.ceil(Math.sqrt(c.army().totalPersonnel())/1.8)));int allowed=Math.min(desired,left);int represented=Math.max(1,(int)Math.ceil(c.army().totalPersonnel()/(double)allowed));for(int slot=0;slot<allowed;slot++)out.add(new MilitaryProjection(c.army().id(),c.faction().id(),slot,classFor(c.army(),slot,allowed),represented,c.army().position()));left-=allowed;if(left<=0)break;}
        return List.copyOf(out);
    }
    private static MilitaryUnitClass classFor(Army a,int slot,int total){int armor=Math.min(total,Math.max(0,a.armor()));int artillery=Math.min(total-armor,Math.max(0,a.artillery()));int cavalry=Math.min(total-armor-artillery,Math.max(0,a.cavalry()/2));if(slot<armor)return MilitaryUnitClass.ARMOR;if(slot<armor+artillery)return MilitaryUnitClass.ARTILLERY;if(slot<armor+artillery+cavalry)return MilitaryUnitClass.CAVALRY;return MilitaryUnitClass.INFANTRY;}
    private static double nearest(SimPosition p,Collection<SimPosition> players){double b=Double.POSITIVE_INFINITY;for(SimPosition x:players)b=Math.min(b,p.distanceTo(x));return b;}
    private record Candidate(Faction faction,Army army,double distance){}
}
