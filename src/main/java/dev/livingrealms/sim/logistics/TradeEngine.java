package dev.livingrealms.sim.logistics;

import dev.livingrealms.sim.economy.MarketEngine;
import dev.livingrealms.sim.economy.ResourceDominanceEngine;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.territory.Jurisdiction;
import dev.livingrealms.sim.territory.TerritoryEngine;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Persistent strategic logistics: contracts dispatch cargo, cargo travels, can be intercepted, then delivers. */
public final class TradeEngine {
    private static final double CARAVAN_SPEED_PER_DAY=180.0;
    private static final List<ResourceType> TRADED=List.of(ResourceType.FOOD,ResourceType.IRON,ResourceType.FUEL,ResourceType.TOOLS,ResourceType.TEXTILES,ResourceType.MACHINERY);

    public void simulateDay(SimulationState state, DeterministicRng rng) {
        Objects.requireNonNull(state,"state");Objects.requireNonNull(rng,"rng");advanceShipments(state,rng);dispatchShipments(state);
    }

    private static void advanceShipments(SimulationState state,DeterministicRng rng) {
        List<Long> remove=new ArrayList<>();
        for(TradeShipment shipment:new ArrayList<>(state.shipments())) {
            shipment.advanceDistance(CARAVAN_SPEED_PER_DAY);
            if(intercepted(state,shipment,rng)) {state.history().add(new WorldEvent(state.clock().day(),"trade_intercepted",describe(shipment)));remove.add(shipment.id());continue;}
            if(shipment.arrived()) {
                Faction buyer=state.findFaction(shipment.buyerFactionId()).orElse(null);
                if(buyer!=null){
                    Settlement dest=buyer.settlements().stream().min(Comparator.comparingDouble(s->s.position().distanceTo(shipment.destination()))).orElse(null);
                    // Split delivery: local market barn + faction strategic reserve.
                    double localShare=shipment.amount()*.65,strategic=shipment.amount()-localShare;
                    if(dest!=null){dest.stockpile().add(shipment.resource(),localShare);dest.enforceStorageCaps();}
                    else strategic=shipment.amount();
                    buyer.stockpile().add(shipment.resource(),strategic);
                    state.history().add(new WorldEvent(state.clock().day(),"trade_delivered",describe(shipment)));
                }
                remove.add(shipment.id());
            }
        }
        for(long id:remove)state.removeShipment(id);
    }

    private static boolean intercepted(SimulationState state,TradeShipment shipment,DeterministicRng rng) {
        SimPosition p=shipment.position();double risk=.0006;
        Jurisdiction jurisdiction=TerritoryEngine.resolve(state.factions(),p,state.config().borderDisputeThreshold());
        if(jurisdiction.claimed()&&jurisdiction.primaryFactionId()!=shipment.sellerFactionId()&&jurisdiction.primaryFactionId()!=shipment.buyerFactionId()){
            Faction controller=state.findFaction(jurisdiction.primaryFactionId()).orElse(null);if(controller!=null){boolean hostile=isAtWar(controller,shipment.sellerFactionId())||isAtWar(controller,shipment.buyerFactionId());risk+=hostile?.11:.004*(1-controller.government().lawEnforcement());}
        }
        if(jurisdiction.contested())risk+=.018;
        for(Faction faction:state.factions()){
            if(faction.id()==shipment.sellerFactionId()||faction.id()==shipment.buyerFactionId())continue;if(!isAtWar(faction,shipment.sellerFactionId())&&!isAtWar(faction,shipment.buyerFactionId()))continue;
            double nearest=faction.settlements().stream().mapToDouble(s->s.position().distanceTo(p)).min().orElse(Double.POSITIVE_INFINITY);if(nearest<300)risk+=.08*(1.0-nearest/300.0);
        }
        return rng.chance(Math.min(.35,risk));
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
        // Strategic need looks at faction treasury stores; surplus can be drawn from local barns too.
        double desired=desiredReserve(buyer,resource);
        double need=Math.max(0,desired-buyer.stockpile().get(resource));
        double sellerHeld=seller.stockpile().get(resource)+localHeld(seller,resource);
        double sellerReserve=desiredReserve(seller,resource)*.75;
        double surplus=Math.max(0,sellerHeld-sellerReserve);
        Settlement origin=closestPairOrigin(seller,buyer),destination=closestTo(buyer,origin.position());
        long day=state.clock().day();
        double sellerAsk=dev.livingrealms.sim.economy.LocalMarketEngine.quote(seller,origin,resource,day).unitPrice();
        double buyerBid=dev.livingrealms.sim.economy.LocalMarketEngine.quote(buyer,destination,resource,day).unitPrice();
        boolean arbitrage=buyerBid>=sellerAsk*1.05;
        if(!arbitrage&&need<desired*.35)return; // no price gap and buyer not short on treasury stores
        if(need<=0&&arbitrage)need=Math.min(64,surplus*.15); // speculative shipment on clear price gap
        double leverage=ResourceDominanceEngine.sellerLeverageMultiplier(state,seller.id(),resource);double price=Math.max(.01,(sellerAsk+buyerBid)*.5*leverage);
        double amount=Math.min(Math.min(need,surplus),buyer.treasury()/Math.max(.01,price));amount=Math.min(amount,256.0);if(amount<1.0||seller.settlements().isEmpty()||buyer.settlements().isEmpty())return;
        drawForTrade(seller,origin,resource,amount);double value=amount*price;buyer.addTreasury(-value);seller.addTreasury(value);
        TradeShipment shipment=new TradeShipment(state.nextId(),seller.id(),buyer.id(),resource,amount,value,origin.position(),destination.position());state.addShipment(shipment);state.history().add(new WorldEvent(state.clock().day(),"trade_dispatched",describe(shipment)+", price="+String.format(java.util.Locale.ROOT,"%.2f",price)));
    }

    private static double localHeld(Faction f,ResourceType r){double t=0;for(Settlement s:f.settlements())t+=s.stockpile().get(r);return t;}
    private static void drawForTrade(Faction seller,Settlement origin,ResourceType resource,double amount){
        double fromLocal=origin.stockpile().take(resource,amount);
        if(fromLocal<amount)seller.stockpile().take(resource,amount-fromLocal);
    }

    private static Settlement closestPairOrigin(Faction seller,Faction buyer){Settlement best=seller.settlements().getFirst();double distance=Double.POSITIVE_INFINITY;for(Settlement s:seller.settlements())for(Settlement b:buyer.settlements()){double d=s.position().distanceTo(b.position());if(d<distance){distance=d;best=s;}}return best;}
    private static Settlement closestTo(Faction faction,SimPosition target){return faction.settlements().stream().min(Comparator.comparingDouble(s->s.position().distanceTo(target))).orElseThrow();}
    private static double desiredReserve(Faction f,ResourceType r){return switch(r){case FOOD->Math.max(30,f.population()*.30);case IRON->Math.max(12,f.population()*.03);case FUEL->Math.max(24,f.population()*.004);case TOOLS->Math.max(8,f.population()*.008);case TEXTILES->Math.max(8,f.population()*.01);case MACHINERY->Math.max(4,f.population()*.001);default->10;};}
    private static String describe(TradeShipment s){return "shipment="+s.id()+", "+s.resource()+"="+s.amount()+", seller="+s.sellerFactionId()+", buyer="+s.buyerFactionId();}
}
