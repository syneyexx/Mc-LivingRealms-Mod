package dev.livingrealms.sim.logistics;

import dev.livingrealms.sim.economy.LocalMarketEngine;
import dev.livingrealms.sim.economy.ResourceDominanceEngine;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.territory.Jurisdiction;
import dev.livingrealms.sim.territory.TerritoryEngine;
import dev.livingrealms.sim.transport.TransportNetworkEngine;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Persistent strategic logistics: contracts dispatch cargo, cargo travels, can be intercepted, then delivers. */
public final class TradeEngine {
    private static final double CARAVAN_SPEED_PER_DAY=180.0;
    /** Fraction of paid value the seller refunds the buyer when a caravan is lost (guild/insurance bond). */
    private static final double INTERCEPT_INSURANCE_RATE=.55;
    private static final List<ResourceType> TRADED=List.of(
            ResourceType.GRAIN,ResourceType.BREAD,ResourceType.MEAT,ResourceType.WOOL,
            ResourceType.FOOD,ResourceType.IRON,ResourceType.FUEL,ResourceType.TOOLS,ResourceType.TEXTILES,ResourceType.MACHINERY);

    public void simulateDay(SimulationState state, DeterministicRng rng) {
        Objects.requireNonNull(state,"state");Objects.requireNonNull(rng,"rng");
        ResourceDominanceEngine.withMemo(state, () -> {
            advanceShipments(state,rng,1.0);
            dispatchShipments(state);
        });
    }

