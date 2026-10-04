package dev.livingrealms.sim.transport;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Plans and maintains faction infrastructure. Physical road/rail blocks are projections of these routes. */
public final class TransportNetworkEngine {
    public void simulateDay(SimulationState state){
        // Route discovery is O(settlements × neighbours); run weekly so soak stays bounded as camps grow.
        if(state.clock().day()%7==0)discoverRoutes(state);
        for(TransportRoute route:state.routes()){
            Faction owner=state.findFaction(route.ownerFactionId()).orElse(null);if(owner==null){route.setOperational(false);continue;}
            route.improve(owner.technology()*.000025);
            route.adjustSecurity((owner.government().lawEnforcement()-.5)*.0002);
        }
    }
    private static void discoverRoutes(SimulationState state){
        // Build a legible regional graph rather than connecting every settlement to every other
        // settlement. Each settlement seeks its four nearest same-realm neighbours. This produces
        // dense road webs with recognizable trunks/branches and scales to many kingdoms.
        for(Faction faction:state.factions()){
            List<Settlement> settlements=faction.settlements();
            Set<RouteKey> existing=new HashSet<>();
            for(TransportRoute route:state.routes())if(route.ownerFactionId()==faction.id())existing.add(RouteKey.of(route.fromSettlementId(),route.toSettlementId(),route.mode()));
            for(Settlement a:settlements){
                List<Settlement> nearest=settlements.stream().filter(b->b.id()!=a.id())
                        .sorted(Comparator.comparingDouble(b->a.position().distanceTo(b.position())))
                        .limit(4).toList();
                for(Settlement b:nearest){
                    double d=a.position().distanceTo(b.position());if(!(d>1)||d>2_600)continue;
                    TransportMode mode=faction.technology()>=1.2&&a.tier().ordinal()>=Settlement.Tier.TOWN.ordinal()&&b.tier().ordinal()>=Settlement.Tier.TOWN.ordinal()?TransportMode.RAIL:TransportMode.ROAD;
                    RouteKey key=RouteKey.of(a.id(),b.id(),mode);
                    if(existing.contains(key))continue;
                    double cost=d*(mode==TransportMode.RAIL?.08:.018);ResourceType material=mode==TransportMode.RAIL?ResourceType.IRON:ResourceType.STONE;
                    if(faction.stockpile().get(material)<cost)continue;
                    faction.stockpile().take(material,cost);
                    state.addRoute(new TransportRoute(state.nextId(),faction.id(),a.id(),b.id(),mode,d,.38,.48,mode==TransportMode.RAIL?900:320));
                    existing.add(key);
                    state.history().add(new WorldEvent(state.clock().day(),"route_built",faction.name()+" "+mode+" "+a.name()+"-"+b.name()));
                }
            }
        }
    }
    private record RouteKey(long low,long high,TransportMode mode){
        static RouteKey of(long a,long b,TransportMode mode){return a<b?new RouteKey(a,b,mode):new RouteKey(b,a,mode);}
    }
    private static boolean sameEnds(TransportRoute r,long a,long b){return(r.fromSettlementId()==a&&r.toSettlementId()==b)||(r.fromSettlementId()==b&&r.toSettlementId()==a);}
    public static Optional<TransportRoute> bestRoute(SimulationState state,long ownerFactionId,long fromSettlementId,long toSettlementId){return state.routes().stream().filter(TransportRoute::operational).filter(r->r.ownerFactionId()==ownerFactionId&&sameEnds(r,fromSettlementId,toSettlementId)).max(Comparator.comparingDouble(TransportRoute::speedBlocksPerDay));}
}
