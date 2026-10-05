package dev.livingrealms.sim.transport;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Plans and maintains faction infrastructure. Physical road/rail blocks are projections of these routes. */
public final class TransportNetworkEngine {
    public void simulateDay(SimulationState state){
        // Route discovery is O(settlements × neighbours); run weekly so soak stays bounded as camps grow.
        if(state.clock().day()%7==0)discoverRoutes(state);
        maintainRoutes(state);
    }

    /** Wear, washouts and bridge failure without treasury/stone upkeep; repair when the realm can pay. */
    private static void maintainRoutes(SimulationState state){
        long day=state.clock().day();
        for(TransportRoute route:state.routes()){
            Faction owner=state.findFaction(route.ownerFactionId()).orElse(null);if(owner==null){route.setOperational(false);continue;}
            // Natural wear + bandit/security pressure; technology slows decay slightly.
            double wear=.00035+(1.0-route.security())*.00022-Math.min(.00008,owner.technology()*.00003);
            if(owner.treasury()<120)wear+=.00080; // neglected roads / empty coffers
            if(day%90==0&&Math.floorMod(route.id()+day,11)==0)wear+=.05; // seasonal washout / bridge crack
            route.improve(-Math.max(0,wear));
            route.adjustSecurity((owner.government().lawEnforcement()-.5)*.0002);
            if(route.quality()<.12){
                if(route.operational()){
                    route.setOperational(false);
                    if(Math.floorMod(route.id()+day,23)==0)
                        state.history().add(new WorldEvent(day,"route_collapsed",owner.name()+" "+route.mode()+" route failed (quality depleted)"));
                }
            }else if(!route.operational()&&route.quality()>=.22){
                route.setOperational(true);
            }
            // Paid maintenance only when the realm is solvent — weekly, not every tithe scrap.
            boolean solvent=owner.treasury()>=200&&owner.stockpile().get(ResourceType.STONE)>=40;
            if(solvent&&day%7==0&&route.quality()<.70){
                double spend=Math.min(40,20+(1.0-route.quality())*35);
                owner.stockpile().take(ResourceType.STONE,spend*.40);
                owner.addTreasury(-Math.min(owner.treasury(),spend));
                route.improve(.025+.015*owner.technology());
                if(!route.operational()&&route.quality()>=.20)route.setOperational(true);
            }
            // Tech trickle still helps well-funded roads.
            if(owner.treasury()>=400)route.improve(owner.technology()*.00002);
        }
    }

    private static void discoverRoutes(SimulationState state){
        // The regional settlement graph is the topology authority. Physical road geometry is a
        // projection of these hierarchy edges; player position never decides whether an edge exists.
        for(Faction faction:state.factions()){
            Set<RouteKey> existing=new HashSet<>();
            for(TransportRoute route:state.routes())if(route.ownerFactionId()==faction.id())
                existing.add(RouteKey.of(route.fromSettlementId(),route.toSettlementId(),route.mode()));

            for(RegionalSettlementGraph.Edge edge:RegionalSettlementGraph.plan(faction)){
                Settlement a=state.findSettlement(edge.fromSettlementId()).orElse(null);
                Settlement b=state.findSettlement(edge.toSettlementId()).orElse(null);
                if(a==null||b==null)continue;
                double d=a.position().distanceTo(b.position());
                if(!(d>1)||d>5_200)continue;

                TransportMode mode=chooseMode(faction,a,b);
                RouteKey key=RouteKey.of(a.id(),b.id(),mode);
                if(existing.contains(key))continue;
                double cost=d*(mode==TransportMode.RAIL?.08:mode==TransportMode.SHIP?.04:mode==TransportMode.RIVER?.022:.018);
                ResourceType material=mode==TransportMode.RAIL?ResourceType.IRON:ResourceType.STONE;
                if(faction.stockpile().get(material)<cost)continue;
                faction.stockpile().take(material,cost);
                double capacity=switch(mode){case RAIL->900;case SHIP->700;case RIVER->480;case CARAVAN->280;default->320;};
                state.addRoute(new TransportRoute(state.nextId(),faction.id(),a.id(),b.id(),mode,d,.38,.48,capacity));
                existing.add(key);
                state.liveness().onRouteBuilt();
                state.history().add(new WorldEvent(state.clock().day(),"route_built",
                        faction.name()+" "+mode+" "+a.name()+"-"+b.name()+" ["+edge.relation()+"]"));
            }
        }
        discoverCrossFactionCorridors(state);
    }