    /**
     * Sub-day presentation pulse: advances shipment progress by a fraction of a day without
     * running dispatch/intercept at full daily intensity. Does not advance the world clock.
     */
    public void presentationPulse(SimulationState state, DeterministicRng rng, double dayFraction) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rng, "rng");
        if (!(dayFraction > 0) || !Double.isFinite(dayFraction)) return;
        advanceShipments(state, rng, Math.min(1.0, dayFraction));
    }

    private static void advanceShipments(SimulationState state,DeterministicRng rng,double dayFraction) {
        List<Long> remove=new ArrayList<>();
        for(TradeShipment shipment:new ArrayList<>(state.shipments())) {
            TransportRoute route=routeFor(state,shipment).orElse(null);
            double speed=(route==null?CARAVAN_SPEED_PER_DAY:route.speedBlocksPerDay())*dayFraction;
            if(route!=null&&!route.operational())speed*=.35; // detour on collapsed road
            shipment.advanceDistance(speed);
            // Full intercept risk only on whole-day steps; microsteps skip interception.
            if(dayFraction>=.999&&intercepted(state,shipment,route,rng)) {
                settleInsurance(state,shipment);
                state.liveness().onShipmentIntercepted();
                state.history().add(new WorldEvent(state.clock().day(),"trade_intercepted",describe(shipment)+(route==null?"":", route="+route.id())));
                remove.add(shipment.id());continue;
            }
            if(shipment.arrived()) {
                Faction buyer=state.findFaction(shipment.buyerFactionId()).orElse(null);
                if(buyer!=null){
                    Settlement dest=buyer.settlements().stream().min(Comparator.comparingDouble(s->s.position().distanceTo(shipment.destination()))).orElse(null);
                    double localShare=shipment.amount()*.65,strategic=shipment.amount()-localShare;
                    if(dest!=null){dest.stockpile().add(shipment.resource(),localShare);dest.enforceStorageCaps();}
                    else strategic=shipment.amount();
                    buyer.stockpile().add(shipment.resource(),strategic);
                    state.liveness().onShipmentDelivered();
                    state.history().add(new WorldEvent(state.clock().day(),"trade_delivered",describe(shipment)+(route==null?"":", route="+route.id())));
                }
                remove.add(shipment.id());
            }
        }
        for(long id:remove)state.removeShipment(id);
    }

    /** Prefer an operational same-realm road between the nearest seller/buyer settlements; else any matching ends. */
    private static Optional<TransportRoute> routeFor(SimulationState state,TradeShipment shipment){
        Settlement from=nearestSettlement(state,shipment.origin(),shipment.sellerFactionId());
        Settlement to=nearestSettlement(state,shipment.destination(),shipment.buyerFactionId());
        if(from==null||to==null)return Optional.empty();
        Optional<TransportRoute> owned=TransportNetworkEngine.bestRoute(state,shipment.sellerFactionId(),from.id(),to.id());
        if(owned.isPresent())return owned;
        return state.routes().stream().filter(TransportRoute::operational)
                .filter(r->(r.fromSettlementId()==from.id()&&r.toSettlementId()==to.id())||(r.fromSettlementId()==to.id()&&r.toSettlementId()==from.id()))
                .max(Comparator.comparingDouble(TransportRoute::speedBlocksPerDay));
    }

    private static Settlement nearestSettlement(SimulationState state,SimPosition pos,long preferFactionId){
        Settlement best=null;double bestD=Double.POSITIVE_INFINITY;
        for(Faction f:state.factions())for(Settlement s:f.settlements()){
            double d=s.position().distanceTo(pos);if(f.id()==preferFactionId)d*=.85;
            if(d<bestD){bestD=d;best=s;}
        }
        return best;
    }

    private static boolean intercepted(SimulationState state,TradeShipment shipment,TransportRoute route,DeterministicRng rng) {
        SimPosition p=shipment.position();double risk=.0006;
        if(route!=null){
            risk+= (1.0-route.security())*.045;
            risk+= (1.0-route.quality())*.02;
            if(!route.operational())risk+=.08;
        }
        Jurisdiction jurisdiction=TerritoryEngine.resolve(state.factions(),p,state.config().borderDisputeThreshold());
        if(jurisdiction.claimed()&&jurisdiction.primaryFactionId()!=shipment.sellerFactionId()&&jurisdiction.primaryFactionId()!=shipment.buyerFactionId()){
            Faction controller=state.findFaction(jurisdiction.primaryFactionId()).orElse(null);if(controller!=null){boolean hostile=isAtWar(controller,shipment.sellerFactionId())||isAtWar(controller,shipment.buyerFactionId());risk+=hostile?.11:.004*(1-controller.government().lawEnforcement());}
        }
        if(jurisdiction.contested())risk+=.018;
        for(Faction faction:state.factions()){
            if(faction.id()==shipment.sellerFactionId()||faction.id()==shipment.buyerFactionId())continue;if(!isAtWar(faction,shipment.sellerFactionId())&&!isAtWar(faction,shipment.buyerFactionId()))continue;
            double nearest=faction.settlements().stream().mapToDouble(s->s.position().distanceTo(p)).min().orElse(Double.POSITIVE_INFINITY);if(nearest<300)risk+=.08*(1.0-nearest/300.0);
        }
        // Land bandit hideouts near the caravan raise intercept risk.
        long nearbyBands=state.pirateHideouts().stream().filter(h->h.active()&&h.position().distanceTo(p)<420).count();
        risk+=Math.min(.12,nearbyBands*.035);
        // Escort strength from logistics fields reduces intercept chance (guards protect cargo).
        risk*=Math.max(.22,1.0-shipment.escortStrength()*.65);
        risk=Math.min(.38,risk);
        if(!rng.chance(risk))return false;
        // Partial loss when escorts hold: cargo delayed/damaged but shipment continues unless total wipe.
        if(shipment.escortStrength()>.45&&shipment.lossState()==TradeShipment.LossState.NONE&&rng.chance(.55)){
            shipment.restoreLogistics(shipment.originSettlementId(),shipment.destinationSettlementId(),shipment.routeId(),shipment.transportModeOrdinal(),
                    shipment.departureDay(),shipment.expectedArrivalDay()+2,Math.min(1,shipment.risk()+.12),shipment.escortStrength()*.7,
                    TradeShipment.LossState.PARTIAL,shipment.delayDays()+2);
            if(route!=null)route.adjustSecurity(-.04);
            state.history().add(new WorldEvent(state.clock().day(),"trade_partial_loss",describe(shipment)+", escorts held"));
            return false;
        }
        if(route!=null)route.adjustSecurity(-.08);
        shipment.restoreLogistics(shipment.originSettlementId(),shipment.destinationSettlementId(),shipment.routeId(),shipment.transportModeOrdinal(),
                shipment.departureDay(),shipment.expectedArrivalDay(),Math.min(1,shipment.risk()+.2),0,TradeShipment.LossState.TOTAL,shipment.delayDays());
        return true;
    }

    /** Buyer already paid; seller refunds an insured share when cargo is lost. */
    private static void settleInsurance(SimulationState state,TradeShipment shipment){
        Faction seller=state.findFaction(shipment.sellerFactionId()).orElse(null);
        Faction buyer=state.findFaction(shipment.buyerFactionId()).orElse(null);
        if(seller==null||buyer==null)return;
        double refund=shipment.value()*INTERCEPT_INSURANCE_RATE;
        double paid=Math.min(seller.treasury(),refund);
        if(paid<=0){
            state.history().add(new WorldEvent(state.clock().day(),"trade_insurance_default",
                    describe(shipment)+", owed="+Math.round(refund)));
            return;
        }
        seller.addTreasury(-paid);
        buyer.addTreasury(paid);
        state.history().add(new WorldEvent(state.clock().day(),"trade_insurance_paid",
                describe(shipment)+", refund="+Math.round(paid)));
    }

    private static boolean isAtWar(Faction faction,long other){DiplomaticRelation rel=faction.relations().get(other);return rel!=null&&rel.status()==RelationStatus.WAR;}

    private static void dispatchShipments(SimulationState state) {
        List<Faction> factions=state.factions();for(int i=0;i<factions.size();i++)for(int j=i+1;j<factions.size();j++){
            Faction a=factions.get(i),b=factions.get(j);DiplomaticRelation ar=a.relations().get(b.id()),br=b.relations().get(a.id());
            if(ar==null||br==null||!ar.tradeAgreement()||!br.tradeAgreement()||ar.status()==RelationStatus.WAR||br.status()==RelationStatus.WAR)continue;
            for(ResourceType resource:TRADED){dispatchOne(state,a,b,resource);dispatchOne(state,b,a,resource);}
        }
    }

    private static void dispatchOne(SimulationState state,Faction seller,Faction buyer,ResourceType resource) {
        if(state.shipments().stream().anyMatch(s->s.sellerFactionId()==seller.id()&&s.buyerFactionId()==buyer.id()&&s.resource()==resource))return;
        if(seller.settlements().isEmpty()||buyer.settlements().isEmpty())return;
        double desired=desiredReserve(buyer,resource);
        double need=Math.max(0,desired-buyer.stockpile().get(resource));
        double sellerHeld=seller.stockpile().get(resource)+localHeld(seller,resource);
        double sellerReserve=desiredReserve(seller,resource)*.75;
        double surplus=Math.max(0,sellerHeld-sellerReserve);
        SettlementPair pair=chooseTradePair(state,seller,buyer);
        if(pair==null)return;
        Settlement origin=pair.origin(),destination=pair.destination();
        long day=state.clock().day();
        double sellerAsk=LocalMarketEngine.quote(seller,origin,resource,day).unitPrice();
        double buyerBid=LocalMarketEngine.quote(buyer,destination,resource,day).unitPrice();
        boolean arbitrage=buyerBid>=sellerAsk*1.05;
        if(!arbitrage&&need<desired*.35)return;
        if(need<=0&&arbitrage)need=Math.min(64,surplus*.15);
        double leverage=ResourceDominanceEngine.sellerLeverageMultiplier(state,seller.id(),resource);double price=Math.max(.01,(sellerAsk+buyerBid)*.5*leverage);
        double amount=Math.min(Math.min(need,surplus),buyer.treasury()/Math.max(.01,price));amount=Math.min(amount,256.0);if(amount<1.0)return;
        // Capacity: skip dispatch if the best route is already overloaded with active shipments.
        Optional<TransportRoute> route=TransportNetworkEngine.bestRoute(state,seller.id(),origin.id(),destination.id());
        if(route.isPresent()){
            long onRoute=state.shipments().stream().filter(s->{
                Optional<TransportRoute> r=routeFor(state,s);return r.isPresent()&&r.get().id()==route.get().id();
            }).count();
            if(onRoute>=Math.max(1,(long)(route.get().capacityPerDay()/180.0)))return;
        }
        drawForTrade(seller,origin,resource,amount);double value=amount*price;buyer.addTreasury(-value);seller.addTreasury(value);
        TradeShipment shipment=new TradeShipment(state.nextId(),seller.id(),buyer.id(),resource,amount,value,origin.position(),destination.position());
        double dist=origin.position().distanceTo(destination.position());long travelDays=Math.max(1L,Math.round(dist/220.0));
        double routeRisk=route.map(r->Mathx.clamp(1.0-r.security(),0,1)).orElse(.35);
        double escort=Mathx.clamp(route.map(r->r.security()).orElse(.4)+Math.min(.35,value/800.0)+Math.min(.2,routeRisk*.4),0,1);
        shipment.restoreLogistics(origin.id(),destination.id(),route.map(r->r.id()).orElse(0L),route.map(r->r.mode().ordinal()).orElse(-1),day,day+travelDays,routeRisk,escort,TradeShipment.LossState.NONE,0);
        state.addShipment(shipment);state.liveness().onShipmentDispatched();state.history().add(new WorldEvent(state.clock().day(),"trade_dispatched",describe(shipment)+", price="+String.format(java.util.Locale.ROOT,"%.2f",price)+(route.map(r->", route="+r.id()).orElse(""))+", escort="+String.format(java.util.Locale.ROOT,"%.2f",escort)));
    }

    private static double localHeld(Faction f,ResourceType r){double t=0;for(Settlement s:f.settlements())t+=s.stockpile().get(r);return t;}
    private static void drawForTrade(Faction seller,Settlement origin,ResourceType resource,double amount){
        double fromLocal=origin.stockpile().take(resource,amount);
        if(fromLocal<amount)seller.stockpile().take(resource,amount-fromLocal);
    }

    private record SettlementPair(Settlement origin,Settlement destination){}

    /**
     * Prefer operational corridor hubs, then a tiny candidate set of top towns/ports.
     * Bounded to avoid O(settlements² × routes) per daily trade dispatch.
     */
    private static SettlementPair chooseTradePair(SimulationState state,Faction seller,Faction buyer){
        List<Settlement> sellerCandidates=tradeCandidates(seller);
        List<Settlement> buyerCandidates=tradeCandidates(buyer);
        SettlementPair best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(Settlement s:sellerCandidates)for(Settlement b:buyerCandidates){
            double d=s.position().distanceTo(b.position());
            boolean routed=TransportNetworkEngine.bestRoute(state,seller.id(),s.id(),b.id()).isPresent();
            boolean hubish=s.tier().ordinal()>=Settlement.Tier.TOWN.ordinal()&&b.tier().ordinal()>=Settlement.Tier.TOWN.ordinal();
            double score=d-(routed?400:0)-(hubish?160:0)-(s.geography().shipSuitable()&&b.geography().shipSuitable()?200:0);
            if(score<bestScore){bestScore=score;best=new SettlementPair(s,b);}
        }
        if(best!=null)return best;
        if(seller.settlements().isEmpty()||buyer.settlements().isEmpty())return null;
        Settlement origin=seller.settlements().getFirst();
        Settlement destination=closestTo(buyer,origin.position());
        if(destination==null)return null;
        return new SettlementPair(origin,destination);
    }

    private static List<Settlement> tradeCandidates(Faction faction){
        return faction.settlements().stream()
                .sorted(Comparator.comparingInt((Settlement s)->s.tier().ordinal()).reversed()
                        .thenComparingInt(Settlement::population).reversed()
                        .thenComparingLong(Settlement::id))
                .limit(4)
                .toList();
    }
    private static Settlement closestTo(Faction faction,SimPosition target){
        return faction.settlements().stream().min(Comparator.comparingDouble(s->s.position().distanceTo(target))).orElse(null);
    }
    private static double desiredReserve(Faction f,ResourceType r){return switch(r){
        case GRAIN,BREAD,MEAT,FOOD -> Math.max(30,f.population()*.30);
        case WOOL,TEXTILES -> Math.max(8,f.population()*.01);
        case IRON -> Math.max(12,f.population()*.03);
        case FUEL -> Math.max(24,f.population()*.004);
        case TOOLS -> Math.max(8,f.population()*.008);
        case MACHINERY -> Math.max(4,f.population()*.001);
        default -> 10;
    };}
    private static String describe(TradeShipment s){return "shipment="+s.id()+", "+s.resource()+"="+s.amount()+", seller="+s.sellerFactionId()+", buyer="+s.buyerFactionId();}
}
