package dev.livingrealms.sim.naval;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Strategic naval production, movement, combat, blockades, convoy raiding and port logistics. */
public final class NavalEngine {
    public void simulateDay(SimulationState state,DeterministicRng rng){
        Objects.requireNonNull(state);Objects.requireNonNull(rng);maintainPorts(state);produceShips(state);assignMissions(state);moveAndSupply(state);resolveBattles(state,rng);resolveBlockadesAndRaiding(state,rng);state.removeDestroyedFleets();
    }

    private static void maintainPorts(SimulationState state){
        for(PortState port:state.ports()){
            Faction settlementOwner=state.findSettlementOwner(port.settlementId()).orElse(null);
            if(settlementOwner==null){port.setOperational(false);continue;}
            if(settlementOwner.id()!=port.factionId()){long previous=port.factionId();port.setFactionId(settlementOwner.id());state.history().add(new WorldEvent(state.clock().day(),"port_captured","port="+port.id()+", from="+previous+", to="+settlementOwner.id()));}
            Faction owner=settlementOwner;
            if(port.condition()<1&&owner.stockpile().get(ResourceType.TOOLS)>1){double spent=owner.stockpile().take(ResourceType.TOOLS,.5*port.level());port.repair(spent*.01);}
            port.adjustSecurity((owner.government().lawEnforcement()-.5)*.0005);
        }
    }

    private static void produceShips(SimulationState state){
        for(PortState port:state.ports()){
            if(!port.operational())continue;Faction faction=state.findFaction(port.factionId()).orElse(null);if(faction==null)continue;
            long localShips=state.fleets().stream().filter(f->f.factionId()==faction.id()&&f.homePortId()==port.id()).mapToLong(Fleet::totalShips).sum();if(localShips>=port.berthCapacity())continue;
            ShipClass cls=preferredClass(faction.technology());if(cls==null||faction.stockpile().get(ResourceType.MACHINERY)<cls.machineryCost()||faction.stockpile().get(ResourceType.IRON)<cls.ironCost())continue;
            faction.stockpile().take(ResourceType.MACHINERY,cls.machineryCost());faction.stockpile().take(ResourceType.IRON,cls.ironCost());faction.stockpile().take(ResourceType.FUEL,Math.min(cls.fuelCapacity(),faction.stockpile().get(ResourceType.FUEL)));
            Optional<Fleet> existing=state.fleets().stream().filter(f->f.factionId()==faction.id()&&f.homePortId()==port.id()&&!f.destroyed()&&f.position().distanceTo(port.position())<80).findFirst();
            if(existing.isPresent())existing.get().addShips(cls,1);else state.addFleet(new Fleet(state.nextId(),faction.id(),port.id(),port.position(),cls,1));
            state.history().add(new WorldEvent(state.clock().day(),"ship_built","faction="+faction.id()+", port="+port.id()+", class="+cls));
        }
    }

    private static ShipClass preferredClass(double tech){ShipClass best=null;for(ShipClass c:ShipClass.values()){if(c==ShipClass.CARGO_SHIP||c==ShipClass.LANDING_SHIP)continue;if(tech>=c.techRequired())best=c;}return best;}

    private static void assignMissions(SimulationState state){
        for(Fleet fleet:state.fleets()){
            if(fleet.destroyed())continue;PortState home=state.findPort(fleet.homePortId()).orElse(null);
            if(home==null||home.factionId()!=fleet.factionId()||!home.operational()){home=nearestFriendlyPort(state,fleet);if(home!=null)fleet.setHomePortId(home.id());}
            if(home==null){fleet.assign(NavalMission.PATROL,fleet.position());continue;}
            if(fleet.fuel()<.22||fleet.supply()<.22){fleet.assign(NavalMission.RETURN_TO_PORT,home.position());continue;}
            if(fleet.mission()==NavalMission.RETURN_TO_PORT&&fleet.position().distanceTo(home.position())<15){fleet.assign(NavalMission.IDLE,home.position());continue;}
            if(fleet.mission()!=NavalMission.IDLE&&fleet.mission()!=NavalMission.PATROL)continue;
            Faction owner=state.findFaction(fleet.factionId()).orElse(null);if(owner==null)continue;
            Set<Long> enemies=new LinkedHashSet<>();for(var e:owner.relations().entrySet())if(e.getValue().status()==RelationStatus.WAR)enemies.add(e.getKey());
            PortState enemyPort=state.ports().stream().filter(PortState::operational).filter(p->enemies.contains(p.factionId())).min(Comparator.comparingDouble(p->p.position().distanceTo(fleet.position()))).orElse(null);
            if(enemyPort!=null&&fleet.position().distanceTo(enemyPort.position())<=fleet.range()*10)fleet.assign(NavalMission.BLOCKADE,enemyPort.position());
            else fleet.assign(NavalMission.PATROL,patrolPoint(home.position(),fleet.id(),state.clock().day()));
        }
    }

