package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.naval.*;
import dev.livingrealms.sim.util.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Pirate bands, hideouts, shipment interception, and fleet engagements.
 */
public final class PiracyEngine {
    private PiracyEngine() {}

    public static void simulatePiracy(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();Map<Long,PortState> portsBySettlement=new HashMap<>();for(PortState p:state.ports())if(p.operational())portsBySettlement.put(p.settlementId(),p);
        for(PirateHideout hideout:state.pirateHideouts())if(hideout.active()){Faction hunter=nearestAntiPiracyFaction(state,hideout);if(hunter!=null){double intel=state.ensureFactionCivilization(hunter.id()).intelligence();double discovery=Math.min(.45,.015+intel*.12);if(hideout.discoveredByFactionId()==0&&rng.chance(discovery))hideout.discover(hunter.id());if(hideout.discoveredByFactionId()==hunter.id()&&antiPiracyPressure(state,hunter,hideout.position())>hideout.defense()+.25&&rng.chance(.12)){double recovered=hideout.takeLoot(hideout.storedLoot());hunter.addTreasury(recovered*.7);hideout.destroy();state.findPirateBand(hideout.bandId()).ifPresent(b->{b.adjustMorale(-.35);if(b.morale()<.18)b.disband();});state.history().add(new WorldEvent(day,"pirate_hideout_destroyed","hideout="+hideout.id()+", faction="+hunter.id()+", recovered="+Math.round(recovered*.7)));}}}
        if(day%30==0)for(var e:portsBySettlement.entrySet()){
            Settlement settlement=state.findSettlement(e.getKey()).orElse(null);Faction owner=settlement==null?null:state.findSettlementOwner(settlement.id()).orElse(null);if(settlement==null||owner==null)continue;
            SettlementCivilizationState civ=state.ensureSettlementCivilization(settlement.id(),owner.id());boolean already=state.pirateBands().stream().anyMatch(p->p.active()&&p.originSettlementId()==settlement.id());double risk=civ.banditPressure()*.55+(1-settlement.publicOrder())*.3+(1-e.getValue().security())*.2;
            if(!already&&risk>.5&&rng.chance(risk*.14)){PirateBand band=new PirateBand(state.nextId(),settlement.id(),day,e.getValue().position(),Math.min(40,Math.max(6,settlement.population()/180)));state.addPirateBand(band);SimPosition hideoutPos=pirateHideoutPosition(state,settlement,e.getValue().position(),band.id());PirateHideout hideout=new PirateHideout(state.nextId(),band.id(),settlement.id(),day,hideoutPos);hideout.adjustDefense(Math.min(.35,band.strength()/120.0));state.addPirateHideout(hideout);civ.adjustBanditPressure(-.08);state.history().add(new WorldEvent(day,"pirate_band_formed","pirates="+band.id()+", settlement="+settlement.id()+", strength="+band.strength()+", hideout="+hideout.id()));}
        }
        for(PirateBand band:state.pirateBands())if(band.active()){
            PortState originPort=portsBySettlement.get(band.originSettlementId());if(originPort!=null&&originPort.security()>.78&&band.position().distanceTo(originPort.position())<180&&rng.chance(.06)){band.loseStrength(1+Math.max(1,originPort.level()/2));band.adjustMorale(-.05);}
            if(resolveFleetPirateEngagement(state,band,rng))continue;
            TradeShipment target=state.shipments().stream().filter(s->waterTradeCandidate(state,s)).min(Comparator.comparingDouble(s->s.position().distanceTo(band.position()))).orElse(null);
            if(target==null){PirateHideout hideout=state.findPirateHideoutByBand(band.id()).orElse(null);if(hideout!=null&&band.position().distanceTo(hideout.position())>40)band.moveToward(hideout.position(),55);if(rng.chance(.0015)){band.disband();if(hideout!=null)hideout.destroy();}continue;}
            band.moveToward(target.position(),80+band.strength()*1.5);
            if(band.position().distanceTo(target.position())<90&&rng.chance(Math.min(.7,.08+band.strength()*.012))){double captured=target.value();band.addLoot(captured*.45);state.findPirateHideoutByBand(band.id()).ifPresent(h->h.addLoot(captured*.55));long shipmentId=target.id();state.recordPhysicalShipmentLoss(shipmentId,"piracy:band="+band.id());state.history().add(new WorldEvent(day,"piracy","pirates="+band.id()+", shipment="+shipmentId+", loot="+Math.round(captured)));band.adjustMorale(.03);}
            if(band.strength()<3||band.morale()<.12){
                band.disband();
                state.findPirateHideoutByBand(band.id()).ifPresent(PirateHideout::destroy);
                state.history().add(new WorldEvent(day,"pirate_band_disbanded","pirates="+band.id()+", reason=collapse"));
            }
        }
        // Hideouts cannot outlive their band: prune orphaned active records after attrition/disband.
        for(PirateHideout hideout:state.pirateHideouts()){
            if(!hideout.active())continue;
            PirateBand band=state.findPirateBand(hideout.bandId()).orElse(null);
            if(band==null||!band.active())hideout.destroy();
        }
    }

