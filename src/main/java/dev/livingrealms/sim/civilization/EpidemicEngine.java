package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.util.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Epidemic spawn/update, named victims, and trade-route disease spread.
 */
public final class EpidemicEngine {
    private EpidemicEngine() {}

    public static void simulateEpidemics(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();Map<Long,EpidemicRecord> active=new HashMap<>();for(EpidemicRecord e:state.epidemics())if(e.active())active.put(e.settlementId(),e);
        CivilizationCalendar.Season season=CivilizationCalendar.season(day);
        for(Faction faction:state.factions())for(Settlement settlement:faction.settlements()){
            SettlementCivilizationState civ=state.ensureSettlementCivilization(settlement.id(),faction.id());EpidemicRecord epidemic=active.get(settlement.id());
            if(epidemic==null&&civ.diseasePressure()>.54&&day-civ.lastEpidemicDay()>60){
                String key=civ.waterSecurity()<.42?"waterborne_fever":civ.sanitation()<.42?"crowding_flux":season==CivilizationCalendar.Season.WINTER?"winter_lung":"trade_pox";
                DiseaseProfile profile=DiseaseProfile.of(key);double severity=Mathx.clamp(civ.diseasePressure()*(.45+.45*profile.mortality()),0,1);double infected=Mathx.clamp((civ.diseasePressure()-.30)*profile.transmission(),.02,.75);
                epidemic=new EpidemicRecord(state.nextId(),settlement.id(),day,key,severity,infected);state.addEpidemic(epidemic);active.put(settlement.id(),epidemic);civ.markEpidemic(day);state.history().add(new WorldEvent(day,"epidemic_started","epidemic="+epidemic.id()+", settlement="+settlement.id()+", disease="+key));
            }
            if(epidemic==null)continue;
            DiseaseProfile profile=DiseaseProfile.of(epidemic.diseaseKey());boolean clinic=CivilizationSupport.has(settlement,"clinic:"),well=CivilizationSupport.has(settlement,"well:"),irrigation=CivilizationSupport.has(settlement,"irrigation:"),aqueduct=CivilizationSupport.has(settlement,"aqueduct:");double medical=civ.knowledge(KnowledgeDomain.MEDICINE);double healerShare=CivilizationSupport.specialistShare(state,settlement.id(),CitizenRole.HEALER);double containment=(clinic?.15:0)+(well?.04*profile.waterSensitivity():0)+(irrigation?.025*profile.waterSensitivity():0)+(aqueduct?.08*profile.waterSensitivity():0)+medical*.20*profile.medicineSensitivity()+healerShare*.16+civ.quarantineStrength()*.30;
            double crowd=Mathx.clamp((settlement.population()-settlement.housing())/(double)Math.max(20,settlement.population()),0,1);double transmissionDrive=profile.transmission()*(.32+civ.diseasePressure()*.42+crowd*profile.crowdingSensitivity()*.32+(1-civ.sanitation())*.22+(1-civ.waterSecurity())*profile.waterSensitivity()*.28);
            double targetInfected=Mathx.clamp(transmissionDrive-containment*.48,0,.92);double targetSeverity=Mathx.clamp(civ.diseasePressure()*.55+profile.mortality()*.25-containment*.52,0,1);
            int deaths=0;if(epidemic.infectedFraction()>.025){double mortality=epidemic.infectedFraction()*epidemic.severity()*profile.mortality()*(.0008+.0014*(1-medical));deaths=Math.min(Math.max(0,settlement.population()-1),(int)Math.floor(settlement.population()*mortality));if(deaths>0){settlement.addPopulation(-deaths);settlement.adjustUnrest(Math.min(.045,deaths/(double)Math.max(50,settlement.population())*.5));markNamedEpidemicVictims(state,settlement,epidemic,deaths,medical,rng);}}
            epidemic.update(day,targetSeverity,targetInfected,deaths);civ.adjustDisease((targetInfected-civ.diseasePressure())*.025);
            if(epidemic.severity()>.50&&faction.government().lawEnforcement()>.42&&civ.education()>.32)civ.setQuarantineStrength(Math.min(.9,civ.quarantineStrength()+.018+.012*medical));else civ.setQuarantineStrength(Math.max(0,civ.quarantineStrength()-.014));
            if(civ.quarantineStrength()>.25){settlement.adjustProsperity(-.0007*civ.quarantineStrength());settlement.setEmployment(settlement.employment()-.0004*civ.quarantineStrength());}
            if(!epidemic.active()){civ.setQuarantineStrength(0);state.history().add(new WorldEvent(day,"epidemic_ended","epidemic="+epidemic.id()+", settlement="+settlement.id()+", disease="+epidemic.diseaseKey()+", deaths="+epidemic.cumulativeDeaths()));}
            else if(deaths>0&&rng.chance(.12))state.history().add(new WorldEvent(day,"epidemic_deaths","epidemic="+epidemic.id()+", settlement="+settlement.id()+", deaths="+deaths));
        }
    }

