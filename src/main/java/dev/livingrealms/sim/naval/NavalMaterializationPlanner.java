package dev.livingrealms.sim.naval;

import dev.livingrealms.sim.world.SimPosition;
import java.util.*;

/** Bounded physical projection of strategic fleets near players. */
public final class NavalMaterializationPlanner {
    private NavalMaterializationPlanner(){}
    public static List<FleetProjection> plan(Collection<Fleet> fleets,Collection<SimPosition> players,double radius,int budget){
        Objects.requireNonNull(fleets);Objects.requireNonNull(players);if(radius<=0||!Double.isFinite(radius)||budget<0)throw new IllegalArgumentException("planner config");if(players.isEmpty()||budget==0)return List.of();
        record Candidate(Fleet fleet,double distance){}
        List<Candidate> candidates=new ArrayList<>();for(Fleet f:fleets){if(f.destroyed())continue;double d=Double.POSITIVE_INFINITY;for(SimPosition p:players)d=Math.min(d,f.position().distanceTo(p));if(d<=radius)candidates.add(new Candidate(f,d));}
        candidates.sort(Comparator.comparingDouble(Candidate::distance).thenComparingLong(c->c.fleet().id()));List<FleetProjection> out=new ArrayList<>();int left=budget;
        for(Candidate c:candidates){int desired=Math.min(12,Math.max(1,c.fleet().totalShips()));int allowed=Math.min(desired,left);List<ShipClass> classes=expanded(c.fleet());for(int slot=0;slot<allowed;slot++){ShipClass cls=classes.get(slot%classes.size());int represented=Math.max(1,(int)Math.ceil(c.fleet().totalShips()/(double)allowed));out.add(new FleetProjection(c.fleet().id(),c.fleet().factionId(),slot,cls,represented,c.fleet().position()));}left-=allowed;if(left<=0)break;}
        return List.copyOf(out);
    }
    private static List<ShipClass> expanded(Fleet fleet){List<ShipClass> out=new ArrayList<>();for(ShipClass c:ShipClass.values())for(int i=0;i<fleet.count(c);i++)out.add(c);return out.isEmpty()?List.of(ShipClass.PATROL_BOAT):out;}
}
