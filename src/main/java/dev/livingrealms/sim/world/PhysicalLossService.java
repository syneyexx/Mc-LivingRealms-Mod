package dev.livingrealms.sim.world;

import dev.livingrealms.sim.aviation.AirWing;
import dev.livingrealms.sim.civilization.CitizenJourney;
import dev.livingrealms.sim.civilization.MigrationGroup;
import dev.livingrealms.sim.civilization.PirateBand;
import dev.livingrealms.sim.ecology.PopulationGroup;
import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.industry.IndustrialSite;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.naval.Fleet;
import dev.livingrealms.sim.naval.ShipClass;
import dev.livingrealms.sim.social.SocialCitizen;
import java.util.Objects;

/**
 * Maps real Minecraft-side losses back onto canonical simulation state.
 * LOD dematerialization must never call these paths; callers use the SimulationState façade.
 */
final class PhysicalLossService {
    private PhysicalLossService() {}

    static boolean recordPhysicalShipmentLoss(SimulationState state,long id,String cause){
        TradeShipment shipment=state.removeShipment(id);
        if(shipment==null)return false;
        state.history().add(new WorldEvent(state.clock().day(),"trade_caravan_lost",
                "shipment="+id+", resource="+shipment.resource()+", amount="+shipment.amount()
                        +", cause="+Objects.requireNonNullElse(cause,"unknown")));
        return true;
    }

    static boolean recordPhysicalAnimalDeath(SimulationState state,long groupId,double count,String cause){
        if(count<=0||!Double.isFinite(count))throw new IllegalArgumentException("count");
        PopulationGroup group=state.findPopulationGroup(groupId).orElse(null);
        if(group==null||group.extinct())return false;
        double before=group.population();
        group.addPopulation(-Math.min(count,before));
        state.history().add(new WorldEvent(state.clock().day(),"wildlife_death",
                "group="+groupId+", species="+group.speciesId()+", count="+count
                        +", cause="+Objects.requireNonNullElse(cause,"unknown")));
        return true;
    }

    static boolean recordIndustrialDamage(SimulationState state,long siteId,double damage,int downtime,String cause){
        IndustrialSite site=state.findIndustrialSite(siteId).orElse(null);
        if(site==null)return false;
        site.damage(damage,downtime);
        state.history().add(new WorldEvent(state.clock().day(),"industry_damaged",
                "site="+siteId+", kind="+site.kind()+", damage="+damage
                        +", cause="+Objects.requireNonNullElse(cause,"unknown")));
        return true;
    }

    static boolean recordPhysicalCitizenDeath(SimulationState state,long settlementId,long socialCitizenId,String cause){
        Settlement settlement=state.findSettlement(settlementId).orElse(null);
        if(settlement==null||settlement.population()<=0)return false;
        SocialCitizen citizen=socialCitizenId>0?state.findSocialCitizen(socialCitizenId).orElse(null):null;
        if(citizen!=null&&!citizen.alive())return false;
        if(citizen!=null)citizen.markDead();
        settlement.addPopulation(-1);
        settlement.adjustUnrest(.0025);
        settlement.setPublicOrder(settlement.publicOrder()-.001);
        String safeCause=Objects.requireNonNullElse(cause,"unknown");
        state.history().add(new WorldEvent(state.clock().day(),"citizen_death",
                "settlement="+settlementId
                        +(citizen==null?"":", citizen="+citizen.id()+", name="+citizen.name())
                        +", cause="+safeCause));
        dev.livingrealms.api.LivingRealmsApi.publish(
                new dev.livingrealms.api.event.CitizenDeath(
                        state.clock().day(),settlementId,citizen==null?0:citizen.id(),safeCause));
        return true;
    }

    static boolean recordPhysicalArmyLoss(SimulationState state,long armyId,int representedPersonnel,String cause){
        Army army=state.findArmy(armyId).orElse(null);
        if(army==null||army.destroyed())return false;
        army.recordRepresentativeLoss(representedPersonnel);
        state.history().add(new WorldEvent(state.clock().day(),"military_loss",
                "army="+armyId+", represented="+representedPersonnel
                        +", cause="+Objects.requireNonNullElse(cause,"unknown")));
        return true;
    }

    static boolean recordPhysicalAircraftLoss(SimulationState state,long wingId,int count,String cause){
        AirWing wing=state.airWings().stream().filter(w->w.id()==wingId).findFirst().orElse(null);
        if(wing==null||wing.destroyed())return false;
        wing.loseAircraft(count);
        state.history().add(new WorldEvent(state.clock().day(),"aircraft_loss",
                "wing="+wingId+", count="+count+", cause="+Objects.requireNonNullElse(cause,"unknown")));
        state.removeDestroyedAirWings();
        return true;
    }

    static boolean recordPhysicalShipLoss(SimulationState state,long fleetId,ShipClass shipClass,int count,String cause){
        if(count<=0)throw new IllegalArgumentException("count");
        Fleet fleet=state.findFleet(fleetId).orElse(null);
        if(fleet==null||fleet.destroyed()||fleet.count(shipClass)<=0)return false;
        int before=fleet.count(shipClass);
        fleet.loseShips(shipClass,count);
        int lost=before-fleet.count(shipClass);
        state.history().add(new WorldEvent(state.clock().day(),"ship_loss",
                "fleet="+fleetId+", class="+shipClass+", count="+lost
                        +", cause="+Objects.requireNonNullElse(cause,"unknown")));
        state.removeDestroyedFleets();
        return lost>0;
    }

    static boolean recordPhysicalMigrationLoss(SimulationState state,long groupId,int representedPeople,String cause){
        if(representedPeople<=0)throw new IllegalArgumentException("representedPeople");
        MigrationGroup group=state.findMigrationGroup(groupId).orElse(null);
        if(group==null||!group.active()||group.people()<=0)return false;
        int before=group.people();
        group.losePeople(Math.min(representedPeople,before));
        int lost=before-group.people();
        state.history().add(new WorldEvent(state.clock().day(),"migration_group_loss",
                "group="+groupId+", people="+lost+", cause="+Objects.requireNonNullElse(cause,"unknown")));
        return lost>0;
    }

    static boolean recordPhysicalPirateLoss(SimulationState state,long bandId,int representedPirates,String cause){
        if(representedPirates<=0)throw new IllegalArgumentException("representedPirates");
        PirateBand band=state.findPirateBand(bandId).orElse(null);
        if(band==null||!band.active())return false;
        int before=band.strength();
        band.loseStrength(Math.min(representedPirates,before));
        int lost=before-band.strength();
        state.history().add(new WorldEvent(state.clock().day(),"pirate_loss",
                "band="+bandId+", pirates="+lost+", cause="+Objects.requireNonNullElse(cause,"unknown")));
        return lost>0;
    }

    static boolean recordPhysicalJourneyDeath(SimulationState state,long journeyId,String cause){
        CitizenJourney journey=state.findCitizenJourney(journeyId).orElse(null);
        if(journey==null||!journey.active())return false;
        journey.abort();
        state.history().add(new WorldEvent(state.clock().day(),"citizen_journey_killed",
                "journey="+journeyId+", purpose="+journey.purpose().name()
                        +", cause="+Objects.requireNonNullElse(cause,"unknown")));
        return true;
    }
}