    public static void markNamedEpidemicVictims(SimulationState state,Settlement settlement,EpidemicRecord epidemic,int aggregateDeaths,double medicine,DeterministicRng rng){
        if(aggregateDeaths<=0)return;long day=state.clock().day();List<SocialCitizen> named=state.socialCitizens().stream().filter(SocialCitizen::alive).filter(c->c.settlementId()==settlement.id()).sorted(Comparator.comparingLong(SocialCitizen::id)).toList();if(named.isEmpty())return;
        int affected=Math.min(named.size(),Math.max(1,(int)Math.ceil(named.size()*epidemic.infectedFraction())));int namedDeaths=Math.min(affected,Math.max(0,(int)Math.round(aggregateDeaths*(named.size()/(double)Math.max(1,settlement.population()+aggregateDeaths)))));
        ArrayList<SocialCitizen> candidates=new ArrayList<>(named);candidates.sort(Comparator.comparingDouble((SocialCitizen c)->victimRisk(c,day,epidemic,medicine)).reversed().thenComparingLong(SocialCitizen::id));
        for(int i=0;i<affected&&i<candidates.size();i++){SocialCitizen c=candidates.get(i);double harm=epidemic.severity()*(.008+.018*(1-medicine));c.adjustHealth(-harm);c.remember(new CitizenMemory(day,MemoryType.LOCAL_EVENT,"epidemic:"+epidemic.id(),"settlement:"+settlement.id(),"Illness spread through "+settlement.name()+" ("+epidemic.diseaseKey()+")",settlement.position(),.72,.95));}
        for(int i=0;i<namedDeaths&&i<candidates.size();i++){SocialCitizen victim=candidates.get(i);if(!victim.alive())continue;victim.markDead();rememberFamilyDeath(state,victim,settlement,"disease:"+epidemic.diseaseKey());state.history().add(new WorldEvent(day,"named_epidemic_death","citizen="+victim.id()+", epidemic="+epidemic.id()+", settlement="+settlement.id()));}
        if(namedDeaths==0&&epidemic.severity()>.72&&rng.chance(.03)){SocialCitizen victim=candidates.getFirst();victim.adjustHealth(-.05);}
    }

    public static double victimRisk(SocialCitizen c,long day,EpidemicRecord epidemic,double medicine){double age=Mathx.clamp((c.ageYears(day)-45)/45.0,0,1);return (1-c.health())*.45+age*.28+epidemic.severity()*.22+(1-medicine)*.05;}

    public static void rememberFamilyDeath(SimulationState state,SocialCitizen victim,Settlement settlement,String cause){String key="citizen:"+victim.id();for(SocialCitizen other:state.socialCitizens())if(other.alive()){CitizenRelationship rel=other.relationships().get(key);if(rel==null||rel.familyBond()==FamilyBond.NONE)continue;other.remember(new CitizenMemory(state.clock().day(),MemoryType.FAMILY_EVENT,key,cause,victim.name()+" died during "+cause,settlement.position(),.95,1));other.relationship(key).adjust(0,.04,0,0,-.08);}}

    public static void spreadDiseaseAlongTrade(SimulationState state,DeterministicRng rng){
        Map<Long,EpidemicRecord> active=new HashMap<>();for(EpidemicRecord e:state.epidemics())if(e.active())active.put(e.settlementId(),e);if(active.isEmpty())return;
        for(TradeShipment shipment:state.shipments()){
            Settlement origin=CivilizationSupport.nearestSettlement(state,shipment.origin(),260);Settlement destination=CivilizationSupport.nearestSettlement(state,shipment.destination(),260);if(origin==null||destination==null||origin.id()==destination.id())continue;EpidemicRecord source=active.get(origin.id());if(source==null||active.containsKey(destination.id()))continue;Faction destOwner=state.findSettlementOwner(destination.id()).orElse(null);if(destOwner==null)continue;SettlementCivilizationState dc=state.ensureSettlementCivilization(destination.id(),destOwner.id());double risk=source.infectedFraction()*(.15+.25*(1-dc.sanitation()))*(1-dc.quarantineStrength());if(rng.chance(risk))dc.adjustDisease(.08+.10*source.severity());
        }
    }
}