    public static boolean resolveFleetPirateEngagement(SimulationState state,PirateBand band,DeterministicRng rng){
        Fleet fleet=state.fleets().stream().filter(f->!f.destroyed()&&f.readiness()>.18&&f.mission()!=NavalMission.RETURN_TO_PORT).filter(f->f.position().distanceTo(band.position())<170).min(Comparator.comparingDouble(f->f.position().distanceTo(band.position()))).orElse(null);if(fleet==null)return false;
        double fleetPower=Math.max(.5,fleet.combatPower()),piratePower=Math.max(.5,band.strength()*(.55+.7*band.morale()));double total=fleetPower+piratePower;double pirateLoss=Mathx.clamp((fleetPower/total)*rng.between(.12,.42),.04,.75);double fleetLoss=Mathx.clamp((piratePower/total)*rng.between(.015,.16),.005,.28);
        int lostPirates=Math.max(1,(int)Math.round(band.strength()*pirateLoss));band.loseStrength(lostPirates);band.adjustMorale(-.08-pirateLoss*.25);fleet.loseFraction(fleetLoss);fleet.gainExperience(.008+.012*pirateLoss);
        state.history().add(new WorldEvent(state.clock().day(),"anti_piracy_action","fleet="+fleet.id()+", pirates="+band.id()+", pirateLoss="+lostPirates+", fleetShips="+fleet.totalShips()));
        if(!band.active()||band.strength()<3||band.morale()<.10){PirateHideout hideout=state.findPirateHideoutByBand(band.id()).orElse(null);double recovered=band.takeLoot(band.loot());if(hideout!=null&&hideout.discoveredByFactionId()==fleet.factionId()){recovered+=hideout.takeLoot(hideout.storedLoot());hideout.destroy();}double payout=recovered*.65;state.findFaction(fleet.factionId()).ifPresent(f->f.addTreasury(payout));band.disband();state.history().add(new WorldEvent(state.clock().day(),"pirate_band_destroyed","pirates="+band.id()+", fleet="+fleet.id()+", recovered="+Math.round(recovered*.65)));return true;}
        if(fleet.destroyed())return false;
        if(fleetPower>piratePower*1.25)band.moveToward(band.position().lerp(fleet.position(),-1),70);
        return false;
    }

    /** Coastal hideouts remain tied to the origin port but sit beyond ordinary harbor fabric. */
    public static SimPosition pirateHideoutPosition(SimulationState state,Settlement origin,SimPosition port,long bandId){
        double a=((CivilizationSupport.mix(state.seed()^bandId)&0xffff)/65535.0)*Math.PI*2.0;
        double r=400+((CivilizationSupport.mix(bandId^0x5DEECE66DL)>>>11)*0x1.0p-53)*600;
        SimPosition candidate=new SimPosition(port.x()+Math.cos(a)*r,port.z()+Math.sin(a)*r);
        if(candidate.distanceTo(origin.position())>=350)return candidate;
        return new SimPosition(port.x()+Math.cos(a)*Math.max(400,r),port.z()+Math.sin(a)*Math.max(400,r));
    }

    public static Faction nearestAntiPiracyFaction(SimulationState state,PirateHideout hideout){return state.factions().stream().filter(f->f.id()!=state.findSettlementOwner(hideout.originSettlementId()).map(Faction::id).orElse(-1L)).filter(f->antiPiracyPressure(state,f,hideout.position())>.2).min(Comparator.comparingDouble(f->f.settlements().stream().mapToDouble(s->s.position().distanceTo(hideout.position())).min().orElse(Double.MAX_VALUE))).orElse(null);}

    public static double antiPiracyPressure(SimulationState state,Faction faction,SimPosition position){double fleet=state.fleets().stream().filter(f->f.factionId()==faction.id()&&!f.destroyed()).filter(f->f.position().distanceTo(position)<650).mapToDouble(Fleet::readiness).max().orElse(0);double local=faction.settlements().stream().filter(s->s.position().distanceTo(position)<500).mapToDouble(s->s.publicOrder()*.65+s.infrastructure()*.35).max().orElse(0);return Mathx.clamp(fleet*.7+local*.3,0,1);}

    public static boolean waterTradeCandidate(SimulationState state,TradeShipment shipment){return state.ports().stream().anyMatch(p->p.operational()&&(p.position().distanceTo(shipment.origin())<500||p.position().distanceTo(shipment.destination())<500));}
}
