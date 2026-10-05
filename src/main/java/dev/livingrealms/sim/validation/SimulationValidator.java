package dev.livingrealms.sim.validation;

import dev.livingrealms.sim.aviation.AirWing;
import dev.livingrealms.sim.civilization.*;
import dev.livingrealms.sim.diplomacy.Treaty;
import dev.livingrealms.sim.diplomacy.WarState;
import dev.livingrealms.sim.ecology.PopulationGroup;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.law.*;
import dev.livingrealms.sim.industry.*;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.military.*;
import dev.livingrealms.sim.naval.*;
import dev.livingrealms.sim.player.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.SimulationState;
import java.util.*;

/** Cross-system invariant checker used by soak tests and debug/runtime diagnostics. */
public final class SimulationValidator {
    private SimulationValidator() {}

    public record Report(List<String> errors) {
        public Report { errors=List.copyOf(errors); }
        public boolean valid(){return errors.isEmpty();}
        public void throwIfInvalid(){if(!valid())throw new IllegalStateException("Invalid simulation state:\n - "+String.join("\n - ",errors));}
    }

    public static Report validate(SimulationState state){
        Objects.requireNonNull(state,"state");
        List<String> errors=new ArrayList<>();
        Set<Long> canonicalIds=new HashSet<>();
        long[] max={0};
        if(state.clock().gameTicks()<0)errors.add("negative simulation clock");
        if(state.peekNextId()<1)errors.add("nextId must be positive");

        for(var region:state.regions()){
            id(canonicalIds,max,region.id(),"region",errors);
            if(!finitePositive(region.areaKm2())) errors.add("region "+region.id()+" invalid area");
            if(!position(region.center().x(),region.center().z())) errors.add("region "+region.id()+" invalid center");
            if(!finiteNonNegative(region.plantBiomass())) errors.add("region "+region.id()+" invalid plant biomass");
            if(!state.biomes().containsKey(region.biome().id())) errors.add("region "+region.id()+" unknown biome "+region.biome().id());
            for(PopulationGroup p:region.populations()){
                id(canonicalIds,max,p.id(),"population",errors);
                if(!state.species().containsKey(p.speciesId())) errors.add("population "+p.id()+" unknown species "+p.speciesId());
                if(!state.biomes().containsKey(p.biomeId())) errors.add("population "+p.id()+" unknown biome "+p.biomeId());
                if(!position(p.position().x(),p.position().z())) errors.add("population "+p.id()+" invalid position");
                if(!finiteNonNegative(p.population())) errors.add("population "+p.id()+" invalid count");
                if(!unit(p.health())||!unit(p.hunger())||!unit(p.thirst())||!unit(p.diseasePressure())||!unit(p.injuryPressure())||!unit(p.reproductiveFraction())||!unit(p.migrationPressure())) errors.add("population "+p.id()+" invalid normalized state");
                if(!finiteNonNegative(p.averageAgeDays())) errors.add("population "+p.id()+" invalid age");
            }
        }

        Set<Long> factionIds=new HashSet<>(),settlementIds=new HashSet<>(),armyIds=new HashSet<>();
        for(Faction f:state.factions()){
            id(canonicalIds,max,f.id(),"faction",errors); if(!factionIds.add(f.id())) errors.add("duplicate faction "+f.id());
            if(!finiteNonNegative(f.treasury())||!finiteNonNegative(f.technology())) errors.add("faction "+f.id()+" invalid treasury/technology");
            var government=f.government();var ruler=government.ruler();
            if(ruler.id()<=0||ruler.ageYears()<0||!unit(ruler.health())||!unit(ruler.diplomacy())||!unit(ruler.stewardship())||!unit(ruler.martial())||!unit(ruler.legitimacy()))errors.add("faction "+f.id()+" invalid ruler state");
            if(!unit(government.stability())||!unit(government.legitimacy())||!unit(government.corruption())||!bounded(government.taxRate(),0,.65)||!unit(government.lawEnforcement())||government.yearsInPower()<0)errors.add("faction "+f.id()+" invalid government state");
            for(ResourceType resource:ResourceType.values()) if(!finiteNonNegative(f.stockpile().get(resource))) errors.add("faction "+f.id()+" invalid stockpile "+resource);
            for(Settlement s:f.settlements()){
                id(canonicalIds,max,s.id(),"settlement",errors); if(!settlementIds.add(s.id())) errors.add("settlement owned more than once "+s.id());
                if(!position(s.position().x(),s.position().z()))errors.add("settlement "+s.id()+" invalid position");
                if(s.population()<0||s.housing()<0) errors.add("settlement "+s.id()+" negative people/housing");
                if(!finiteNonNegative(s.infrastructure())||!unit(s.prosperity())||!unit(s.unrest())||!unit(s.foodSecurity())||!unit(s.publicOrder())||!unit(s.employment())) errors.add("settlement "+s.id()+" invalid society state");
                if(!finitePositive(s.barnCapacity())||!finitePositive(s.granaryCapacity())) errors.add("settlement "+s.id()+" invalid storage capacity");
                for(ResourceType resource:ResourceType.values()) if(!finiteNonNegative(s.stockpile().get(resource))) errors.add("settlement "+s.id()+" invalid local stockpile "+resource);
            }
            for(Army a:f.armies()){
                id(canonicalIds,max,a.id(),"army",errors); if(!armyIds.add(a.id())) errors.add("army owned more than once "+a.id());
                if(a.factionId()!=f.id()) errors.add("army "+a.id()+" owner mismatch");
                if(!position(a.position().x(),a.position().z()))errors.add("army "+a.id()+" invalid position");
                if(a.infantry()<0||a.cavalry()<0||a.artillery()<0||a.armor()<0||a.aircraft()<0||!unit(a.morale())||!unit(a.supply())) errors.add("army "+a.id()+" invalid strength/state");
            }
        }
        for(Faction f:state.factions()) for(var relationEntry:f.relations().entrySet()){
            long other=relationEntry.getKey();if(!factionIds.contains(other))errors.add("faction "+f.id()+" relation to missing faction "+other);
            if(!bounded(relationEntry.getValue().opinion(),-100,100))errors.add("faction "+f.id()+" invalid relation opinion to "+other);
        }

        for(TradeShipment s:state.shipments()){
            id(canonicalIds,max,s.id(),"shipment",errors);
            if(!factionIds.contains(s.sellerFactionId())||!factionIds.contains(s.buyerFactionId())||s.sellerFactionId()==s.buyerFactionId()) errors.add("shipment "+s.id()+" invalid factions");
            if(!position(s.origin().x(),s.origin().z())||!position(s.destination().x(),s.destination().z()))errors.add("shipment "+s.id()+" invalid route position");
            if(!finitePositive(s.amount())||!finiteNonNegative(s.value())||!unit(s.progress())) errors.add("shipment "+s.id()+" invalid cargo/progress");
        }
        for(Treaty t:state.treaties()){
            id(canonicalIds,max,t.id(),"treaty",errors); if(!pair(factionIds,t.factionA(),t.factionB())) errors.add("treaty "+t.id()+" invalid factions"); if(t.endDay()<t.startDay()) errors.add("treaty "+t.id()+" invalid dates");
        }
        for(WarState w:state.wars()){
            id(canonicalIds,max,w.id(),"war",errors); if(!pair(factionIds,w.attackerFactionId(),w.defenderFactionId())) errors.add("war "+w.id()+" invalid factions"); if(w.targetSettlementId()>0&&!settlementIds.contains(w.targetSettlementId())) errors.add("war "+w.id()+" missing target settlement");if(!bounded(w.attackerScore(),-100,100)||!unit(w.attackerExhaustion())||!unit(w.defenderExhaustion()))errors.add("war "+w.id()+" invalid metrics");
        }
        for(TransportRoute r:state.routes()){
            id(canonicalIds,max,r.id(),"route",errors); if(!factionIds.contains(r.ownerFactionId())||!settlementIds.contains(r.fromSettlementId())||!settlementIds.contains(r.toSettlementId())||r.fromSettlementId()==r.toSettlementId()) errors.add("route "+r.id()+" invalid endpoints"); if(!finitePositive(r.distanceBlocks())||!unit(r.quality())||!unit(r.security())||!finiteNonNegative(r.capacityPerDay())) errors.add("route "+r.id()+" invalid metrics");
        }
        for(MilitaryObjective o:state.objectives()){
            id(canonicalIds,max,o.id(),"objective",errors); if(!armyIds.contains(o.armyId())||!factionIds.contains(o.ownerFactionId())) errors.add("objective "+o.id()+" invalid owner/army"); if(o.targetFactionId()>0&&!factionIds.contains(o.targetFactionId())) errors.add("objective "+o.id()+" missing target faction"); if(o.targetSettlementId()>0&&!settlementIds.contains(o.targetSettlementId())) errors.add("objective "+o.id()+" missing target settlement");if(!position(o.targetPosition().x(),o.targetPosition().z())||o.createdDay()<0||o.priority()<0)errors.add("objective "+o.id()+" invalid target/priority");
        }
        for(SiegeState s:state.sieges()){
            id(canonicalIds,max,s.id(),"siege",errors); if(!pair(factionIds,s.attackerFactionId(),s.defenderFactionId())||!settlementIds.contains(s.settlementId())) errors.add("siege "+s.id()+" invalid references"); if(!unit(s.progress())||!unit(s.blockade())||!unit(s.breach())||!unit(s.defenderCountermeasures())||s.rams()<0||s.ladders()<0||s.artilleryPieces()<0) errors.add("siege "+s.id()+" invalid metrics");
        }
        for(AirWing w:state.airWings()){
            id(canonicalIds,max,w.id(),"airwing",errors); if(!factionIds.contains(w.factionId())) errors.add("airwing "+w.id()+" missing faction"); if(!position(w.position().x(),w.position().z())||!position(w.basePosition().x(),w.basePosition().z())||!position(w.targetPosition().x(),w.targetPosition().z()))errors.add("airwing "+w.id()+" invalid position");if(w.aircraft()<0||!unit(w.fuel())||!unit(w.readiness())||!unit(w.experience())) errors.add("airwing "+w.id()+" invalid state");
        }
        Set<Long> portIds=new HashSet<>();
        for(PortState p:state.ports()){
            id(canonicalIds,max,p.id(),"port",errors);if(!portIds.add(p.id()))errors.add("duplicate port "+p.id());
            if(!factionIds.contains(p.factionId())||!settlementIds.contains(p.settlementId()))errors.add("port "+p.id()+" invalid owner/settlement");
            if(!position(p.position().x(),p.position().z()))errors.add("port "+p.id()+" invalid position");
            if(p.level()<1||p.level()>5||!unit(p.condition())||!unit(p.security()))errors.add("port "+p.id()+" invalid metrics");
        }
        for(Fleet f:state.fleets()){
            id(canonicalIds,max,f.id(),"fleet",errors);if(!factionIds.contains(f.factionId())||!portIds.contains(f.homePortId()))errors.add("fleet "+f.id()+" invalid faction/home port");
            if(!position(f.position().x(),f.position().z())||!position(f.targetPosition().x(),f.targetPosition().z()))errors.add("fleet "+f.id()+" invalid position");
            if(f.totalShips()<0||!unit(f.fuel())||!unit(f.readiness())||!unit(f.experience())||!unit(f.supply())||f.embarkedPersonnel()<0||f.embarkedPersonnel()>f.troopCapacity())errors.add("fleet "+f.id()+" invalid state");
            for(ShipClass c:ShipClass.values())if(f.count(c)<0)errors.add("fleet "+f.id()+" negative ships "+c);
        }
        Set<String> industryKeys=new HashSet<>();
        for(IndustrialSite site:state.industrialSites()){
            id(canonicalIds,max,site.id(),"industrial site",errors);
            if(!factionIds.contains(site.factionId())||!settlementIds.contains(site.settlementId()))errors.add("industrial site "+site.id()+" invalid owner/settlement");
            String key=site.settlementId()+":"+site.kind();if(!industryKeys.add(key))errors.add("duplicate industrial site slot "+key);
            if(site.level()<1||site.level()>5||!unit(site.condition())||site.starvedDays()<0||site.downtimeDays()<0||site.lastCycles()<0||!unit(site.lastUtilization()))errors.add("industrial site "+site.id()+" invalid metrics");
        }
        Set<String> livingCitizenSlots=new HashSet<>();Set<Long> citizenIds=new HashSet<>();
        for(SocialCitizen c:state.socialCitizens()){
            id(canonicalIds,max,c.id(),"social citizen",errors);citizenIds.add(c.id());
            if(!factionIds.contains(c.factionId())||!settlementIds.contains(c.settlementId()))errors.add("social citizen "+c.id()+" invalid faction/settlement");
            if(c.projectionSlot()<0||c.skinVariant()<0||c.skinVariant()>=48||c.name().isBlank())errors.add("social citizen "+c.id()+" invalid identity");
            if(c.birthDay()>state.clock().day())errors.add("social citizen "+c.id()+" born in future");
            if(!unit(c.health())||!finiteNonNegative(c.money()))errors.add("social citizen "+c.id()+" invalid health/money");
            CitizenNeeds n=c.needs();if(!unit(n.hunger())||!unit(n.safety())||!unit(n.social())||!unit(n.status())||!unit(n.comfort()))errors.add("social citizen "+c.id()+" invalid needs");
            CitizenPersonality p=c.personality();if(!unit(p.aggression())||!unit(p.tradeAffinity())||!unit(p.caution())||!unit(p.greed())||!unit(p.loyalty())||!unit(p.treachery()))errors.add("social citizen "+c.id()+" invalid personality");
            if(c.memories().size()>SocialCitizen.MAX_MEMORIES)errors.add("social citizen "+c.id()+" too many memories");
            for(CitizenMemory m:c.memories()){if(m.day()<0||m.day()>state.clock().day()||!position(m.position().x(),m.position().z())||!unit(m.importance())||!unit(m.confidence()))errors.add("social citizen "+c.id()+" invalid memory");}
            if(c.relationships().size()>SocialCitizen.MAX_RELATIONSHIPS)errors.add("social citizen "+c.id()+" too many relationships");
            for(CitizenRelationship r:c.relationships().values())if(r.targetKey().isBlank()||!unit(r.friendship())||!unit(r.hostility())||!unit(r.romance())||!unit(r.rivalry())||!unit(r.trust()))errors.add("social citizen "+c.id()+" invalid relationship");
            if(c.alive()&&!livingCitizenSlots.add(c.settlementId()+":"+c.projectionSlot()))errors.add("duplicate living social citizen slot "+c.settlementId()+":"+c.projectionSlot());
        }
        if(state.settlementCivilizations().size()>state.factions().stream().mapToInt(f->f.settlements().size()).sum()+64)errors.add("too many settlement civilization profiles");
        for(SettlementCivilizationState c:state.settlementCivilizations().values()){
            if(!settlementIds.contains(c.settlementId())||!factionIds.contains(c.heritageFactionId()))errors.add("settlement civilization "+c.settlementId()+" invalid references");
            if(!unit(c.sanitation())||!unit(c.diseasePressure())||!unit(c.education())||!unit(c.waterSecurity())||!unit(c.refugeePressure())||!unit(c.banditPressure())||!unit(c.culturalCohesion())||!unit(c.assimilation())||!unit(c.resourcePressure())||!unit(c.quarantineStrength()))errors.add("settlement civilization "+c.settlementId()+" invalid metrics");
            if(c.knowledge().size()!=KnowledgeDomain.values().length||c.knowledge().values().stream().anyMatch(v->!unit(v)))errors.add("settlement civilization "+c.settlementId()+" invalid knowledge");
            if(c.lastEpidemicDay()>state.clock().day()||c.lastMigrationDay()>state.clock().day())errors.add("settlement civilization "+c.settlementId()+" future lifecycle marker");
        }
        for(FactionCivilizationState c:state.factionCivilizations().values()){
            if(!factionIds.contains(c.factionId()))errors.add("faction civilization "+c.factionId()+" missing faction");
            if(c.cultureName().isBlank()||c.faithName().isBlank()||c.dialectName().isBlank()||!unit(c.culturalInfluence())||!unit(c.religiousInfluence())||!unit(c.education())||!unit(c.propaganda())||!unit(c.intelligence())||!bounded(c.tributeRate(),0,.35)||!unit(c.mercantileTradition())||!unit(c.martialTradition())||!unit(c.agrarianTradition())||!unit(c.artisticTradition())||!unit(c.religiousTolerance())||!unit(c.openness())||!unit(c.lawSeverity())||!unit(c.dueProcess())||!unit(c.refugeeAcceptance()))errors.add("faction civilization "+c.factionId()+" invalid state");
            if(c.tributaryToFactionId()>0&&(!factionIds.contains(c.tributaryToFactionId())||c.tributaryToFactionId()==c.factionId()))errors.add("faction civilization "+c.factionId()+" invalid overlord");
            if(c.spyNetworks().size()>FactionCivilizationState.MAX_SPY_NETWORKS)errors.add("faction civilization "+c.factionId()+" too many spy networks");
            for(var e:c.spyNetworks().entrySet())if(!factionIds.contains(e.getKey())||e.getKey()==c.factionId()||!unit(e.getValue()))errors.add("faction civilization "+c.factionId()+" invalid spy network");
        }
        if(state.households().size()>SimulationState.MAX_HOUSEHOLDS)errors.add("too many households");
        Set<Long> householdIds=new HashSet<>(),dependentIds=new HashSet<>(),allPeopleIds=new HashSet<>(citizenIds);
        for(HouseholdState h:state.households()){
            id(canonicalIds,max,h.id(),"household",errors);householdIds.add(h.id());
            if(!factionIds.contains(h.factionId())||!settlementIds.contains(h.settlementId())||h.foundedDay()>state.clock().day()||!finiteNonNegative(h.sharedWealth()))errors.add("household "+h.id()+" invalid state/references");
            if(h.memberIds().size()>HouseholdState.MAX_NAMED_MEMBERS||h.children().size()>HouseholdState.MAX_CHILDREN)errors.add("household "+h.id()+" exceeds bounds");
            for(long memberId:h.memberIds()){
                SocialCitizen member=state.findSocialCitizen(memberId).orElse(null);if(member==null){errors.add("household "+h.id()+" missing member "+memberId);continue;}
                if(member.householdId()!=h.id())errors.add("household "+h.id()+" member "+memberId+" backlink mismatch");
                if(h.active()&&member.alive()&&(member.factionId()!=h.factionId()||member.settlementId()!=h.settlementId()))errors.add("household "+h.id()+" active member location mismatch "+memberId);
            }
            for(DependentChild child:h.children()){
                if(!h.active())continue; // deactivated households must not retain children after merge/adoption
                id(canonicalIds,max,child.id(),"dependent child",errors);dependentIds.add(child.id());allPeopleIds.add(child.id());
                if(child.birthDay()>state.clock().day())errors.add("dependent child "+child.id()+" born in future");
                if(citizenIds.contains(child.id()))errors.add("dependent child "+child.id()+" already materialized as citizen");
                for(long parentId:child.biologicalParents())if(!citizenIds.contains(parentId))errors.add("dependent child "+child.id()+" missing biological parent "+parentId);
                for(long parentId:child.adoptiveParents())if(!citizenIds.contains(parentId))errors.add("dependent child "+child.id()+" missing adoptive parent "+parentId);
            }
        }
        for(SocialCitizen c:state.socialCitizens())if(c.householdId()>0&&!householdIds.contains(c.householdId()))errors.add("social citizen "+c.id()+" references missing household "+c.householdId());

        if(state.epidemics().size()>SimulationState.MAX_EPIDEMICS)errors.add("too many epidemics");
        for(EpidemicRecord e:state.epidemics()){
            id(canonicalIds,max,e.id(),"epidemic",errors);if(!settlementIds.contains(e.settlementId())||e.startDay()>state.clock().day()||e.lastUpdateDay()<e.startDay()||e.lastUpdateDay()>state.clock().day()||e.diseaseKey().isBlank()||!unit(e.severity())||!unit(e.infectedFraction())||e.cumulativeDeaths()<0)errors.add("epidemic "+e.id()+" invalid state");
        }
        if(state.migrationGroups().size()>SimulationState.MAX_MIGRATIONS)errors.add("too many migration groups");
        for(MigrationGroup g:state.migrationGroups()){
            id(canonicalIds,max,g.id(),"migration group",errors);if(!factionIds.contains(g.originFactionId())||!settlementIds.contains(g.sourceSettlementId())||g.targetSettlementId()>0&&!settlementIds.contains(g.targetSettlementId())||g.campSettlementId()>0&&!settlementIds.contains(g.campSettlementId())||g.createdDay()>state.clock().day()||g.people()<0||!unit(g.progress())||!unit(g.food())||!unit(g.health()))errors.add("migration group "+g.id()+" invalid state/references");
            if(g.householdIds().size()>8)errors.add("migration group "+g.id()+" too many households");for(long householdId:g.householdIds())if(!householdIds.contains(householdId))errors.add("migration group "+g.id()+" missing household "+householdId);
        }
        if(state.justiceCases().size()>SimulationState.MAX_JUSTICE_CASES)errors.add("too many justice cases");
        for(JusticeCase c:state.justiceCases()){
            id(canonicalIds,max,c.id(),"justice case",errors);if(!factionIds.contains(c.factionId())||!settlementIds.contains(c.settlementId())||c.openedDay()>state.clock().day()||c.accusedKey().isBlank()||!finiteNonNegative(c.fine())||c.releaseDay()<0)errors.add("justice case "+c.id()+" invalid state/references");
            long accused=citizenKey(c.accusedKey());if(accused>0&&!citizenIds.contains(accused))errors.add("justice case "+c.id()+" missing accused citizen "+accused);if(c.status()==JusticeStatus.SERVING&&c.sentence()!=SentenceType.IMPRISONMENT)errors.add("justice case "+c.id()+" serving non-imprisonment sentence");
        }
        if(state.hiddenCaches().size()>SimulationState.MAX_HIDDEN_CACHES)errors.add("too many hidden caches");
        for(HiddenCache c:state.hiddenCaches()){
            id(canonicalIds,max,c.id(),"hidden cache",errors);if(!factionIds.contains(c.factionId())||!settlementIds.contains(c.settlementId())||c.createdDay()>state.clock().day()||!position(c.position().x(),c.position().z())||c.goods().values().stream().anyMatch(v->!finiteNonNegative(v)))errors.add("hidden cache "+c.id()+" invalid state/references");if(c.discoveredByFactionId()>0&&(!factionIds.contains(c.discoveredByFactionId())||c.discoveredByFactionId()==c.factionId()))errors.add("hidden cache "+c.id()+" invalid discovery faction");
        }
        if(state.pirateBands().size()>SimulationState.MAX_PIRATE_BANDS)errors.add("too many pirate bands");
        for(PirateBand p:state.pirateBands()){
            id(canonicalIds,max,p.id(),"pirate band",errors);if(!settlementIds.contains(p.originSettlementId())||p.createdDay()>state.clock().day()||!position(p.position().x(),p.position().z())||p.strength()<0||!unit(p.morale())||!finiteNonNegative(p.loot()))errors.add("pirate band "+p.id()+" invalid state");
        }
        if(state.pirateHideouts().size()>SimulationState.MAX_PIRATE_HIDEOUTS)errors.add("too many pirate hideouts");
        for(PirateHideout h:state.pirateHideouts()){
            id(canonicalIds,max,h.id(),"pirate hideout",errors);
            if(h.active()){
                if(state.findPirateBand(h.bandId()).filter(PirateBand::active).isEmpty()
                        ||!settlementIds.contains(h.originSettlementId())
                        ||h.createdDay()>state.clock().day()
                        ||!position(h.position().x(),h.position().z())
                        ||!unit(h.defense())
                        ||!finiteNonNegative(h.storedLoot())
                        ||h.discoveredByFactionId()>0&&!factionIds.contains(h.discoveredByFactionId())){
                    errors.add("pirate hideout "+h.id()+" invalid state/references");
                }
            } else if(!position(h.position().x(),h.position().z())||!finiteNonNegative(h.storedLoot())||h.createdDay()>state.clock().day()){
                errors.add("pirate hideout "+h.id()+" invalid destroyed record");
            }
        }
        if(state.diplomaticMarriages().size()>SimulationState.MAX_DIPLOMATIC_MARRIAGES)errors.add("too many diplomatic marriages");
        for(DiplomaticMarriage m:state.diplomaticMarriages()){
            id(canonicalIds,max,m.id(),"diplomatic marriage",errors);if(m.day()>state.clock().day()||!pair(factionIds,m.factionA(),m.factionB())||!citizenIds.contains(m.citizenA())||!citizenIds.contains(m.citizenB())||m.citizenA()==m.citizenB())errors.add("diplomatic marriage "+m.id()+" invalid state/references");
        }
        if(state.dynasties().size()>factionIds.size())errors.add("too many dynasties");
        for(DynastyState d:state.dynasties().values()){
            if(!factionIds.contains(d.factionId())||d.foundedDay()>state.clock().day()||d.houseName().isBlank()||!unit(d.prestige())||d.generation()<1||d.lastSuccessionDay()>state.clock().day()||d.crisisSinceDay()>state.clock().day())errors.add("dynasty "+d.factionId()+" invalid state");
            if(d.rulerCitizenId()>0&&!citizenIds.contains(d.rulerCitizenId()))errors.add("dynasty "+d.factionId()+" missing ruler citizen "+d.rulerCitizenId());
            if(d.heirCitizenId()>0&&!allPeopleIds.contains(d.heirCitizenId()))errors.add("dynasty "+d.factionId()+" missing heir person "+d.heirCitizenId());
            if(d.regentCitizenId()>0&&!citizenIds.contains(d.regentCitizenId()))errors.add("dynasty "+d.factionId()+" missing regent citizen "+d.regentCitizenId());
            if(d.regentCitizenId()>0&&d.regentCitizenId()==d.rulerCitizenId())errors.add("dynasty "+d.factionId()+" ruler cannot be regent");
        }
        if(state.civicEvents().size()>SimulationState.MAX_CIVIC_EVENTS)errors.add("too many civic events");
        for(CivicEvent e:state.civicEvents()){
            id(canonicalIds,max,e.id(),"civic event",errors);if(!factionIds.contains(e.factionId())||!settlementIds.contains(e.settlementId())||e.startDay()>state.clock().day()||e.endDay()<e.startDay()||e.title().isBlank()||!unit(e.attendance())||!unit(e.intensity()))errors.add("civic event "+e.id()+" invalid state/references");
        }
        if(state.intelligenceOperations().size()>SimulationState.MAX_INTELLIGENCE_OPERATIONS)errors.add("too many intelligence operations");
        for(IntelligenceOperation o:state.intelligenceOperations()){
            id(canonicalIds,max,o.id(),"intelligence operation",errors);if(!pair(factionIds,o.sourceFactionId(),o.targetFactionId())||o.startDay()>state.clock().day()||o.agentCitizenId()>0&&!citizenIds.contains(o.agentCitizenId())||!unit(o.progress())||!unit(o.secrecy())||!unit(o.quality()))errors.add("intelligence operation "+o.id()+" invalid state/references");
        }
        if(state.propagandaCampaigns().size()>SimulationState.MAX_PROPAGANDA_CAMPAIGNS)errors.add("too many propaganda campaigns");
        for(PropagandaCampaign c:state.propagandaCampaigns()){
            id(canonicalIds,max,c.id(),"propaganda campaign",errors);if(!factionIds.contains(c.factionId())||c.settlementId()>0&&!settlementIds.contains(c.settlementId())||c.startDay()>state.clock().day()||c.endDay()<c.startDay()||!unit(c.intensity())||!unit(c.reach()))errors.add("propaganda campaign "+c.id()+" invalid state/references");
        }
        if(state.ruinSites().size()>SimulationState.MAX_RUIN_SITES)errors.add("too many ruin sites");
        for(RuinSite r:state.ruinSites()){
            id(canonicalIds,max,r.id(),"ruin site",errors);if(!factionIds.contains(r.originalFactionId())||r.createdDay()>state.clock().day()||!position(r.position().x(),r.position().z())||r.originalName().isBlank()||!unit(r.preservation()))errors.add("ruin site "+r.id()+" invalid state/references");
        }
        if(state.assistanceTasks().size()>SimulationState.MAX_ASSISTANCE_TASKS)errors.add("too many assistance tasks");
        Set<String> activeTaskSlots=new HashSet<>();for(AssistanceTask t:state.assistanceTasks()){
            id(canonicalIds,max,t.id(),"assistance task",errors);if(!factionIds.contains(t.factionId())||!settlementIds.contains(t.settlementId())||t.createdDay()<0||t.createdDay()>state.clock().day()||t.expiresDay()<t.createdDay()||t.causeKey().isBlank()||!unit(t.initialPressure())||t.initialPressure()<=0||!unit(t.remainingPressure()))errors.add("assistance task "+t.id()+" invalid state/references");if(t.active()&&!activeTaskSlots.add(t.settlementId()+":"+t.type()))errors.add("duplicate active assistance task slot "+t.settlementId()+":"+t.type());
        }
        if(state.resourceClaims().size()>SimulationState.MAX_RESOURCE_CLAIMS)errors.add("too many resource claims");
        Set<String> claimSlots=new HashSet<>();for(ResourceClaim c:state.resourceClaims()){
            id(canonicalIds,max,c.id(),"resource claim",errors);if(!factionIds.contains(c.factionId())||!settlementIds.contains(c.settlementId()))errors.add("resource claim "+c.id()+" invalid owner/settlement");if(!position(c.position().x(),c.position().z())||!unit(c.strength()))errors.add("resource claim "+c.id()+" invalid state");if(c.contestedByFactionId()>0&&(!factionIds.contains(c.contestedByFactionId())||c.contestedByFactionId()==c.factionId()))errors.add("resource claim "+c.id()+" invalid contender");if(c.active()&&!claimSlots.add(c.settlementId()+":"+c.type()))errors.add("duplicate active resource claim slot "+c.settlementId()+":"+c.type());
        }
        if(state.raids().size()>SimulationState.MAX_RAIDS)errors.add("too many raids");for(RaidParty r:state.raids()){
            id(canonicalIds,max,r.id(),"raid",errors);if(!settlementIds.contains(r.originSettlementId())||!settlementIds.contains(r.targetSettlementId())||r.originSettlementId()==r.targetSettlementId())errors.add("raid "+r.id()+" invalid settlements");if(!r.bandit()&&!factionIds.contains(r.attackerFactionId()))errors.add("raid "+r.id()+" invalid attacker");if(r.bandit()&&r.attackerFactionId()!=0)errors.add("bandit raid "+r.id()+" has faction attacker");if(r.manpower()<0||!unit(r.morale())||!unit(r.progress())||r.createdDay()<0||r.createdDay()>state.clock().day())errors.add("raid "+r.id()+" invalid state");
        }
        if(state.legends().size()>SimulationState.MAX_LEGENDS)errors.add("too many legends");for(LegendRecord l:state.legends()){
            id(canonicalIds,max,l.id(),"legend",errors);if(!factionIds.contains(l.factionId())||l.settlementId()>0&&!settlementIds.contains(l.settlementId())||l.day()<0||l.day()>state.clock().day()||l.subjectKey().isBlank()||l.title().isBlank()||l.description().isBlank()||!unit(l.renown()))errors.add("legend "+l.id()+" invalid state");
        }

        for(PlayerStanding ps:state.playerStandings().values()){
            for(var e:ps.reputations().entrySet()){if(!factionIds.contains(e.getKey()))errors.add("player "+ps.actorKey()+" reputation references missing faction "+e.getKey());if(!bounded(e.getValue(),-100,100))errors.add("player "+ps.actorKey()+" invalid reputation");}
            if(ps.isMember()){if(!factionIds.contains(ps.memberFactionId()))errors.add("player "+ps.actorKey()+" member of missing faction "+ps.memberFactionId());if(ps.rank()==FactionRank.OUTSIDER)errors.add("player "+ps.actorKey()+" member with OUTSIDER rank");if(ps.joinedDay()<0||!finiteNonNegative(ps.servicePoints()))errors.add("player "+ps.actorKey()+" invalid membership state");}
            else if(ps.memberFactionId()!=0||ps.rank()!=FactionRank.OUTSIDER)errors.add("player "+ps.actorKey()+" inconsistent non-member state");
            if(ps.expulsions()<0)errors.add("player "+ps.actorKey()+" negative expulsions");
        }
        for(BountyContract b:state.bounties()){
            id(canonicalIds,max,b.id(),"bounty",errors); if(!factionIds.contains(b.issuerFactionId())) errors.add("bounty "+b.id()+" missing issuer"); if(!finitePositive(b.reward())) errors.add("bounty "+b.id()+" invalid reward");
        }
        for(CustodyRecord c:state.custody()){
            id(canonicalIds,max,c.id(),"custody",errors); if(!factionIds.contains(c.factionId())) errors.add("custody "+c.id()+" missing faction"); if(c.releaseDay()<c.startDay()||!finiteNonNegative(c.bountyAtArrest())) errors.add("custody "+c.id()+" invalid state");
        }
        for(WantedProfile p:state.crimeLedger().profiles().values()){
            if(!finiteNonNegative(p.globalInfamy()))errors.add("wanted profile "+p.actorKey()+" invalid global infamy");
            for(JurisdictionWanted w:p.jurisdictions().values()){
                if(!factionIds.contains(w.factionId())) errors.add("wanted profile "+p.actorKey()+" missing jurisdiction "+w.factionId());
                if(!finiteNonNegative(w.bounty())||!finiteNonNegative(w.notoriety())||!unit100(w.heat())||w.lastCrimeDay()<-1||w.witnessedCrimes()<0||w.violentCrimes()<0||w.captures()<0) errors.add("wanted profile "+p.actorKey()+" invalid values");
            }
        }
        for(CrimeIncident incident:state.crimeLedger().incidents()){
            id(canonicalIds,max,incident.id(),"crime incident",errors);
            if(!factionIds.contains(incident.jurisdictionFactionId())||incident.day()<0||!finiteNonNegative(incident.stolenOrDamageValue())||incident.witnessCount()<0||!position(incident.position().x(),incident.position().z()))errors.add("crime incident "+incident.id()+" invalid state");
        }
        if(state.underworldContracts().size()>SimulationState.MAX_UNDERWORLD_CONTRACTS)errors.add("too many underworld contracts");
        for(dev.livingrealms.sim.underworld.UnderworldContract c:state.underworldContracts()){
            id(canonicalIds,max,c.id(),"underworld contract",errors);
            if(!factionIds.contains(c.jurisdictionFactionId())||c.createdDay()<0||c.expiresDay()<c.createdDay()||!finiteNonNegative(c.minValue())||!finiteNonNegative(c.reward()))
                errors.add("underworld contract "+c.id()+" invalid state");
            if(c.status()==dev.livingrealms.sim.underworld.UnderworldContract.Status.ACCEPTED&&(c.acceptorActorKey()==null||c.acceptorActorKey().isBlank()))
                errors.add("underworld contract "+c.id()+" accepted without actor");
        }
        for(dev.livingrealms.sim.underworld.StolenGoodsEntry e:state.stolenGoodsLedger().entries()){
            id(canonicalIds,max,e.id(),"stolen goods",errors);
            if(e.actorKey().isBlank()||e.goodKey().isBlank()||!finiteNonNegative(e.value())||e.quantity()<=0||e.acquiredDay()<0)
                errors.add("stolen goods "+e.id()+" invalid state");
        }
        if(state.peekNextId()<=max[0]) errors.add("nextId "+state.peekNextId()+" is not greater than max canonical id "+max[0]);
        return new Report(errors);
    }