    /**
     * Inter-realm trade corridors: when two factions have mutual trade agreements and peace,
     * connect a regional hub pair (capitals / market towns / ports) instead of every settlement pair.
     */
    private static void discoverCrossFactionCorridors(SimulationState state){
        List<Faction> factions=new ArrayList<>(state.factions());
        Set<RouteKey> existing=new HashSet<>();
        for(TransportRoute route:state.routes())existing.add(RouteKey.of(route.fromSettlementId(),route.toSettlementId(),route.mode()));
        for(int i=0;i<factions.size();i++)for(int j=i+1;j<factions.size();j++){
            Faction a=factions.get(i),b=factions.get(j);
            DiplomaticRelation ar=a.relations().get(b.id()),br=b.relations().get(a.id());
            if(ar==null||br==null||!ar.tradeAgreement()||!br.tradeAgreement())continue;
            if(ar.status()==RelationStatus.WAR||br.status()==RelationStatus.WAR)continue;
            if(ar.status()==RelationStatus.HOSTILE||br.status()==RelationStatus.HOSTILE)continue;
            Settlement hubA=tradeHub(a),hubB=tradeHub(b);
            if(hubA==null||hubB==null)continue;
            double d=hubA.position().distanceTo(hubB.position());
            if(!(d>40)||d>3_600)continue;
            // Prefer road/caravan corridors for land trade; ships only when both hubs are ship-suitable.
            TransportMode mode;
            if(hubA.geography().shipSuitable()&&hubB.geography().shipSuitable())mode=TransportMode.SHIP;
            else if(d>1_800)mode=TransportMode.CARAVAN;
            else mode=TransportMode.ROAD;
            RouteKey key=RouteKey.of(hubA.id(),hubB.id(),mode);
            if(existing.contains(key))continue;
            // Ownership: the richer solvent partner pays and owns the connector for maintenance.
            Faction owner=a.treasury()>=b.treasury()?a:b;
            double cost=d*(mode==TransportMode.SHIP?.035:mode==TransportMode.CARAVAN?.012:.016);
            ResourceType material=mode==TransportMode.SHIP?ResourceType.WOOD:ResourceType.STONE;
            if(owner.stockpile().get(material)<cost||owner.treasury()<Math.min(250,cost))continue;
            owner.stockpile().take(material,cost);
            owner.addTreasury(-Math.min(owner.treasury(),Math.min(250,cost*.25)));
            double capacity=switch(mode){case SHIP->640;case CARAVAN->300;default->360;};
            double security=.35+.08*Math.min(ar.opinion(),br.opinion())/100.0;
            state.addRoute(new TransportRoute(state.nextId(),owner.id(),hubA.id(),hubB.id(),mode,d,.34,security,capacity));
            existing.add(key);
            state.liveness().onRouteBuilt();
            state.history().add(new WorldEvent(state.clock().day(),"trade_corridor_built",
                    owner.name()+" "+mode+" corridor "+hubA.name()+"-"+hubB.name()+" (trade agreement)"));
        }
    }

    private static Settlement tradeHub(Faction faction){
        return faction.settlements().stream()
                .max(Comparator
                        .comparingInt((Settlement s)->s.tier().ordinal())
                        .thenComparingInt(Settlement::population)
                        .thenComparingDouble(s->s.geography().shipSuitable()?1:s.geography().harborSuitability())
                        .thenComparingLong(Settlement::id))
                .orElse(null);
    }

    /**
     * Road by default. Water modes require geography evidence (world-discovered profile preferred;
     * name heuristic remains only as bootstrap/fallback for old saves without discovery).
     */
    private static TransportMode chooseMode(Faction faction,Settlement a,Settlement b){
        SettlementGeographyProfile ga=a.geography(),gb=b.geography();
        if(ga.shipSuitable()&&gb.shipSuitable())return TransportMode.SHIP;
        if(ga.riverSuitable()&&gb.riverSuitable())return TransportMode.RIVER;
        if(ga.watery()&&gb.watery()&&(ga.navigableWater()||gb.navigableWater()))return TransportMode.RIVER;
        if(faction.technology()>=1.2&&isMajor(a)&&isMajor(b))
            return TransportMode.RAIL;
        if(a.role()==SettlementRole.HAMLET||b.role()==SettlementRole.HAMLET)
            return TransportMode.CARAVAN; // rural lanes / pack routes
        return TransportMode.ROAD;
    }
    private static boolean isMajor(Settlement settlement){
        return settlement.role()==SettlementRole.CAPITAL
                ||settlement.role()==SettlementRole.CITY
                ||settlement.role()==SettlementRole.TOWN;
    }
    private record RouteKey(long low,long high,TransportMode mode){
        static RouteKey of(long a,long b,TransportMode mode){return a<b?new RouteKey(a,b,mode):new RouteKey(b,a,mode);}
    }
    private static boolean sameEnds(TransportRoute r,long a,long b){return(r.fromSettlementId()==a&&r.toSettlementId()==b)||(r.fromSettlementId()==b&&r.toSettlementId()==a);}
    public static Optional<TransportRoute> bestRoute(SimulationState state,long ownerFactionId,long fromSettlementId,long toSettlementId){
        // Prefer own-faction routes, then any operational corridor with matching ends (cross-realm trade connectors).
        Optional<TransportRoute> owned=state.routes().stream().filter(TransportRoute::operational)
                .filter(r->r.ownerFactionId()==ownerFactionId&&sameEnds(r,fromSettlementId,toSettlementId))
                .max(Comparator.comparingDouble(TransportRoute::speedBlocksPerDay));
        if(owned.isPresent())return owned;
        return state.routes().stream().filter(TransportRoute::operational)
                .filter(r->sameEnds(r,fromSettlementId,toSettlementId))
                .max(Comparator.comparingDouble(TransportRoute::speedBlocksPerDay));
    }
}