    private static PortState nearestFriendlyPort(SimulationState state,Fleet fleet){return state.ports().stream().filter(PortState::operational).filter(p->p.factionId()==fleet.factionId()).min(Comparator.comparingDouble(p->p.position().distanceTo(fleet.position()))).orElse(null);}

    private static SimPosition patrolPoint(SimPosition center,long fleetId,long day){double angle=((fleetId*31+day*7)%360)*Math.PI/180.0;double radius=120+(fleetId%5)*35;return new SimPosition(center.x()+Math.cos(angle)*radius,center.z()+Math.sin(angle)*radius);}

    private static void moveAndSupply(SimulationState state){
        for(Fleet fleet:state.fleets()){
            if(fleet.destroyed())continue;fleet.moveDay();PortState home=state.findPort(fleet.homePortId()).orElse(null);if(home==null||fleet.position().distanceTo(home.position())>=30)continue;Faction owner=state.findFaction(fleet.factionId()).orElse(null);if(owner==null)continue;
            double fuelNeed=Math.max(.1,fleet.fuelDemand());double fuel=owner.stockpile().take(ResourceType.FUEL,fuelNeed);fleet.refuel(fuel/fuelNeed*.35);double foodNeed=Math.max(.1,fleet.totalShips()*.25);double food=owner.stockpile().take(ResourceType.FOOD,foodNeed);fleet.resupply(food/foodNeed*.25);fleet.adjustReadiness(home.repairPerDay());
        }
    }

    private static void resolveBattles(SimulationState state,DeterministicRng rng){
        List<Fleet> fleets=new ArrayList<>(state.fleets());for(int i=0;i<fleets.size();i++)for(int j=i+1;j<fleets.size();j++){
            Fleet a=fleets.get(i),b=fleets.get(j);if(a.destroyed()||b.destroyed()||a.factionId()==b.factionId()||a.position().distanceTo(b.position())>140)continue;
            Faction fa=state.findFaction(a.factionId()).orElse(null);if(fa==null||fa.relations().get(b.factionId())==null||fa.relations().get(b.factionId()).status()!=RelationStatus.WAR)continue;
            double pa=a.combatPower()*rng.between(.82,1.18),pb=b.combatPower()*rng.between(.82,1.18),sum=Math.max(.1,pa+pb);double lossA=Math.min(.65,pb/sum*rng.between(.10,.32)),lossB=Math.min(.65,pa/sum*rng.between(.10,.32));a.loseFraction(lossA);b.loseFraction(lossB);a.gainExperience(.015);b.gainExperience(.015);state.history().add(new WorldEvent(state.clock().day(),"naval_battle","fleets="+a.id()+"/"+b.id()+", survivors="+a.totalShips()+"/"+b.totalShips()));
        }
    }

    private static void resolveBlockadesAndRaiding(SimulationState state,DeterministicRng rng){
        for(Fleet fleet:state.fleets()){
            if(fleet.destroyed()||fleet.mission()!=NavalMission.BLOCKADE&&fleet.mission()!=NavalMission.CONVOY_RAID)continue;Faction owner=state.findFaction(fleet.factionId()).orElse(null);if(owner==null)continue;
            for(PortState port:state.ports()){
                if(!port.operational()||port.factionId()==fleet.factionId()||fleet.position().distanceTo(port.position())>160)continue;var rel=owner.relations().get(port.factionId());if(rel==null||rel.status()!=RelationStatus.WAR)continue;port.damage(Math.min(.025,.002*fleet.totalShips()));state.findSettlement(port.settlementId()).ifPresent(s->{s.adjustProsperity(-.004*Math.min(5,fleet.totalShips()));s.setPublicOrder(s.publicOrder()-.002);});
            }
            List<TradeShipment> shipments=new ArrayList<>(state.shipments());for(TradeShipment shipment:shipments){if(shipment.sellerFactionId()==fleet.factionId()||shipment.buyerFactionId()==fleet.factionId())continue;var rel=owner.relations().get(shipment.sellerFactionId());if(rel==null||rel.status()!=RelationStatus.WAR)continue;SimPosition p=shipment.origin().lerp(shipment.destination(),shipment.progress());if(p.distanceTo(fleet.position())>120)continue;double chance=Math.min(.75,.06+.015*fleet.totalShips()+.08*fleet.readiness());if(rng.nextDouble()<chance)state.recordPhysicalShipmentLoss(shipment.id(),"naval_interdiction:fleet="+fleet.id());}
        }
    }
}