    private static void id(Set<Long> ids,long[] max,long id,String kind,List<String> errors){if(id<=0)errors.add(kind+" has nonpositive id "+id);if(!ids.add(id))errors.add("duplicate canonical id "+id+" at "+kind);max[0]=Math.max(max[0],id);}
    private static long citizenKey(String key){if(key==null||!key.startsWith("citizen:"))return 0;try{return Long.parseLong(key.substring(8));}catch(NumberFormatException ignored){return 0;}}
    private static boolean pair(Set<Long> factions,long a,long b){return a!=b&&factions.contains(a)&&factions.contains(b);}
    private static boolean finitePositive(double v){return Double.isFinite(v)&&v>0;}
    private static boolean finiteNonNegative(double v){return Double.isFinite(v)&&v>=0;}
    private static boolean unit(double v){return Double.isFinite(v)&&v>=0&&v<=1;}
    private static boolean unit100(double v){return Double.isFinite(v)&&v>=0&&v<=100;}
    private static boolean bounded(double v,double min,double max){return Double.isFinite(v)&&v>=min&&v<=max;}
    private static boolean position(double x,double z){return Double.isFinite(x)&&Double.isFinite(z)&&Math.abs(x)<=dev.livingrealms.sim.world.SimPosition.MAX_ABS_COORDINATE&&Math.abs(z)<=dev.livingrealms.sim.world.SimPosition.MAX_ABS_COORDINATE;}
}
