package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.civilian.*;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.government.*;
import dev.livingrealms.sim.law.*;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.naval.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.util.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Deep civilisation lifecycle layered on the existing canonical faction/settlement/economy/law systems.
 * This class owns no duplicate population/economy authority: it creates persistent social records and
 * writes consequences back through Settlement, Faction, CrimeLedger, GovernmentState and WorldHistory.
 */
public final class CivilizationLifecycleEngine {
    private static final int MAX_NAMED_CITIZENS=8_000;

    public void simulateDay(SimulationState state,DeterministicRng rng){
        Objects.requireNonNull(state);Objects.requireNonNull(rng);
        ensureCulturalPoliciesAndDynasties(state);
        ensureHouseholds(state);
        simulateEpidemics(state,rng);
        advanceMigrationGroups(state,rng);
        simulatePiracy(state,rng);
        simulateCivicEvents(state,rng);
        simulateIntelligenceOperations(state,rng);
        simulatePropagandaCampaigns(state,rng);
        simulateRuins(state,rng);
        simulateAssistanceTasks(state);
        long day=state.clock().day();
        if(day%7==0){diffuseKnowledge(state);spreadDiseaseAlongTrade(state,rng);advanceJusticeCases(state);}
        if(day%30==0){simulateHouseholdLifecycle(state,rng);trainApprentices(state);payWages(state);spawnNpcCrime(state,rng);simulateHiddenCaches(state);simulatePoliticalMarriages(state,rng);refreshDynasties(state);}
        if(day%90==0)evolveCultureAndLaw(state);
    }

    /** Called by CivilizationEngine instead of instantaneous source->target population teleportation. */
    public void considerMigration(SimulationState state){
        long day=state.clock().day();
        long activeMigrations=state.migrationGroups().stream().filter(MigrationGroup::active).count();
        if(activeMigrations>=96)return;
        long camps=state.factions().stream().flatMap(f->f.settlements().stream()).filter(s->s.name().startsWith("Refugee Camp ")||s.name().startsWith("Haven ")).count();
        if(camps>=48)return; // hard world-wide camp budget for soak performance
        List<Settlement> all=state.factions().stream().flatMap(f->f.settlements().stream()).filter(s->!s.name().startsWith("Refugee Camp ")).toList();
        int spawned=0;
        for(Settlement source:all){
            if(spawned>=8)return; // at most a handful of new groups per weekly pass
            Faction sourceOwner=state.findSettlementOwner(source.id()).orElse(null);if(sourceOwner==null||source.population()<24)continue;
            SettlementCivilizationState sc=state.ensureSettlementCivilization(source.id(),sourceOwner.id());
            if(day-sc.lastMigrationDay()<21||state.migrationGroups().stream().anyMatch(g->g.active()&&g.sourceSettlementId()==source.id()))continue;
            MigrationReason reason=migrationReason(source,sourceOwner,sc);
            if(reason==null)continue;
            Settlement target=all.stream().filter(t->t.id()!=source.id()).filter(t->acceptableMigrationTarget(state,sourceOwner,t)).max(Comparator.comparingDouble(t->migrationScore(state,t)-source.position().distanceTo(t.position())/7000.0)).orElse(null);
            double sourceScore=migrationScore(state,source);double targetScore=target==null?0:migrationScore(state,target);
            if(target!=null&&targetScore<=sourceScore+.08)target=null;
            int people=Math.min(48,Math.max(2,(int)Math.round(source.population()*(.003+.012*Math.max(sc.refugeePressure(),1-source.foodSecurity())))));
            people=Math.min(people,Math.max(0,source.population()-12));if(people<=0)continue;
            MigrationGroup group=new MigrationGroup(state.nextId(),sourceOwner.id(),source.id(),target==null?0:target.id(),day,people,reason);
            attachHouseholds(state,group,source.id(),people);
            source.addPopulation(-people);sc.markMigration(day);sc.adjustRefugeePressure(-.05);
            state.addMigrationGroup(group);spawned++;
            state.history().add(new WorldEvent(day,"migration_departed","group="+group.id()+", from="+source.id()+", to="+(target==null?0:target.id())+", people="+people+", reason="+reason));
        }
    }

    private static void ensureCulturalPoliciesAndDynasties(SimulationState state){
        for(Faction faction:state.factions()){
            boolean newProfile=state.findFactionCivilization(faction.id()).isEmpty();
            FactionCivilizationState civ=state.ensureFactionCivilization(faction.id());
            if(newProfile){
                long h=mix(state.seed()^faction.id()*0x9E3779B97F4A7C15L);
                civ.setCultureTraits(unit(h,0),unit(h,9),unit(h,18),unit(h,27),unit(h,36),unit(h,45));
                double severity=.28+.48*unit(h,6),due=.28+.58*unit(h,22),refugees=.25+.65*unit(h,39);
                if(faction.government().type()==GovernmentType.THEOCRACY){severity=Math.max(severity,.5);due=Math.max(.35,due-.08);} civ.setLawPolicy(severity,due,refugees);
            }
            if(hereditaryGovernment(faction.government())){
                DynastyState dynasty=state.dynasties().get(faction.id());
                if(dynasty==null){dynasty=state.ensureDynasty(faction.id(),state.clock().day(),houseName(faction));state.history().add(new WorldEvent(state.clock().day(),"dynasty_founded","faction="+faction.id()+", house="+dynasty.houseName()));}
                if(state.playerRuler(faction.id()).isEmpty())ensureRulerCitizen(state,faction,dynasty);
            }
        }
    }

    private static boolean hereditaryGovernment(GovernmentState government){return government.successionLaw()==SuccessionLaw.HEREDITARY||government.type()==GovernmentType.FEUDAL_MONARCHY||government.type()==GovernmentType.ABSOLUTE_MONARCHY||government.type()==GovernmentType.CONSTITUTIONAL_MONARCHY;}

    private static void ensureRulerCitizen(SimulationState state,Faction faction,DynastyState dynasty){
        SocialCitizen ruler=dynasty.rulerCitizenId()>0?state.findSocialCitizen(dynasty.rulerCitizenId()).filter(SocialCitizen::alive).orElse(null):null;
        if(ruler==null)ruler=state.socialCitizens().stream().filter(SocialCitizen::alive).filter(c->c.factionId()==faction.id()&&c.name().equals(faction.rulerName())).findFirst().orElse(null);
        if(ruler==null&&state.socialCitizens().size()<MAX_NAMED_CITIZENS&&!faction.settlements().isEmpty()){
            Settlement capital=faction.settlements().stream().max(Comparator.comparingInt(Settlement::population)).orElseThrow();long id=state.nextId();int slot=virtualSlot(state,capital.id(),id);CitizenRole role=faction.government().type()==GovernmentType.THEOCRACY?CitizenRole.PRIEST:CitizenRole.OFFICIAL;long birth=state.clock().day()-faction.government().ruler().ageYears()*365L-120;DeterministicRng r=new DeterministicRng(state.seed()^id);CitizenPersonality personality=new CitizenPersonality(r.between(.2,.8),r.between(.2,.8),r.between(.2,.8),r.between(.15,.8),r.between(.35,.95),r.between(.02,.5));int ageYears=Math.max(18,faction.government().ruler().ageYears());var appearance=dev.livingrealms.sim.civilian.AppearanceProfile.forCitizen(state.seed(),id,role,ageYears,faction.id());ruler=new SocialCitizen(id,faction.id(),capital.id(),slot,faction.rulerName(),appearance.textureIndex(),role,birth,personality);ruler.restoreAppearance(appearance.pack());ruler.addMoney(Math.max(40,faction.treasury()*.02));state.addSocialCitizen(ruler);state.history().add(new WorldEvent(state.clock().day(),"ruler_personified","faction="+faction.id()+", citizen="+id+", name="+ruler.name()));
        }
        if(ruler!=null)dynasty.setRulerCitizenId(ruler.id());
    }

    private static void ensureHouseholds(SimulationState state){
        Map<Long,HouseholdState> byId=new HashMap<>();for(HouseholdState h:state.households())if(h.active())byId.put(h.id(),h);
        for(SocialCitizen citizen:state.socialCitizens())if(citizen.alive()){
            if(citizen.householdId()>0&&byId.containsKey(citizen.householdId()))continue;
            HouseholdState partnerHouse=null;
            for(var e:citizen.relationships().entrySet())if(e.getValue().familyBond()==FamilyBond.PARTNER){long otherId=parseCitizenKey(e.getKey());SocialCitizen other=state.findSocialCitizen(otherId).filter(SocialCitizen::alive).orElse(null);if(other!=null&&other.householdId()>0){partnerHouse=byId.get(other.householdId());if(partnerHouse!=null)break;}}
            if(partnerHouse==null){
                partnerHouse=new HouseholdState(state.nextId(),citizen.factionId(),citizen.settlementId(),state.clock().day());
                Faction owner=state.findFaction(citizen.factionId()).orElse(null);
                Settlement homeSettlement=state.findSettlement(citizen.settlementId()).orElse(null);
                String homeKey=owner!=null&&homeSettlement!=null
                        ?dev.livingrealms.sim.social.HouseholdHomeBinder.assignHomeKey(owner,homeSettlement,partnerHouse.id())
                        :"house:"+Math.floorMod(citizen.projectionSlot(),72);
                partnerHouse.setHomeKey(homeKey);
                partnerHouse.adjustWealth(citizen.money()*.25);
                state.addHousehold(partnerHouse);
                byId.put(partnerHouse.id(),partnerHouse);
            }
            partnerHouse.addMember(citizen.id());citizen.setHouseholdId(partnerHouse.id());
        }
        // Merge households after partnership formation; oldest household survives to preserve a stable identity.
        for(SocialCitizen citizen:state.socialCitizens())if(citizen.alive()&&citizen.householdId()>0)for(var e:citizen.relationships().entrySet())if(e.getValue().familyBond()==FamilyBond.PARTNER){SocialCitizen partner=state.findSocialCitizen(parseCitizenKey(e.getKey())).filter(SocialCitizen::alive).orElse(null);if(partner==null||partner.householdId()<=0||partner.householdId()==citizen.householdId())continue;HouseholdState a=byId.get(citizen.householdId()),b=byId.get(partner.householdId());if(a==null||b==null||a.factionId()!=b.factionId()||a.settlementId()!=b.settlementId())continue;HouseholdState keep=a.id()<b.id()?a:b,drop=keep==a?b:a;for(long member:new ArrayList<>(drop.memberIds()))if(keep.addMember(member)){state.findSocialCitizen(member).ifPresent(c->c.setHouseholdId(keep.id()));drop.removeMember(member);}for(DependentChild child:drop.children())keep.addChild(child);keep.adjustWealth(drop.sharedWealth());drop.deactivate();}
        for(HouseholdState household:state.households())if(household.active()&&household.memberIds().stream().noneMatch(id->state.findSocialCitizen(id).map(SocialCitizen::alive).orElse(false))&&household.children().isEmpty())household.deactivate();
    }

    private static void simulateAssistanceTasks(SimulationState state){
        long day=state.clock().day();
        for(Faction faction:state.factions())for(Settlement settlement:faction.settlements()){
            SettlementCivilizationState civ=state.ensureSettlementCivilization(settlement.id(),faction.id());
            EnumMap<AssistanceTaskType,Double> pressure=new EnumMap<>(AssistanceTaskType.class);
            pressure.put(AssistanceTaskType.FOOD_RELIEF,Mathx.clamp((.48-settlement.foodSecurity())/.48,0,1));
            pressure.put(AssistanceTaskType.MEDICAL_AID,Mathx.clamp(Math.max(civ.diseasePressure(),1-civ.sanitation())-.34,0,1));
            pressure.put(AssistanceTaskType.SECURITY_SUPPORT,Mathx.clamp(Math.max(civ.banditPressure(),1-settlement.publicOrder())-.30,0,1));
            pressure.put(AssistanceTaskType.REFUGEE_SUPPORT,Mathx.clamp(civ.refugeePressure()-.28,0,1));
            pressure.put(AssistanceTaskType.WATER_SUPPLY,Mathx.clamp(.50-civ.waterSecurity(),0,1)*2);
            double housingRatio=settlement.housing()<=0?1:Mathx.clamp(settlement.population()/(double)settlement.housing(),0,2);
            pressure.put(AssistanceTaskType.HOUSING_SUPPLIES,Mathx.clamp(housingRatio-.84,0,1));
            double insecureTrade=state.routes().stream().filter(TransportRoute::operational).filter(r->r.fromSettlementId()==settlement.id()||r.toSettlementId()==settlement.id()).mapToDouble(r->Math.max(0,.55-r.security())).max().orElse(0);
            pressure.put(AssistanceTaskType.TRADE_ESCORT,Mathx.clamp(insecureTrade*1.7,0,1));
            pressure.put(AssistanceTaskType.INFRASTRUCTURE_REPAIR,AssistanceContributionEngine.infrastructurePressure(state,settlement));
            for(var entry:pressure.entrySet()){
                AssistanceTaskType type=entry.getKey();double p=entry.getValue();Optional<AssistanceTask> active=state.activeAssistanceTask(settlement.id(),type);
                if(active.isPresent()){active.get().updatePressure(day,p);if(!active.get().active())state.history().add(new WorldEvent(day,"assistance_task_"+active.get().status().name().toLowerCase(Locale.ROOT),"task="+active.get().id()+", settlement="+settlement.id()+", type="+type));continue;}
                if(p<.32||day%7!=Math.floorMod(settlement.id()+type.ordinal()*3,7))continue;
                AssistanceTask task=new AssistanceTask(state.nextId(),faction.id(),settlement.id(),day,day+45,type,"pressure:"+type.name().toLowerCase(Locale.ROOT),p);state.addAssistanceTask(task);
                state.history().add(new WorldEvent(day,"assistance_task_opened","task="+task.id()+", settlement="+settlement.id()+", type="+type+", pressure="+String.format(Locale.ROOT,"%.3f",p)));
            }
        }
    }

    private static void simulateHouseholdLifecycle(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();
        for(HouseholdState household:state.households())if(household.active()){
            Settlement settlement=state.findSettlement(household.settlementId()).orElse(null);Faction faction=state.findFaction(household.factionId()).orElse(null);if(settlement==null||faction==null){household.deactivate();continue;}
            List<SocialCitizen> adults=household.memberIds().stream().map(state::findSocialCitizen).flatMap(Optional::stream).filter(SocialCitizen::alive).filter(c->c.ageYears(day)>=18).toList();
            boolean partnered=adults.stream().anyMatch(a->hasHouseholdPartner(a,adults));
            SettlementCivilizationState civ=state.ensureSettlementCivilization(settlement.id(),faction.id());
            double freeHousing=Mathx.clamp((settlement.housing()-settlement.population())/(double)Math.max(20,settlement.population()),0,1);
            double fertility=Mathx.clamp(.012+.028*settlement.foodSecurity()+.012*freeHousing+.008*civ.culturalCohesion()-.025*civ.diseasePressure(),0,.08);
            if(partnered&&household.children().size()<HouseholdState.MAX_CHILDREN&&settlement.housing()>settlement.population()&&rng.chance(fertility)){
                long[] parents=parentPair(adults);DependentChild child=new DependentChild(state.nextId(),day,parents[0],parents[1]);household.addChild(child);
                state.history().add(new WorldEvent(day,"household_birth","child="+child.id()+", household="+household.id()+", settlement="+settlement.id()+", parents="+parents[0]+"/"+parents[1]));
            }
            for(DependentChild child:household.takeAdultChildren(day))if(state.socialCitizens().size()<MAX_NAMED_CITIZENS&&household.memberIds().size()<HouseholdState.MAX_NAMED_MEMBERS)materializeAdultChild(state,household,faction,settlement,child,rng);
            if(adults.isEmpty()&&!household.children().isEmpty())adoptOrphans(state,household,settlement);
            // Living household budget: wages already top up sharedWealth; spend on food/comfort, then needs follow.
            double mouths=Math.max(1,household.representedPeople());
            double scarcity=1.0-settlement.foodSecurity();
            double foodBill=mouths*(.08+.22*scarcity);
            double spent=Math.min(household.sharedWealth(),foodBill);
            if(spent>0){
                household.adjustWealth(-spent);
                settlement.stockpile().take(ResourceType.FOOD,spent*.55); // market purchase from local granary
            }
            double fed=spent/Math.max(.01,foodBill);
            for(SocialCitizen adult:adults){
                adult.needs().approach(.35+.55*fed,settlement.publicOrder(),.55+.2*settlement.prosperity(),
                        Mathx.clamp(.3+household.sharedWealth()/120.0,0,1),.4+.35*fed+(household.homeKey().isBlank()?0:.1),.18);
                if(fed<.45)adult.addMoney(-Math.min(adult.money(),.4)); // personal purse covers shortfall
            }
            household.adjustWealth((settlement.prosperity()-.45)*.35-mouths*scarcity*.05);
        }
    }

    private static void materializeAdultChild(SimulationState state,HouseholdState household,Faction faction,Settlement settlement,DependentChild dependent,DeterministicRng rng){
        long id=dependent.id();CitizenRole role=chooseProfession(settlement,id);SocialCitizen child=FamilyMaterializer.materialize(state,household,dependent,role);
        state.history().add(new WorldEvent(state.clock().day(),"coming_of_age","citizen="+child.id()+", household="+household.id()+", settlement="+settlement.id()+", profession="+role));
    }

    private static void adoptOrphans(SimulationState state,HouseholdState orphaned,Settlement settlement){
        HouseholdState target=state.households().stream().filter(HouseholdState::active).filter(h->h.id()!=orphaned.id()&&h.settlementId()==settlement.id()&&h.memberIds().stream().anyMatch(id->state.findSocialCitizen(id).filter(SocialCitizen::alive).map(c->c.ageYears(state.clock().day())>=21).orElse(false))).filter(h->h.children().size()<6).max(Comparator.comparingDouble(HouseholdState::resilience)).orElse(null);
        if(target==null){
            if(has(settlement,"orphanage:")){if(!orphaned.homeKey().startsWith("orphanage:")){orphaned.setHomeKey("orphanage:0");state.history().add(new WorldEvent(state.clock().day(),"orphans_sheltered","household="+orphaned.id()+", settlement="+settlement.id()+", children="+orphaned.children().size()));}return;}
            orphaned.setHomeKey("community_care:"+settlement.id());return;
        }
        List<DependentChild> children=new ArrayList<>(orphaned.children());List<Long> guardians=target.memberIds().stream().filter(id->state.findSocialCitizen(id).filter(SocialCitizen::alive).map(c->c.ageYears(state.clock().day())>=21).orElse(false)).limit(2).toList();for(DependentChild child:children){child.adoptBy(guardians);target.addChild(child);}double transfer=orphaned.sharedWealth()*.5;target.adjustWealth(transfer);orphaned.restore(orphaned.memberIds(),List.of(),Math.max(0,orphaned.sharedWealth()-transfer),orphaned.homeKey(),false);state.history().add(new WorldEvent(state.clock().day(),"orphans_adopted","fromHousehold="+orphaned.id()+", toHousehold="+target.id()+", settlement="+settlement.id()+", children="+children.size()));
    }

    private static long[] parentPair(List<SocialCitizen> adults){
        for(SocialCitizen a:adults)for(var e:a.relationships().entrySet())if(e.getValue().familyBond()==FamilyBond.PARTNER){long bid=parseCitizenKey(e.getKey());if(bid>0&&adults.stream().anyMatch(c->c.id()==bid))return new long[]{a.id(),bid};}
        return adults.size()>=2?new long[]{adults.get(0).id(),adults.get(1).id()}:adults.isEmpty()?new long[]{0,0}:new long[]{adults.get(0).id(),0};
    }

    private static void simulateEpidemics(SimulationState state,DeterministicRng rng){
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
            DiseaseProfile profile=DiseaseProfile.of(epidemic.diseaseKey());boolean clinic=has(settlement,"clinic:"),well=has(settlement,"well:"),irrigation=has(settlement,"irrigation:"),aqueduct=has(settlement,"aqueduct:");double medical=civ.knowledge(KnowledgeDomain.MEDICINE);double healerShare=specialistShare(state,settlement.id(),CitizenRole.HEALER);double containment=(clinic?.15:0)+(well?.04*profile.waterSensitivity():0)+(irrigation?.025*profile.waterSensitivity():0)+(aqueduct?.08*profile.waterSensitivity():0)+medical*.20*profile.medicineSensitivity()+healerShare*.16+civ.quarantineStrength()*.30;
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

    private static void markNamedEpidemicVictims(SimulationState state,Settlement settlement,EpidemicRecord epidemic,int aggregateDeaths,double medicine,DeterministicRng rng){
        if(aggregateDeaths<=0)return;long day=state.clock().day();List<SocialCitizen> named=state.socialCitizens().stream().filter(SocialCitizen::alive).filter(c->c.settlementId()==settlement.id()).sorted(Comparator.comparingLong(SocialCitizen::id)).toList();if(named.isEmpty())return;
        int affected=Math.min(named.size(),Math.max(1,(int)Math.ceil(named.size()*epidemic.infectedFraction())));int namedDeaths=Math.min(affected,Math.max(0,(int)Math.round(aggregateDeaths*(named.size()/(double)Math.max(1,settlement.population()+aggregateDeaths)))));
        ArrayList<SocialCitizen> candidates=new ArrayList<>(named);candidates.sort(Comparator.comparingDouble((SocialCitizen c)->victimRisk(c,day,epidemic,medicine)).reversed().thenComparingLong(SocialCitizen::id));
        for(int i=0;i<affected&&i<candidates.size();i++){SocialCitizen c=candidates.get(i);double harm=epidemic.severity()*(.008+.018*(1-medicine));c.adjustHealth(-harm);c.remember(new CitizenMemory(day,MemoryType.LOCAL_EVENT,"epidemic:"+epidemic.id(),"settlement:"+settlement.id(),"Illness spread through "+settlement.name()+" ("+epidemic.diseaseKey()+")",settlement.position(),.72,.95));}
        for(int i=0;i<namedDeaths&&i<candidates.size();i++){SocialCitizen victim=candidates.get(i);if(!victim.alive())continue;victim.markDead();rememberFamilyDeath(state,victim,settlement,"disease:"+epidemic.diseaseKey());state.history().add(new WorldEvent(day,"named_epidemic_death","citizen="+victim.id()+", epidemic="+epidemic.id()+", settlement="+settlement.id()));}
        if(namedDeaths==0&&epidemic.severity()>.72&&rng.chance(.03)){SocialCitizen victim=candidates.getFirst();victim.adjustHealth(-.05);}
    }

    private static double victimRisk(SocialCitizen c,long day,EpidemicRecord epidemic,double medicine){double age=Mathx.clamp((c.ageYears(day)-45)/45.0,0,1);return (1-c.health())*.45+age*.28+epidemic.severity()*.22+(1-medicine)*.05;}

    private static void rememberFamilyDeath(SimulationState state,SocialCitizen victim,Settlement settlement,String cause){String key="citizen:"+victim.id();for(SocialCitizen other:state.socialCitizens())if(other.alive()){CitizenRelationship rel=other.relationships().get(key);if(rel==null||rel.familyBond()==FamilyBond.NONE)continue;other.remember(new CitizenMemory(state.clock().day(),MemoryType.FAMILY_EVENT,key,cause,victim.name()+" died during "+cause,settlement.position(),.95,1));other.relationship(key).adjust(0,.04,0,0,-.08);}}

    private static void spreadDiseaseAlongTrade(SimulationState state,DeterministicRng rng){
        Map<Long,EpidemicRecord> active=new HashMap<>();for(EpidemicRecord e:state.epidemics())if(e.active())active.put(e.settlementId(),e);if(active.isEmpty())return;
        for(TradeShipment shipment:state.shipments()){
            Settlement origin=nearestSettlement(state,shipment.origin(),260);Settlement destination=nearestSettlement(state,shipment.destination(),260);if(origin==null||destination==null||origin.id()==destination.id())continue;EpidemicRecord source=active.get(origin.id());if(source==null||active.containsKey(destination.id()))continue;Faction destOwner=state.findSettlementOwner(destination.id()).orElse(null);if(destOwner==null)continue;SettlementCivilizationState dc=state.ensureSettlementCivilization(destination.id(),destOwner.id());double risk=source.infectedFraction()*(.15+.25*(1-dc.sanitation()))*(1-dc.quarantineStrength());if(rng.chance(risk))dc.adjustDisease(.08+.10*source.severity());
        }
    }

    private static void advanceMigrationGroups(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();for(MigrationGroup group:state.migrationGroups())if(group.active()){
            Settlement source=state.findSettlement(group.sourceSettlementId()).orElse(null);if(source==null){group.turnBack();continue;}
            if(group.status()==MigrationStatus.CAMPED){Settlement camp=state.findSettlement(group.campSettlementId()).orElse(null);if(camp==null){group.turnBack();continue;}Faction campOwner=state.findSettlementOwner(camp.id()).orElse(null);if(campOwner!=null){SettlementCivilizationState cc=state.ensureSettlementCivilization(camp.id(),campOwner.id());camp.setDevelopmentPriority(camp.foodSecurity()<.55?DevelopmentPriority.FOOD:camp.housingShortage()>0?DevelopmentPriority.HOUSING:DevelopmentPriority.BALANCED);cc.adjustRefugeePressure(-.006);if(camp.tier().ordinal()>=Settlement.Tier.HAMLET.ordinal()||day-group.createdDay()>180){if(camp.name().startsWith("Refugee Camp "))camp.rename("Haven "+group.id());group.settle();state.history().add(new WorldEvent(day,"refugee_camp_became_settlement","group="+group.id()+", settlement="+camp.id()+", name="+camp.name()+", people="+camp.population()));}}continue;}
            Settlement target=group.targetSettlementId()>0?state.findSettlement(group.targetSettlementId()).orElse(null):null;double distance=target==null?600:Math.max(100,source.position().distanceTo(target.position()));group.advance(Mathx.clamp(95.0/distance,.03,.35));group.consume(.008+.012*(1-group.health()));
            if(group.food()<.3){Settlement aid=nearestSettlement(state,source.position().lerp(target==null?source.position():target.position(),group.progress()),260);if(aid!=null&&aid.foodSecurity()>.68&&rng.chance(.18)){group.resupply(.10);state.history().add(new WorldEvent(day,"refugees_aided","group="+group.id()+", settlement="+aid.id()));}}
            if(group.food()<.2&&rng.chance(.08)){int lost=Math.min(group.people(),Math.max(1,group.people()/40));group.losePeople(lost);state.history().add(new WorldEvent(day,"refugee_hardship","group="+group.id()+", losses="+lost));}
            if(!group.active()||group.progress()<1)continue;
            if(target!=null&&acceptsMigration(state,group,target)){Faction owner=state.findSettlementOwner(target.id()).orElseThrow();target.addPopulation(group.people());state.ensureSettlementCivilization(target.id(),owner.id()).adjustRefugeePressure(.04+Math.min(.18,group.people()/500.0));moveAttachedHouseholds(state,group,owner.id(),target.id());group.settle();state.history().add(new WorldEvent(day,"migration_arrived","group="+group.id()+", settlement="+target.id()+", people="+group.people()+", reason="+group.reason()));}
            else establishRefugeeCamp(state,group,source);
        }
    }

    private static boolean acceptsMigration(SimulationState state,MigrationGroup group,Settlement target){Faction owner=state.findSettlementOwner(target.id()).orElse(null);if(owner==null)return false;FactionCivilizationState fc=state.ensureFactionCivilization(owner.id());SettlementCivilizationState sc=state.ensureSettlementCivilization(target.id(),owner.id());double capacity=Mathx.clamp((target.housing()-target.population()+30)/(double)Math.max(30,target.population()),0,1);double acceptance=fc.refugeeAcceptance()*.45+target.publicOrder()*.18+target.foodSecurity()*.18+capacity*.19-sc.refugeePressure()*.20;Faction origin=state.findFaction(group.originFactionId()).orElse(null);if(origin!=null&&origin.id()!=owner.id()){DiplomaticRelation rel=origin.relations().get(owner.id());if(rel!=null&&(rel.status()==RelationStatus.HOSTILE||rel.status()==RelationStatus.WAR))acceptance-=.45;}return acceptance>.42;}

    private static void establishRefugeeCamp(SimulationState state,MigrationGroup group,Settlement source){
        Faction origin=state.findFaction(group.originFactionId()).orElse(null);if(origin==null){group.turnBack();return;}
        // Soft cap: reuse a nearby existing camp under the same origin instead of spawning unbounded new settlements.
        Optional<Settlement> existing=origin.settlements().stream()
                .filter(s->s.name().startsWith("Refugee Camp ")||s.name().startsWith("Haven "))
                .filter(s->s.position().distanceTo(source.position())<900)
                .filter(s->s.population()<220)
                .min(Comparator.comparingDouble(s->s.position().distanceTo(source.position())));
        if(existing.isPresent()){
            Settlement camp=existing.get();
            camp.addPopulation(group.people());
            camp.addHousing(Math.max(4,group.people()/2));
            camp.stockpile().add(ResourceType.FOOD,Math.max(8,group.people()*1.5));
            state.ensureSettlementCivilization(camp.id(),origin.id()).adjustRefugeePressure(.12);
            moveAttachedHouseholds(state,group,origin.id(),camp.id());
            group.campAt(camp.id());
            state.history().add(new WorldEvent(state.clock().day(),"refugee_camp_absorbed","group="+group.id()+", settlement="+camp.id()+", people="+group.people()));
            return;
        }
        long campCount=origin.settlements().stream().filter(s->s.name().startsWith("Refugee Camp ")).count();
        if(campCount>=6){group.turnBack();state.history().add(new WorldEvent(state.clock().day(),"refugee_camp_refused","group="+group.id()+", reason=camp_cap"));return;}
        double angle=Math.toRadians(Math.floorMod(Long.hashCode(group.id()*73),360));double radius=180+Math.floorMod(group.id(),160);
        SimPosition p=new SimPosition(source.position().x()+Math.cos(angle)*radius,source.position().z()+Math.sin(angle)*radius);
        // Housing slightly below population so SettlementPlanner enqueues visible shelters for materialization.
        int shelter=Math.max(8,group.people()/3);
        Settlement camp=new Settlement(state.nextId(),"Refugee Camp "+group.id(),p,group.people(),shelter);
        camp.setDevelopmentPriority(DevelopmentPriority.HOUSING);
        // Seed only a completed well so water security exists; farms/pastures/houses remain pending
        // construction so camps physically appear near players instead of staying abstract markers.
        camp.markConstructionCompleted("well:0");
        camp.stockpile().add(ResourceType.FOOD,Math.max(20,group.people()*3.0));
        camp.stockpile().add(ResourceType.WOOD,Math.max(24,group.people()*2.0));
        camp.stockpile().add(ResourceType.STONE,Math.max(8,group.people()*.5));
        camp.setFoodSecurity(Mathx.clamp(group.food(),.15,.7));
        origin.addSettlement(camp);
        // Ensure planner exposes pending shelters/farms/roads for physical materialization near players.
        long pendingShelter=SettlementPlanner.pending(origin,camp).stream()
                .filter(i->i.role()==StructureRole.HOUSE||i.role()==StructureRole.FARM||i.role()==StructureRole.ROAD)
                .count();
        if(pendingShelter==0)camp.addHousing(Math.max(4,group.people()/4));
        state.ensureSettlementCivilization(camp.id(),origin.id()).adjustRefugeePressure(.75);
        moveAttachedHouseholds(state,group,origin.id(),camp.id());
        group.campAt(camp.id());
        // Church/guild aid task for the new camp.
        if(state.activeAssistanceTask(camp.id(),AssistanceTaskType.REFUGEE_SUPPORT).isEmpty()){
            state.addAssistanceTask(new AssistanceTask(state.nextId(),origin.id(),camp.id(),state.clock().day(),state.clock().day()+45,AssistanceTaskType.REFUGEE_SUPPORT,"migration:"+group.id(),.55+.25*group.people()/40.0));
        }
        state.history().add(new WorldEvent(state.clock().day(),"refugee_camp_founded","group="+group.id()+", settlement="+camp.id()+", people="+group.people()+", reason="+group.reason()));
    }

    private static void attachHouseholds(SimulationState state,MigrationGroup group,long sourceSettlementId,int people){int represented=0;List<HouseholdState> candidates=state.households().stream().filter(HouseholdState::active).filter(h->h.settlementId()==sourceSettlementId).sorted(Comparator.comparingLong(HouseholdState::id)).toList();for(HouseholdState h:candidates){if(represented>=people||group.householdIds().size()>=8)break;if(group.addHousehold(h.id()))represented+=h.representedPeople();}}
    private static void moveAttachedHouseholds(SimulationState state,MigrationGroup group,long factionId,long settlementId){for(long hid:group.householdIds())state.findHousehold(hid).ifPresent(h->{h.migrate(factionId,settlementId);for(long cid:h.memberIds())state.findSocialCitizen(cid).filter(SocialCitizen::alive).ifPresent(c->c.migrateTo(factionId,settlementId));});}

    private static void diffuseKnowledge(SimulationState state){
        for(Faction faction:state.factions())for(Settlement settlement:faction.settlements()){
            SettlementCivilizationState c=state.ensureSettlementCivilization(settlement.id(),faction.id());double scholars=specialistShare(state,settlement.id(),CitizenRole.SCHOLAR),healers=specialistShare(state,settlement.id(),CitizenRole.HEALER),artisans=specialistShare(state,settlement.id(),CitizenRole.ARTISAN);double base=.0008+.003*c.education();
            if(has(settlement,"farm:"))c.adjustKnowledge(KnowledgeDomain.AGRICULTURE,base*(.9+artisans*.2));if(has(settlement,"mine:")||has(settlement,"workshop:"))c.adjustKnowledge(KnowledgeDomain.METALLURGY,base*(.7+artisans*.5));if(has(settlement,"school:"))for(KnowledgeDomain d:KnowledgeDomain.values())c.adjustKnowledge(d,base*(.5+scholars));if(has(settlement,"clinic:"))c.adjustKnowledge(KnowledgeDomain.MEDICINE,base*(1+healers));if(has(settlement,"observatory:"))c.adjustKnowledge(KnowledgeDomain.CARTOGRAPHY,base*.9);if(has(settlement,"factory:"))c.adjustKnowledge(KnowledgeDomain.INDUSTRY,base*(1+artisans*.4));if(has(settlement,"dock:"))c.adjustKnowledge(KnowledgeDomain.NAVIGATION,base*.8);if(has(settlement,"barracks:"))c.adjustKnowledge(KnowledgeDomain.MILITARY,base*.55);if(has(settlement,"keep:")||has(settlement,"workshop:")||has(settlement,"wall:")||has(settlement,"house:"))c.adjustKnowledge(KnowledgeDomain.CONSTRUCTION,base*(.75+specialistShare(state,settlement.id(),CitizenRole.BUILDER)*.45));if(faction.name().equals(WizardTreesSeeder.FACTION_NAME))c.adjustKnowledge(KnowledgeDomain.ARCANA,base*1.5);
        }
        for(TransportRoute route:state.routes())if(route.operational()){
            Settlement a=state.findSettlement(route.fromSettlementId()).orElse(null),b=state.findSettlement(route.toSettlementId()).orElse(null);if(a==null||b==null)continue;Faction ao=state.findSettlementOwner(a.id()).orElse(null),bo=state.findSettlementOwner(b.id()).orElse(null);if(ao==null||bo==null)continue;SettlementCivilizationState ac=state.ensureSettlementCivilization(a.id(),ao.id()),bc=state.ensureSettlementCivilization(b.id(),bo.id());double rate=.003*Mathx.clamp(route.quality()*route.security(),.1,1);for(KnowledgeDomain d:KnowledgeDomain.values()){double av=ac.knowledge(d),bv=bc.knowledge(d);if(av>bv)bc.adjustKnowledge(d,(av-bv)*rate);else if(bv>av)ac.adjustKnowledge(d,(bv-av)*rate);}
        }
        if(state.clock().day()%30==0)applyKnowledgeBenefits(state);
    }

    private static void applyKnowledgeBenefits(SimulationState state){
        for(Faction faction:state.factions()){
            double weighted=0,people=0;for(Settlement s:faction.settlements()){SettlementCivilizationState c=state.ensureSettlementCivilization(s.id(),faction.id());double p=Math.max(1,s.population());double general=(c.knowledge(KnowledgeDomain.AGRICULTURE)+c.knowledge(KnowledgeDomain.METALLURGY)+c.knowledge(KnowledgeDomain.CONSTRUCTION)+c.knowledge(KnowledgeDomain.MEDICINE)+c.knowledge(KnowledgeDomain.INDUSTRY)+c.knowledge(KnowledgeDomain.NAVIGATION))/6.0;weighted+=general*p;people+=p;s.setFoodSecurity(s.foodSecurity()+.0012*(c.knowledge(KnowledgeDomain.AGRICULTURE)-.35));s.improveInfrastructure(.001*c.knowledge(KnowledgeDomain.CONSTRUCTION));}
            if(people<=0)continue;double knowledge=weighted/people;double targetTech=knowledge*1.6;if(targetTech>faction.technology())faction.advanceTechnology(Math.min(.004,(targetTech-faction.technology())*.003));
        }
    }

    private static void trainApprentices(SimulationState state){
        long day=state.clock().day();
        Map<Long,List<SocialCitizen>> bySettlement=new HashMap<>();
        for(SocialCitizen c:state.socialCitizens())if(c.alive()&&c.ageYears(day)>=16)bySettlement.computeIfAbsent(c.settlementId(),k->new ArrayList<>()).add(c);
        for(var entry:bySettlement.entrySet()){
            Settlement settlement=state.findSettlement(entry.getKey()).orElse(null);Faction owner=settlement==null?null:state.findSettlementOwner(settlement.id()).orElse(null);if(settlement==null||owner==null)continue;
            SettlementCivilizationState civ=state.ensureSettlementCivilization(settlement.id(),owner.id());boolean school=has(settlement,"school:");Map<CitizenRole,SocialCitizen> mentorByRole=new EnumMap<>(CitizenRole.class);
            for(SocialCitizen c:entry.getValue())mentorByRole.merge(c.role(),c,(a,b)->a.professionSkill()>=b.professionSkill()?a:b);
            for(SocialCitizen apprentice:entry.getValue()){
                int age=apprentice.ageYears(day);if(age>32||apprentice.professionSkill()>=.88)continue;SocialCitizen mentor=mentorByRole.get(apprentice.role());if(mentor==null||mentor.id()==apprentice.id()||mentor.professionSkill()<apprentice.professionSkill()+.10)continue;
                double before=apprentice.professionSkill();double training=.008+.012*civ.education()+.012*mentor.professionSkill()+(school?.008:0);apprentice.practiceProfession(training);double after=apprentice.professionSkill();
                if((before<.50&&after>=.50)||(before<.75&&after>=.75)){
                    GuildRank rank=GuildRank.of(after);
                    apprentice.remember(new CitizenMemory(day,MemoryType.LOCAL_EVENT,"citizen:"+mentor.id(),mentor.name(),"I was raised to "+rank.titleFor(apprentice.role())+" under "+mentor.name()+".",settlement.position(),.55,1));
                    state.history().add(new WorldEvent(day,"apprenticeship_milestone","citizen="+apprentice.id()+", mentor="+mentor.id()+", role="+apprentice.role()+", rank="+rank.key()+", skill="+String.format(java.util.Locale.ROOT,"%.2f",after)));
                }
            }
        }
    }

    private static void simulateRuins(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();Map<Long,RuinSite> bySettlement=new HashMap<>();for(RuinSite r:state.ruinSites())if(r.active())bySettlement.put(r.originalSettlementId(),r);
        for(Faction faction:state.factions())for(Settlement settlement:faction.settlements()){RuinSite ruin=bySettlement.get(settlement.id());if(settlement.population()<=0){if(ruin==null){String cause=state.wars().stream().anyMatch(w->w.involves(faction.id())&&w.targetSettlementId()==settlement.id())?"war":"abandonment";ruin=new RuinSite(state.nextId(),settlement.id(),faction.id(),day,settlement.position(),settlement.name(),cause);state.addRuinSite(ruin);state.history().add(new WorldEvent(day,"settlement_became_ruin","ruin="+ruin.id()+", settlement="+settlement.id()+", faction="+faction.id()+", cause="+cause));}else if(!ruin.looted()&&rng.chance(.002+.008*(1-settlement.publicOrder()))){ruin.markLooted();state.history().add(new WorldEvent(day,"ruin_looted","ruin="+ruin.id()+", settlement="+settlement.id()));}}else if(ruin!=null){ruin.reclaim();state.history().add(new WorldEvent(day,"ruin_reclaimed","ruin="+ruin.id()+", settlement="+settlement.id()+", population="+settlement.population()));}}
        if(day%30==0)for(RuinSite ruin:state.ruinSites())if(ruin.active())ruin.weather(.0025);
    }

    private static void simulateCivicEvents(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();
        for(CivicEvent event:state.civicEvents())if(event.active()){
            Settlement settlement=state.findSettlement(event.settlementId()).orElse(null);Faction faction=state.findFaction(event.factionId()).orElse(null);
            if(settlement==null||faction==null||day>event.endDay()){event.finish();continue;}
            SettlementCivilizationState sc=state.ensureSettlementCivilization(settlement.id(),faction.id());FactionCivilizationState fc=state.ensureFactionCivilization(faction.id());
            double desired=Math.max(1,Math.min(18,settlement.population()*.006*event.intensity()));double consumed=faction.stockpile().take(ResourceType.FOOD,desired);double provision=desired<=0?1:Mathx.clamp(consumed/desired,0,1);double attendance=Mathx.clamp((.35+.35*settlement.publicOrder()+.30*sc.culturalCohesion())*provision,0,1);event.setAttendance(attendance);
            double effect=.0008*event.intensity()*attendance;sc.adjustCohesion(effect);settlement.adjustUnrest(-effect*.65);settlement.adjustProsperity(effect*.25);
            if(event.type()==CivicEventType.RELIGIOUS_RITUAL)fc.approach(fc.culturalInfluence(),Math.min(1,fc.religiousInfluence()+.01),fc.education(),fc.propaganda(),fc.intelligence(),.002);
            if(day>=event.endDay()){event.finish();state.history().add(new WorldEvent(day,"civic_event_ended","event="+event.id()+", settlement="+settlement.id()+", attendance="+String.format(Locale.ROOT,"%.2f",event.attendance())));}
        }
        // Daily generation for time-sensitive rites (holy days / succession / mourning / victory / wedding).
        // Generic seasonal fairs remain on the 30-day cadence so ordinary festivals stay bounded.
        CivilizationCalendar.Season season=CivilizationCalendar.season(day);
        for(Faction faction:state.factions())for(Settlement settlement:faction.settlements()){
            if(day<=0||settlement.population()<24||state.civicEvents().stream().anyMatch(e->e.active()&&e.settlementId()==settlement.id()))continue;
            boolean culturalSite=has(settlement,"temple:")||has(settlement,"tavern:")||has(settlement,"market:");if(!culturalSite)continue;
            SettlementCivilizationState sc=state.ensureSettlementCivilization(settlement.id(),faction.id());
            FactionCivilizationState fc=state.ensureFactionCivilization(faction.id());
            FaithCatalog.FaithProfile faith=FaithCatalog.of(fc.faithName());
            boolean underSiege=state.sieges().stream().anyMatch(s->s.active()&&s.settlementId()==settlement.id());
            boolean epidemic=state.epidemics().stream().anyMatch(e->e.active()&&e.settlementId()==settlement.id());
            boolean atWar=state.wars().stream().anyMatch(w->w.active()&&w.involves(faction.id()));
            // advanceDays simulates day D then increments the clock; holy-day D is observed while clock still reads D-1.
            boolean holy=faith.isHolyDay(day)||faith.isHolyDay(day+1);
            DynastyState dynasty=state.dynasties().get(faction.id());
            boolean coronation=dynasty!=null&&dynasty.lastSuccessionDay()>0&&day-dynasty.lastSuccessionDay()<=2&&day-dynasty.lastSuccessionDay()>=0;
            boolean mourning=dynasty!=null&&dynasty.successionCrisis()&&dynasty.crisisSinceDay()>0&&day-dynasty.crisisSinceDay()<=2;
            boolean victory=state.history().recent(16).stream().anyMatch(e->e.day()>=day-2&&e.type().equals("peace_treaty")&&e.message().contains(faction.name()));
            boolean wedding=state.history().recent(16).stream().anyMatch(e->e.day()>=day-2&&e.type().equals("political_marriage")&&e.message().contains("factions=")&&e.message().contains(String.valueOf(faction.id())));
            CivicEventType type=null;
            if(coronation&&has(settlement,"keep:"))type=CivicEventType.CORONATION;
            else if(mourning&&has(settlement,"temple:"))type=CivicEventType.MOURNING;
            else if(victory&&has(settlement,"tavern:")&&!underSiege)type=CivicEventType.VICTORY_FEAST;
            else if(wedding&&has(settlement,"keep:")&&!underSiege)type=CivicEventType.WEDDING_FEAST;
            else if(holy&&has(settlement,"temple:")&&!underSiege&&!epidemic)type=CivicEventType.RELIGIOUS_RITUAL;
            else if(day%30==0&&!underSiege&&!epidemic&&!(atWar&&sc.banditPressure()>.55)){
                if(season==CivilizationCalendar.Season.AUTUMN&&has(settlement,"farm:"))type=CivicEventType.HARVEST_FESTIVAL;
                else if(has(settlement,"temple:")&&fc.religiousInfluence()>.56)type=CivicEventType.RELIGIOUS_RITUAL;
                else if(has(settlement,"market:")&&fc.mercantileTradition()>.55)type=CivicEventType.MARKET_FAIR;
                else if(has(settlement,"tavern:")||has(settlement,"market:"))type=CivicEventType.HARVEST_FESTIVAL;
            }
            if(type==null)continue;
            double intensity=Mathx.clamp(.32+.28*settlement.prosperity()+.2*sc.culturalCohesion()+rng.between(-.05,.05)+(holy?.08:0),.2,.92);
            CivicEvent event=new CivicEvent(state.nextId(),faction.id(),settlement.id(),day,day+2,type,civicTitle(type,settlement,faith),intensity);state.addCivicEvent(event);
            state.history().add(new WorldEvent(day,"civic_event_started","event="+event.id()+", type="+type+", settlement="+settlement.id()));
            for(SocialCitizen c:state.socialCitizens())if(c.alive()&&c.settlementId()==settlement.id()&&Math.floorMod(c.id()+day,5)==0)c.remember(new CitizenMemory(day,MemoryType.LOCAL_EVENT,"civic:"+event.id(),event.title(),"The community gathered for "+event.title()+".",settlement.position(),.42,1));
        }
        if(day>0&&day%30==0)applyHolyOrderPresence(state);
    }

    /** Holy orders are not flavor-only: temples + priests reduce bandit pressure and reinforce cohesion. */
    private static void applyHolyOrderPresence(SimulationState state){
        for(Faction faction:state.factions()){
            FactionCivilizationState fc=state.ensureFactionCivilization(faction.id());
            FaithCatalog.FaithProfile faith=FaithCatalog.of(fc.faithName());
            for(Settlement settlement:faction.settlements()){
                if(!has(settlement,"temple:"))continue;
                long clergy=state.socialCitizens().stream().filter(SocialCitizen::alive).filter(c->c.settlementId()==settlement.id()&&(c.role()==CitizenRole.PRIEST||c.role()==CitizenRole.SCHOLAR)).count();
                if(clergy<=0&&fc.religiousInfluence()<.35)continue;
                SettlementCivilizationState sc=state.ensureSettlementCivilization(settlement.id(),faction.id());
                double orderStrength=Mathx.clamp(.012+.008*clergy+.01*fc.religiousInfluence()+.006*faith.heresySeverity(),0,.05);
                sc.adjustBanditPressure(-orderStrength);
                sc.adjustCohesion(orderStrength*.55);
                settlement.setPublicOrder(Math.min(1,settlement.publicOrder()+orderStrength*.35));
                if(Math.floorMod(settlement.id()+state.clock().day(),97)==0){
                    state.history().add(new WorldEvent(state.clock().day(),"holy_order_patrol",
                            settlement.name()+" • "+faith.holyOrder()+" presence reinforced order"));
                }
            }
        }
    }

    private static String civicTitle(CivicEventType type,Settlement settlement,FaithCatalog.FaithProfile faith){return switch(type){case HARVEST_FESTIVAL->settlement.name()+" Harvest Feast";case RELIGIOUS_RITUAL->settlement.name()+" "+faith.primaryDeity()+" Rite";case VICTORY_FEAST->settlement.name()+" Victory Feast";case MOURNING->settlement.name()+" Day of Mourning";case CORONATION->settlement.name()+" Coronation";case MARKET_FAIR->settlement.name()+" Market Fair";case WEDDING_FEAST->settlement.name()+" Wedding Feast";};}

    private static void simulateIntelligenceOperations(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();
        for(IntelligenceOperation op:state.intelligenceOperations())if(op.active()){
            Faction source=state.findFaction(op.sourceFactionId()).orElse(null),target=state.findFaction(op.targetFactionId()).orElse(null);if(source==null||target==null){op.fail();continue;}
            FactionCivilizationState sc=state.ensureFactionCivilization(source.id()),tc=state.ensureFactionCivilization(target.id());SocialCitizen agent=op.agentCitizenId()>0?state.findSocialCitizen(op.agentCitizenId()).filter(SocialCitizen::alive).orElse(null):null;
            double skill=agent==null?.35:agent.professionSkill();double network=sc.spyStrength(target.id());double counter=.20+tc.intelligence()*.45+target.government().lawEnforcement()*.25;op.advance(.006+.010*sc.intelligence()+.012*network+.008*skill,.008+.012*skill+.008*network);op.erodeSecrecy(.0015+.003*counter-.0015*sc.intelligence());
            if(!op.discovered()&&op.secrecy()<.26&&rng.chance(.02+.08*counter)){op.discover();source.relationWith(target.id()).adjust(-4);target.relationWith(source.id()).adjust(-4);sc.adjustSpyNetwork(target.id(),-.08);state.history().add(new WorldEvent(day,"spy_discovered","operation="+op.id()+", source="+source.id()+", target="+target.id()));if(agent!=null){Settlement loc=target.settlements().stream().min(Comparator.comparingDouble(s->s.position().distanceTo(state.findSettlement(agent.settlementId()).map(Settlement::position).orElse(s.position())))).orElse(null);if(loc!=null){CrimeResult result=state.reportCrime("citizen:"+agent.id(),target.id(),CrimeType.TRESPASS,0,loc.position(),true,2,"intelligence","espionage");if(result.registered()){JusticeCase jc=new JusticeCase(state.nextId(),target.id(),loc.id(),day,"citizen:"+agent.id(),CrimeType.TRESPASS);jc.charge();state.addJusticeCase(jc);}}}}
            if(op.progress()>=1){op.finish();sc.adjustSpyNetwork(target.id(),.05+.08*op.quality());KnowledgeDomain domain=switch(op.type()){case MILITARY_RECON->KnowledgeDomain.MILITARY;case ECONOMIC_RECON->KnowledgeDomain.INDUSTRY;case POLITICAL_INFILTRATION,COUNTERINTELLIGENCE->KnowledgeDomain.CARTOGRAPHY;};for(Settlement s:source.settlements())state.ensureSettlementCivilization(s.id(),source.id()).adjustKnowledge(domain,.006+.02*op.quality());state.history().add(new WorldEvent(day,"intelligence_report","operation="+op.id()+", source="+source.id()+", target="+target.id()+", type="+op.type()+", quality="+String.format(Locale.ROOT,"%.2f",op.quality())));}
        }
        if(day<=0||day%30!=0)return;
        for(Faction source:state.factions()){
            FactionCivilizationState sc=state.ensureFactionCivilization(source.id());if(sc.intelligence()<.12&&sc.spyNetworks().isEmpty())continue;
            Faction target=source.relations().entrySet().stream().filter(e->e.getValue().status().ordinal()>=RelationStatus.RIVAL.ordinal()).map(e->state.findFaction(e.getKey()).orElse(null)).filter(Objects::nonNull).max(Comparator.comparingDouble(f->sc.spyStrength(f.id())-source.relationWith(f.id()).opinion()/200.0)).orElse(null);if(target==null)continue;
            if(state.intelligenceOperations().stream().anyMatch(o->o.active()&&o.sourceFactionId()==source.id()&&o.targetFactionId()==target.id()))continue;
            SocialCitizen agent=state.socialCitizens().stream().filter(SocialCitizen::alive).filter(c->c.factionId()==source.id()&&c.ageYears(day)>=18&&(c.role()==CitizenRole.SPY||c.role()==CitizenRole.TRADER||c.role()==CitizenRole.OFFICIAL||c.role()==CitizenRole.SCHOLAR)).max(Comparator.comparingDouble(SocialCitizen::professionSkill)).orElse(null);
            IntelligenceOperationType type;
            if(source.relationWith(target.id()).status()==RelationStatus.WAR)type=IntelligenceOperationType.MILITARY_RECON;
            else if(sc.spyStrength(target.id())>.42&&state.intelligenceOperations().stream().anyMatch(o->!o.active()&&o.sourceFactionId()==source.id()&&o.targetFactionId()==target.id()&&o.discovered()))type=IntelligenceOperationType.COUNTERINTELLIGENCE;
            else if(sc.mercantileTradition()>.58)type=IntelligenceOperationType.ECONOMIC_RECON;
            else type=IntelligenceOperationType.POLITICAL_INFILTRATION;
            IntelligenceOperation op=new IntelligenceOperation(state.nextId(),source.id(),target.id(),day,agent==null?0:agent.id(),type);state.addIntelligenceOperation(op);sc.adjustSpyNetwork(target.id(),.015);state.history().add(new WorldEvent(day,"intelligence_operation_started","operation="+op.id()+", source="+source.id()+", target="+target.id()+", type="+type));
        }
    }

    private static void simulatePropagandaCampaigns(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();
        for(PropagandaCampaign campaign:state.propagandaCampaigns())if(campaign.active()){
            Faction faction=state.findFaction(campaign.factionId()).orElse(null);if(faction==null||day>campaign.endDay()){campaign.finish();continue;}List<Settlement> targets=campaign.settlementId()>0?faction.settlements().stream().filter(s->s.id()==campaign.settlementId()).toList():faction.settlements();if(targets.isEmpty()){campaign.finish();continue;}
            FactionCivilizationState fc=state.ensureFactionCivilization(faction.id());double capacity=Mathx.clamp(.25+.35*fc.culturalInfluence()+.25*fc.education()+.15*(1-faction.government().corruption()),0,1);campaign.setReach(capacity);double effect=.0007*campaign.intensity()*campaign.reach();for(Settlement s:targets){SettlementCivilizationState sc=state.ensureSettlementCivilization(s.id(),faction.id());switch(campaign.theme()){case UNITY->{s.adjustUnrest(-effect);sc.adjustCohesion(effect*.8);}case WAR_EFFORT->{for(Army a:faction.armies())a.adjustMorale(effect*.4);s.adjustUnrest(-effect*.35);}case LEGITIMACY->{faction.government().adjustLegitimacy(effect*.35);s.adjustUnrest(-effect*.45);}case RELIGION->{sc.adjustCohesion(effect*.6);fc.approach(fc.culturalInfluence(),Math.min(1,fc.religiousInfluence()+effect),fc.education(),fc.propaganda(),fc.intelligence(),.001);}case ANTI_BANDIT->{sc.adjustBanditPressure(-effect*.8);s.setPublicOrder(s.publicOrder()+effect*.25);}case RECONSTRUCTION->{s.adjustProsperity(effect*.45);sc.adjustCohesion(effect*.35);}}}
            fc.approach(fc.culturalInfluence(),fc.religiousInfluence(),fc.education(),Math.min(1,fc.propaganda()+effect*3),fc.intelligence(),.003);double cost=Math.min(faction.treasury(),.18*campaign.intensity()*Math.max(1,targets.stream().mapToInt(Settlement::population).sum()/100.0));faction.addTreasury(-cost);
            if(day>=campaign.endDay()){campaign.finish();state.history().add(new WorldEvent(day,"propaganda_campaign_ended","campaign="+campaign.id()+", faction="+faction.id()+", reach="+String.format(Locale.ROOT,"%.2f",campaign.reach())));}
        }
        if(day<=0||day%30!=0)return;
        for(Faction faction:state.factions()){
            if(state.propagandaCampaigns().stream().anyMatch(c->c.active()&&c.factionId()==faction.id()))continue;double unrest=faction.settlements().stream().mapToDouble(Settlement::unrest).average().orElse(0);boolean war=state.wars().stream().anyMatch(w->w.active()&&w.involves(faction.id()));double bandits=faction.settlements().stream().mapToDouble(s->state.ensureSettlementCivilization(s.id(),faction.id()).banditPressure()).average().orElse(0);FactionCivilizationState fc=state.ensureFactionCivilization(faction.id());double infraStress=faction.settlements().stream().mapToDouble(s->1-Math.min(1,s.infrastructure())).average().orElse(0);PropagandaTheme theme=null;if(war)theme=PropagandaTheme.WAR_EFFORT;else if(unrest>.38)theme=PropagandaTheme.UNITY;else if(faction.government().legitimacy()<.42)theme=PropagandaTheme.LEGITIMACY;else if(bandits>.30)theme=PropagandaTheme.ANTI_BANDIT;else if(fc.religiousInfluence()>.62&&hasAny(faction,"temple:"))theme=PropagandaTheme.RELIGION;else if(infraStress>.45)theme=PropagandaTheme.RECONSTRUCTION;if(theme==null)continue;Settlement focus=faction.settlements().stream().max(Comparator.comparingDouble(Settlement::unrest)).orElse(null);double intensity=Mathx.clamp(.35+unrest*.35+(war?.18:0)+rng.between(-.04,.04),.25,.9);PropagandaCampaign campaign=new PropagandaCampaign(state.nextId(),faction.id(),focus==null?0:focus.id(),day,day+29,theme,intensity);state.addPropagandaCampaign(campaign);state.history().add(new WorldEvent(day,"propaganda_campaign_started","campaign="+campaign.id()+", faction="+faction.id()+", theme="+theme));
        }
    }

    private static void spawnNpcCrime(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();
        for(SocialCitizen c:state.socialCitizens())if(c.alive()&&c.ageYears(day)>=16){Settlement s=state.findSettlement(c.settlementId()).orElse(null);Faction f=state.findFaction(c.factionId()).orElse(null);if(s==null||f==null)continue;boolean pending=state.justiceCases().stream().anyMatch(j->j.active()&&j.accusedKey().equals("citizen:"+c.id()));if(pending)continue;FactionCivilizationState civ=state.ensureFactionCivilization(f.id());FaithCatalog.FaithProfile faith=FaithCatalog.of(civ.faithName());double pressure=(1-c.needs().hunger())*.25+(1-c.needs().status())*.12+c.personality().greed()*.16+c.personality().treachery()*.12+c.personality().aggression()*.08+(1-s.publicOrder())*.18;if(!rng.chance(Mathx.clamp(pressure*.018,0,.06)))continue;CrimeType type;if(has(s,"temple:")&&faith.heresySeverity()>.5&&c.personality().treachery()>.55&&c.personality().loyalty()<.45&&rng.chance(faith.heresySeverity()*.25))type=CrimeType.HERESY;else if(c.role()==CitizenRole.TRADER&&f.government().corruption()>.4&&c.personality().greed()>.6&&rng.chance(.3))type=CrimeType.SMUGGLING;else if(c.personality().aggression()>.72&&rng.chance(.35))type=CrimeType.ASSAULT;else if(c.personality().greed()>.65)type=CrimeType.THEFT;else type=CrimeType.TRESPASS;boolean witnessed=rng.chance(.45+.4*f.government().lawEnforcement());CrimeResult result=state.reportCrime("citizen:"+c.id(),f.id(),type,type==CrimeType.THEFT||type==CrimeType.SMUGGLING?rng.between(4,25):0,s.position(),witnessed,witnessed?1+rng.nextInt(3):0,"settlement:"+s.id(),"npc_simulation");if(result.registered()){JusticeCase jc=new JusticeCase(state.nextId(),f.id(),s.id(),day,"citizen:"+c.id(),type);state.addJusticeCase(jc);}}
    }

    private static void advanceJusticeCases(SimulationState state){
        long day=state.clock().day();
        for(JusticeCase jc:state.justiceCases())if(jc.active()){
            SocialCitizen accused=state.findSocialCitizen(parseCitizenKey(jc.accusedKey())).orElse(null);Faction faction=state.findFaction(jc.factionId()).orElse(null);Settlement settlement=state.findSettlement(jc.settlementId()).orElse(null);if(faction==null||settlement==null){jc.dismiss();continue;}FactionCivilizationState policy=state.ensureFactionCivilization(faction.id());
            if(jc.status()==JusticeStatus.INVESTIGATING){
                long investigateDays=2+Math.round(policy.dueProcess()*2)-(has(settlement,"courthouse:")?1:0);
                if(day-jc.openedDay()>=Math.max(1,investigateDays)){jc.charge();state.history().add(new WorldEvent(day,"court_case_charged","case="+jc.id()+", accused="+jc.accusedKey()+", settlement="+settlement.id()));}
                continue;
            }
            if(jc.status()==JusticeStatus.CHARGED){
                long processDays=1+Math.round(policy.dueProcess()*3)-(has(settlement,"courthouse:")?1:0);if(day-jc.openedDay()<processDays)continue;
                double evidence=evidenceStrength(state,jc);double convictionThreshold=.28+policy.dueProcess()*.30;
                if(evidence<convictionThreshold){jc.dismiss();state.history().add(new WorldEvent(day,"court_case_dismissed","case="+jc.id()+", accused="+jc.accusedKey()+", evidence="+String.format(java.util.Locale.ROOT,"%.2f",evidence)));continue;}
                SentenceType sentence=sentenceFor(jc.crimeType(),policy,settlement);double fine=jc.crimeType().baseBounty()*(.35+.8*policy.lawSeverity());long release=day+(sentence==SentenceType.IMPRISONMENT?Math.max(2,(long)Math.ceil(fine/25.0)):0);jc.sentence(sentence,sentence==SentenceType.FINE||sentence==SentenceType.RESTITUTION?fine:0,release);applySentence(state,jc,accused,faction,settlement);
            }
            else if(jc.status()==JusticeStatus.SERVING&&day>=jc.releaseDay()){jc.complete();state.history().add(new WorldEvent(day,"sentence_completed","case="+jc.id()+", accused="+jc.accusedKey()));}
            else if(jc.status()==JusticeStatus.SENTENCED)jc.complete();
        }
    }

    private static double evidenceStrength(SimulationState state,JusticeCase jc){
        return state.crimeLedger().incidents().stream().filter(i->i.actorKey().equals(jc.accusedKey())&&i.jurisdictionFactionId()==jc.factionId()&&i.type()==jc.crimeType()&&i.day()>=jc.openedDay()-1).max(Comparator.comparingLong(CrimeIncident::day).thenComparingLong(CrimeIncident::id)).map(i->Mathx.clamp((i.witnessed()?.42:.08)+Math.min(.38,i.witnessCount()*.11)+(i.evidence().isBlank()?0:.16),0,1)).orElse(.18);
    }

    private static SentenceType sentenceFor(CrimeType crime,FactionCivilizationState policy,Settlement settlement){double severity=policy.lawSeverity();return switch(crime){case TRESPASS,POACHING->severity>.75?SentenceType.FINE:SentenceType.WARNING;case THEFT,BURGLARY,SMUGGLING->severity>.72&&has(settlement,"prison:")?SentenceType.IMPRISONMENT:SentenceType.RESTITUTION;case ASSAULT,ROBBERY,SABOTAGE,ARSON->severity>.5&&has(settlement,"prison:")?SentenceType.IMPRISONMENT:SentenceType.FINE;case HERESY->{FaithCatalog.FaithProfile faith=FaithCatalog.of(policy.faithName());double heresy=faith.heresySeverity();if(heresy>.6&&severity>.55)yield has(settlement,"prison:")?SentenceType.IMPRISONMENT:SentenceType.EXILE;if(heresy>.35)yield SentenceType.FINE;yield SentenceType.WARNING;}case MURDER,REGICIDE,WAR_CRIME->severity>.86?SentenceType.EXECUTION:severity>.45?SentenceType.EXILE:SentenceType.IMPRISONMENT;};}
    private static void applySentence(SimulationState state,JusticeCase jc,SocialCitizen accused,Faction faction,Settlement settlement){long day=state.clock().day();switch(jc.sentence()){case WARNING->{}case FINE->{if(accused!=null){double paid=Math.min(accused.money(),jc.fine());accused.addMoney(-paid);faction.addTreasury(paid);}}case RESTITUTION->{if(accused!=null){double paid=Math.min(accused.money(),jc.fine());accused.addMoney(-paid);settlement.stockpile().add(ResourceType.GOLD,paid*.55);settlement.setPublicOrder(settlement.publicOrder()+.01);faction.addTreasury(paid*.45);}}case IMPRISONMENT->{if(has(settlement,"prison:"))state.addCustody(new CustodyRecord(state.nextId(),jc.accusedKey(),faction.id(),day,jc.releaseDay(),jc.crimeType().baseBounty(),"court_case:"+jc.id()));}case EXILE->{if(accused!=null)exileCitizen(state,accused,faction,settlement);}case EXECUTION->{if(accused!=null&&accused.alive())state.recordPhysicalCitizenDeath(settlement.id(),accused.id(),"lawful_execution");}}state.history().add(new WorldEvent(day,"court_sentence","case="+jc.id()+", accused="+jc.accusedKey()+", sentence="+jc.sentence()+", faction="+faction.id()));}
    private static void exileCitizen(SimulationState state,SocialCitizen accused,Faction faction,Settlement settlement){
        Settlement target=state.factions().stream().filter(f->f.id()!=faction.id()).filter(f->f.relations().get(faction.id())==null||f.relations().get(faction.id()).status()!=RelationStatus.WAR).flatMap(f->f.settlements().stream()).min(Comparator.comparingDouble(s->s.position().distanceTo(settlement.position()))).orElse(null);
        if(target==null){state.ensureSettlementCivilization(settlement.id(),faction.id()).adjustBanditPressure(.08);return;}
        Faction owner=state.findSettlementOwner(target.id()).orElseThrow();long oldHouseholdId=accused.householdId();state.findHousehold(oldHouseholdId).ifPresent(h->h.removeMember(accused.id()));
        HouseholdState exileHousehold=new HouseholdState(state.nextId(),owner.id(),target.id(),state.clock().day());exileHousehold.addMember(accused.id());exileHousehold.adjustWealth(accused.money()*.2);exileHousehold.setHomeKey("exile:"+target.id());state.addHousehold(exileHousehold);
        accused.setHouseholdId(exileHousehold.id());accused.migrateTo(owner.id(),target.id());state.history().add(new WorldEvent(state.clock().day(),"citizen_exiled","citizen="+accused.id()+", from="+settlement.id()+", to="+target.id()+", oldHousehold="+oldHouseholdId));
    }

    private static void simulateHiddenCaches(SimulationState state){
        long day=state.clock().day();
        for(Faction faction:state.factions())for(Settlement settlement:faction.settlements()){
            SettlementCivilizationState c=state.ensureSettlementCivilization(settlement.id(),faction.id());
            boolean threat=c.banditPressure()>.65||state.wars().stream().anyMatch(w->w.active()&&(w.attackerFactionId()==faction.id()||w.defenderFactionId()==faction.id())&&(w.targetSettlementId()==0||w.targetSettlementId()==settlement.id()));
            HiddenCache existing=state.hiddenCaches().stream().filter(x->!x.recovered()&&x.settlementId()==settlement.id()).findFirst().orElse(null);
            if(threat&&existing==null&&(faction.stockpile().get(ResourceType.GOLD)>12||faction.stockpile().get(ResourceType.TOOLS)>40)){
                EnumMap<ResourceType,Double> goods=new EnumMap<>(ResourceType.class);
                double gold=faction.stockpile().take(ResourceType.GOLD,Math.min(24,faction.stockpile().get(ResourceType.GOLD)*.08));
                double tools=faction.stockpile().take(ResourceType.TOOLS,Math.min(30,faction.stockpile().get(ResourceType.TOOLS)*.05));
                goods.put(ResourceType.GOLD,gold);goods.put(ResourceType.TOOLS,tools);
                double angle=Math.toRadians(Math.floorMod(Objects.hash(settlement.id(),day),360));
                SimPosition pos=new SimPosition(settlement.position().x()+Math.cos(angle)*95,settlement.position().z()+Math.sin(angle)*95);
                HiddenCache cache=new HiddenCache(state.nextId(),faction.id(),settlement.id(),day,pos,goods);state.addHiddenCache(cache);
                state.history().add(new WorldEvent(day,"wealth_hidden","cache="+cache.id()+", settlement="+settlement.id()));
                existing=cache;
            }
            if(existing!=null&&!existing.recovered()&&!existing.discovered())discoverHiddenCacheByIntelligence(state,existing,faction,day);
            if(!threat&&existing!=null&&!existing.recovered()&&day-existing.createdDay()>45){
                for(var e:existing.goods().entrySet())faction.stockpile().add(e.getKey(),e.getValue());existing.recover();
                state.history().add(new WorldEvent(day,"hidden_cache_recovered","cache="+existing.id()+", settlement="+settlement.id()));
            }else if(existing!=null&&existing.discovered()&&!existing.recovered()&&day-existing.createdDay()>15){
                Faction finder=state.findFaction(existing.discoveredByFactionId()).orElse(null);
                if(finder!=null){DiplomaticRelation rel=finder.relations().get(faction.id());double hostility=rel!=null&&(rel.status()==RelationStatus.HOSTILE||rel.status()==RelationStatus.WAR)?1:.2;double spy=state.ensureFactionCivilization(finder.id()).spyStrength(faction.id());double roll=unit(state.seed()^existing.id()*31L^finder.id()*131L^day*17L,7);if(roll<(.015+.09*spy)*hostility){
                    double gold=existing.goods().getOrDefault(ResourceType.GOLD,0.0),tools=existing.goods().getOrDefault(ResourceType.TOOLS,0.0);finder.stockpile().add(ResourceType.GOLD,gold);finder.stockpile().add(ResourceType.TOOLS,tools);existing.recover();state.history().add(new WorldEvent(day,"hidden_cache_raided","cache="+existing.id()+", owner="+faction.id()+", finder="+finder.id()+", value="+Math.round(existing.value())));
                }}
            }
        }
    }

    private static void discoverHiddenCacheByIntelligence(SimulationState state,HiddenCache cache,Faction owner,long day){
        for(Faction finder:state.factions()){
            if(finder.id()==owner.id())continue;FactionCivilizationState intel=state.ensureFactionCivilization(finder.id());double network=intel.spyStrength(owner.id());if(network<.08)continue;
            double chance=.015+network*.13+intel.intelligence()*.025;double roll=unit(state.seed()^cache.id()*97L^finder.id()*193L^day*29L,11);if(roll>=chance)continue;
            cache.discover(finder.id());state.history().add(new WorldEvent(day,"hidden_cache_compromised","cache="+cache.id()+", owner="+owner.id()+", finder="+finder.id()));break;
        }
    }

    private static void simulatePoliticalMarriages(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();
        for(DiplomaticMarriage marriage:state.diplomaticMarriages())if(marriage.active()){
            SocialCitizen a=state.findSocialCitizen(marriage.citizenA()).orElse(null),b=state.findSocialCitizen(marriage.citizenB()).orElse(null);
            if(a==null||b==null||!a.alive()||!b.alive()){
                marriage.end();if(a!=null)a.relationship("citizen:"+marriage.citizenB()).setFamilyBond(FamilyBond.NONE);if(b!=null)b.relationship("citizen:"+marriage.citizenA()).setFamilyBond(FamilyBond.NONE);
                Faction fa=state.findFaction(marriage.factionA()).orElse(null),fb=state.findFaction(marriage.factionB()).orElse(null);if(fa!=null&&fb!=null){fa.relationWith(fb.id()).adjust(-2);fb.relationWith(fa.id()).adjust(-2);}state.history().add(new WorldEvent(day,"political_marriage_ended","marriage="+marriage.id()+", reason=death_or_missing"));
            }
        }
        if(state.diplomaticMarriages().stream().filter(DiplomaticMarriage::active).count()>=SimulationState.MAX_DIPLOMATIC_MARRIAGES)return;
        List<Faction> factions=state.factions();for(int i=0;i<factions.size();i++)for(int j=i+1;j<factions.size();j++){
            Faction a=factions.get(i),b=factions.get(j);DiplomaticRelation rel=a.relations().get(b.id());if(rel==null||rel.status()==RelationStatus.WAR||rel.status()==RelationStatus.HOSTILE||rel.opinion()<30)continue;if(state.diplomaticMarriages().stream().anyMatch(m->m.active()&&((m.factionA()==a.id()&&m.factionB()==b.id())||(m.factionA()==b.id()&&m.factionB()==a.id()))))continue;if(!rng.chance(.04))continue;
            SocialCitizen ca=eligiblePoliticalPartner(state,a),cb=eligiblePoliticalPartner(state,b);if(ca==null||cb==null)continue;
            boolean hostA=isDynasticCourtMember(state,a,ca)||(!isDynasticCourtMember(state,b,cb)&&a.government().legitimacy()>=b.government().legitimacy());SocialCitizen host=hostA?ca:cb,guest=hostA?cb:ca;Faction hostFaction=hostA?a:b;if(!relocateCourtSpouse(state,hostFaction,host,guest))continue;
            ca.relationship("citizen:"+cb.id()).setFamilyBond(FamilyBond.PARTNER);cb.relationship("citizen:"+ca.id()).setFamilyBond(FamilyBond.PARTNER);ca.relationship("citizen:"+cb.id()).adjust(.12,0,.32,0,.18);cb.relationship("citizen:"+ca.id()).adjust(.12,0,.32,0,.18);
            DiplomaticMarriage marriage=new DiplomaticMarriage(state.nextId(),day,ca.id(),cb.id(),a.id(),b.id());state.addDiplomaticMarriage(marriage);a.relationWith(b.id()).adjust(7);b.relationWith(a.id()).adjust(7);state.history().add(new WorldEvent(day,"political_marriage","marriage="+marriage.id()+", factions="+a.id()+"/"+b.id()+", citizens="+ca.id()+"/"+cb.id()+", court="+hostFaction.id()));
        }
    }

    private static boolean relocateCourtSpouse(SimulationState state,Faction hostFaction,SocialCitizen host,SocialCitizen guest){
        HouseholdState hostHouse=state.findHousehold(host.householdId()).filter(HouseholdState::active).orElse(null);if(hostHouse==null||hostHouse.memberIds().size()>=HouseholdState.MAX_NAMED_MEMBERS)return false;
        Settlement destination=state.findSettlement(hostHouse.settlementId()).orElse(null);Settlement source=state.findSettlement(guest.settlementId()).orElse(null);if(destination==null)return false;
        state.findHousehold(guest.householdId()).ifPresent(h->h.removeMember(guest.id()));if(source!=null&&source.id()!=destination.id()&&source.population()>1){source.addPopulation(-1);destination.addPopulation(1);}
        guest.migrateTo(hostFaction.id(),destination.id());guest.setHouseholdId(hostHouse.id());hostHouse.addMember(guest.id());return true;
    }
    private static boolean isDynasticCourtMember(SimulationState state,Faction faction,SocialCitizen citizen){DynastyState d=state.dynasties().get(faction.id());return d!=null&&(d.rulerCitizenId()==citizen.id()||d.heirCitizenId()==citizen.id());}
    private static SocialCitizen eligiblePoliticalPartner(SimulationState state,Faction faction){DynastyState dynasty=state.dynasties().get(faction.id());List<SocialCitizen> candidates=state.socialCitizens().stream().filter(SocialCitizen::alive).filter(c->c.factionId()==faction.id()).filter(c->c.ageYears(state.clock().day())>=18&&c.ageYears(state.clock().day())<=55).filter(c->!hasLivingPartner(state,c)).filter(c->c.role()==CitizenRole.OFFICIAL||c.role()==CitizenRole.PRIEST||dynasty!=null&&c.householdId()>0&&state.findHousehold(c.householdId()).map(h->h.memberIds().contains(dynasty.rulerCitizenId())).orElse(false)).sorted(Comparator.comparingLong(SocialCitizen::id)).toList();return candidates.isEmpty()?null:candidates.getFirst();}

    private static void simulatePiracy(SimulationState state,DeterministicRng rng){
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
            if(band.strength()<3||band.morale()<.12){band.disband();state.history().add(new WorldEvent(day,"pirate_band_disbanded","pirates="+band.id()+", reason=collapse"));}
        }
    }

    private static boolean resolveFleetPirateEngagement(SimulationState state,PirateBand band,DeterministicRng rng){
        Fleet fleet=state.fleets().stream().filter(f->!f.destroyed()&&f.readiness()>.18&&f.mission()!=NavalMission.RETURN_TO_PORT).filter(f->f.position().distanceTo(band.position())<170).min(Comparator.comparingDouble(f->f.position().distanceTo(band.position()))).orElse(null);if(fleet==null)return false;
        double fleetPower=Math.max(.5,fleet.combatPower()),piratePower=Math.max(.5,band.strength()*(.55+.7*band.morale()));double total=fleetPower+piratePower;double pirateLoss=Mathx.clamp((fleetPower/total)*rng.between(.12,.42),.04,.75);double fleetLoss=Mathx.clamp((piratePower/total)*rng.between(.015,.16),.005,.28);
        int lostPirates=Math.max(1,(int)Math.round(band.strength()*pirateLoss));band.loseStrength(lostPirates);band.adjustMorale(-.08-pirateLoss*.25);fleet.loseFraction(fleetLoss);fleet.gainExperience(.008+.012*pirateLoss);
        state.history().add(new WorldEvent(state.clock().day(),"anti_piracy_action","fleet="+fleet.id()+", pirates="+band.id()+", pirateLoss="+lostPirates+", fleetShips="+fleet.totalShips()));
        if(!band.active()||band.strength()<3||band.morale()<.10){PirateHideout hideout=state.findPirateHideoutByBand(band.id()).orElse(null);double recovered=band.takeLoot(band.loot());if(hideout!=null&&hideout.discoveredByFactionId()==fleet.factionId()){recovered+=hideout.takeLoot(hideout.storedLoot());hideout.destroy();}double payout=recovered*.65;state.findFaction(fleet.factionId()).ifPresent(f->f.addTreasury(payout));band.disband();state.history().add(new WorldEvent(state.clock().day(),"pirate_band_destroyed","pirates="+band.id()+", fleet="+fleet.id()+", recovered="+Math.round(recovered*.65)));return true;}
        if(fleet.destroyed())return false;
        if(fleetPower>piratePower*1.25)band.moveToward(band.position().lerp(fleet.position(),-1),70);
        return false;
    }

    private static SimPosition pirateHideoutPosition(SimulationState state,Settlement origin,SimPosition port,long bandId){double a=((mix(state.seed()^bandId)&0xffff)/65535.0)*Math.PI*2.0;double r=180+((mix(bandId^0x5DEECE66DL)>>>11)*0x1.0p-53)*260;SimPosition candidate=new SimPosition(port.x()+Math.cos(a)*r,port.z()+Math.sin(a)*r);return candidate.distanceTo(origin.position())<120?new SimPosition(origin.position().x()+Math.cos(a)*160,origin.position().z()+Math.sin(a)*160):candidate;}
    private static Faction nearestAntiPiracyFaction(SimulationState state,PirateHideout hideout){return state.factions().stream().filter(f->f.id()!=state.findSettlementOwner(hideout.originSettlementId()).map(Faction::id).orElse(-1L)).filter(f->antiPiracyPressure(state,f,hideout.position())>.2).min(Comparator.comparingDouble(f->f.settlements().stream().mapToDouble(s->s.position().distanceTo(hideout.position())).min().orElse(Double.MAX_VALUE))).orElse(null);}
    private static double antiPiracyPressure(SimulationState state,Faction faction,SimPosition position){double fleet=state.fleets().stream().filter(f->f.factionId()==faction.id()&&!f.destroyed()).filter(f->f.position().distanceTo(position)<650).mapToDouble(Fleet::readiness).max().orElse(0);double local=faction.settlements().stream().filter(s->s.position().distanceTo(position)<500).mapToDouble(s->s.publicOrder()*.65+s.infrastructure()*.35).max().orElse(0);return Mathx.clamp(fleet*.7+local*.3,0,1);}

    private static boolean waterTradeCandidate(SimulationState state,TradeShipment shipment){return state.ports().stream().anyMatch(p->p.operational()&&(p.position().distanceTo(shipment.origin())<500||p.position().distanceTo(shipment.destination())<500));}

    private static void refreshDynasties(SimulationState state){
        long day=state.clock().day();for(Faction faction:state.factions()){
            DynastyState dynasty=state.dynasties().get(faction.id());if(dynasty==null)continue;SocialCitizen ruler=state.findSocialCitizen(dynasty.rulerCitizenId()).orElse(null);if(ruler==null||!ruler.alive()){dynasty.startCrisis(day);}else{long heir=findHeir(state,ruler);dynasty.setHeirCitizenId(heir);if(heir>0)dynasty.endCrisis();else if(faction.government().successionLaw()==SuccessionLaw.HEREDITARY)dynasty.startCrisis(day);}
            if(dynasty.successionCrisis()){
                long duration=Math.max(0,day-dynasty.crisisSinceDay());
                faction.government().adjustLegitimacy(-.0025-Math.min(.004,duration/4000.0));
                faction.government().adjustStability(-.0018);
                dynasty.adjustPrestige(-.004);
                for(Settlement s:faction.settlements())s.adjustUnrest(.002+(duration>90?.003:0));
                if(duration>0&&duration%30==0)state.history().add(new WorldEvent(day,"succession_crisis_ongoing","faction="+faction.id()+", house="+dynasty.houseName()+", days="+duration));
            }
            double prestigeDelta=(faction.government().legitimacy()-.5)*.02-faction.settlements().stream().mapToDouble(Settlement::unrest).average().orElse(0)*.01;dynasty.adjustPrestige(prestigeDelta);
        }
    }
    private static long findHeir(SimulationState state,SocialCitizen ruler){
        long named=ruler.relationships().entrySet().stream().filter(e->e.getValue().familyBond()==FamilyBond.CHILD||e.getValue().familyBond()==FamilyBond.ADOPTED_CHILD).mapToLong(e->parseCitizenKey(e.getKey())).filter(id->state.findSocialCitizen(id).filter(SocialCitizen::alive).isPresent()).boxed().min(Comparator.comparingLong(id->state.findSocialCitizen(id).orElseThrow().birthDay())).orElse(0L);
        if(named>0)return named;
        return state.households().stream().flatMap(h->h.children().stream()).filter(child->child.parentAId()==ruler.id()||child.parentBId()==ruler.id()||child.adoptiveParentAId()==ruler.id()||child.adoptiveParentBId()==ruler.id()).min(Comparator.comparingLong(DependentChild::birthDay).thenComparingLong(DependentChild::id)).map(DependentChild::id).orElse(0L);
    }

    private static void evolveCultureAndLaw(SimulationState state){
        for(Faction faction:state.factions()){
            FactionCivilizationState c=state.ensureFactionCivilization(faction.id());double trade=faction.relations().values().stream().filter(DiplomaticRelation::tradeAgreement).count()/Math.max(1.0,faction.relations().size());double war=state.wars().stream().anyMatch(w->w.active()&&(w.attackerFactionId()==faction.id()||w.defenderFactionId()==faction.id()))?1:0;double farm=faction.settlements().stream().filter(s->has(s,"farm:")).count()/Math.max(1.0,faction.settlements().size());double art=faction.settlements().stream().filter(s->has(s,"monument:")||has(s,"temple:")).count()/Math.max(1.0,faction.settlements().size());c.setCultureTraits(lerp(c.mercantileTradition(),trade,.04),lerp(c.martialTradition(),war,.025),lerp(c.agrarianTradition(),farm,.03),lerp(c.artisticTradition(),art,.025),c.religiousTolerance(),lerp(c.openness(),trade,.02));double unrest=faction.settlements().stream().mapToDouble(Settlement::unrest).average().orElse(0);c.setLawPolicy(lerp(c.lawSeverity(),Mathx.clamp(faction.government().lawEnforcement()*.7+unrest*.3,0,1),.03),lerp(c.dueProcess(),Mathx.clamp(.35+c.education()*.45-faction.government().corruption()*.25,0,1),.03),lerp(c.refugeeAcceptance(),Mathx.clamp(.65+c.openness()*.25-unrest*.4,0,1),.03));
        }
    }

    private static MigrationReason migrationReason(Settlement s,Faction owner,SettlementCivilizationState c){
        // War: nearby armies OR active war targeting this settlement / involving the realm.
        boolean warNear=owner.armies().stream().anyMatch(a->a.position().distanceTo(s.position())<300);
        // Note: SimulationState is available via considerMigration callers; war check uses refugee pressure + armies here,
        // and open warfare is injected through refugeePressure by CivilizationEngine.updateSettlements.
        if((c.refugeePressure()>.55&&warNear)||c.banditPressure()>.72)return MigrationReason.WAR;
        if(s.foodSecurity()<.38||s.stockpile().get(ResourceType.FOOD)<s.population()*.6)return MigrationReason.FAMINE;
        if(c.diseasePressure()>.65)return MigrationReason.DISEASE;
        if(owner.government().taxRate()>.36&&s.unrest()>.35)return MigrationReason.TAXATION;
        // Persecution: severe law + heavy tax when unrest is high (culture intolerance proxy without extra state).
        if(s.unrest()>.42&&owner.government().lawEnforcement()>.72&&owner.government().taxRate()>.28)return MigrationReason.PERSECUTION;
        if(s.housingShortage()>Math.max(12,s.population()/8))return MigrationReason.HOUSING;
        if(s.employment()<.34)return MigrationReason.WORK;
        // Family reunification: low local cohesion with strong household ties elsewhere is modeled as FAMILY.
        if(c.culturalCohesion()<.34&&s.prosperity()<.42&&s.unrest()>.28)return MigrationReason.FAMILY;
        if(c.refugeePressure()>.48)return MigrationReason.OPPORTUNITY;
        return null;
    }

    /** Monthly wage payout from local prosperity into named citizens' purses (skill-scaled). */
    private static void payWages(SimulationState state){
        for(Faction faction:state.factions()){
            for(Settlement settlement:faction.settlements()){
                double employment=settlement.employment();
                if(employment<.12)continue;
                double purse=settlement.prosperity()*.8+faction.treasury()*.0004;
                List<SocialCitizen> workers=state.socialCitizens().stream().filter(SocialCitizen::alive)
                        .filter(c->c.settlementId()==settlement.id())
                        .sorted(Comparator.comparingLong(SocialCitizen::id)).toList();
                if(workers.isEmpty())continue;
                double wagePool=Math.min(purse,settlement.population()*.002*employment);
                if(wagePool<=0)continue;
                double paid=0;
                for(SocialCitizen c:workers){
                    double roleMul=switch(c.role()){
                        case OFFICIAL,GUARD -> 1.35;
                        case TRADER,ARTISAN,SCHOLAR,TEACHER,HEALER -> 1.20;
                        case FARMER,MINER,LUMBERJACK,FISHER,BUTCHER,CARPENTER,BUILDER,DOCKWORKER,SAILOR -> 1.0;
                        case PRIEST -> .85;
                        default -> .75;
                    };
                    double guildMul=GuildRank.guildedRole(c.role())?GuildRank.of(c).wageMultiplier():1.0;
                    double wage=Math.max(.05,wagePool/workers.size()*roleMul*guildMul*(.55+.9*c.professionSkill())*employment);
                    if(faction.treasury()<wage*.15&&settlement.stockpile().get(ResourceType.GOLD)<wage)continue;
                    double fromLocal=settlement.stockpile().take(ResourceType.GOLD,wage*.35);
                    double need=wage-fromLocal;
                    if(need>0)faction.addTreasury(-Math.min(faction.treasury(),need));
                    c.addMoney(wage);c.practiceProfession(.01);paid+=wage;
                    // Household purse receives a share so family consumption is not only prosperity drift.
                    if(c.householdId()>0)state.findHousehold(c.householdId()).ifPresent(h->h.adjustWealth(wage*.45));
                }
                if(paid>0)state.history().add(new WorldEvent(state.clock().day(),"wages_paid","settlement="+settlement.id()+", workers="+workers.size()+", paid="+Math.round(paid)));
            }
        }
    }
    private static boolean acceptableMigrationTarget(SimulationState state,Faction sourceOwner,Settlement target){Faction owner=state.findSettlementOwner(target.id()).orElse(null);if(owner==null)return false;if(owner.id()==sourceOwner.id())return true;DiplomaticRelation rel=sourceOwner.relations().get(owner.id());return rel==null||rel.status()==RelationStatus.NEUTRAL||rel.status()==RelationStatus.FRIENDLY||rel.status()==RelationStatus.ALLIED;}
    private static double migrationScore(SimulationState state,Settlement s){Faction owner=state.findSettlementOwner(s.id()).orElse(null);if(owner==null)return 0;SettlementCivilizationState c=state.ensureSettlementCivilization(s.id(),owner.id());double housing=Mathx.clamp((s.housing()-s.population()+20)/(double)Math.max(20,s.population()),0,1);return s.foodSecurity()*.28+s.publicOrder()*.23+s.prosperity()*.16+housing*.15+(1-c.diseasePressure())*.12+owner.government().stability()*.06;}
    private static CitizenRole chooseProfession(Settlement s,long id){List<CitizenRole> roles=new ArrayList<>(List.of(CitizenRole.FARMER,CitizenRole.BUILDER,CitizenRole.TRADER,CitizenRole.GUARD,CitizenRole.BUTCHER));if(has(s,"mine:"))roles.add(CitizenRole.MINER);if(has(s,"lumber_camp:")){roles.add(CitizenRole.LUMBERJACK);roles.add(CitizenRole.CARPENTER);}if(has(s,"fishery:")){roles.add(CitizenRole.FISHER);roles.add(CitizenRole.SAILOR);}if(has(s,"dock:")){roles.add(CitizenRole.DOCKWORKER);roles.add(CitizenRole.SAILOR);}if(has(s,"workshop:"))roles.add(CitizenRole.ARTISAN);if(has(s,"clinic:"))roles.add(CitizenRole.HEALER);if(has(s,"temple:"))roles.add(CitizenRole.PRIEST);if(has(s,"school:")){roles.add(CitizenRole.SCHOLAR);roles.add(CitizenRole.TEACHER);}if(has(s,"warehouse:")){roles.add(CitizenRole.DOCKWORKER);}return roles.get(Math.floorMod(Long.hashCode(id*31),roles.size()));}
    private static int virtualSlot(SimulationState state,long settlementId,long id){int slot=60_000+Math.floorMod(Long.hashCode(id),39_000);Set<Integer> used=new HashSet<>();for(SocialCitizen c:state.socialCitizens())if(c.settlementId()==settlementId)used.add(c.projectionSlot());while(used.contains(slot)&&slot<99_999)slot++;if(used.contains(slot)){slot=59_999;while(slot>1&&used.contains(slot))slot--;}return slot;}
    private static boolean hasLivingPartner(SimulationState state,SocialCitizen c){return c.relationships().entrySet().stream().filter(e->e.getValue().familyBond()==FamilyBond.PARTNER).mapToLong(e->parseCitizenKey(e.getKey())).anyMatch(id->state.findSocialCitizen(id).filter(SocialCitizen::alive).isPresent());}
    private static boolean hasHouseholdPartner(SocialCitizen c,List<SocialCitizen> householdAdults){return c.relationships().entrySet().stream().filter(e->e.getValue().familyBond()==FamilyBond.PARTNER).mapToLong(e->parseCitizenKey(e.getKey())).anyMatch(id->householdAdults.stream().anyMatch(other->other.id()==id&&other.alive()));}
    private static long parseCitizenKey(String key){if(key==null||!key.startsWith("citizen:"))return 0;try{return Long.parseLong(key.substring(8));}catch(NumberFormatException ignored){return 0;}}
    private static String houseName(Faction faction){String clean=faction.name().replaceAll("[^A-Za-z]","");if(clean.isBlank())clean="Realm";return "House "+clean.substring(0,Math.min(8,clean.length()));}
    private static Settlement nearestSettlement(SimulationState state,SimPosition p,double max){Settlement best=null;double d=max;for(Faction f:state.factions())for(Settlement s:f.settlements()){double x=s.position().distanceTo(p);if(x<d){d=x;best=s;}}return best;}
    private static double specialistShare(SimulationState state,long settlementId,CitizenRole role){long total=0;double weighted=0;for(SocialCitizen c:state.socialCitizens())if(c.alive()&&c.settlementId()==settlementId){total++;if(c.role()==role)weighted+=.45+.55*c.professionSkill();}return total==0?0:weighted/total;}
    private static boolean has(Settlement s,String prefix){return s.completedConstruction().stream().anyMatch(k->k.startsWith(prefix));}
    private static boolean hasAny(Faction faction,String prefix){for(Settlement s:faction.settlements())if(has(s,prefix))return true;return false;}
    private static long mix(long z){z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;return z^(z>>>31);} private static double unit(long h,int shift){long z=mix(h^(shift*0x9E3779B97F4A7C15L));return (z>>>11)*0x1.0p-53;} private static double lerp(double a,double b,double r){return Mathx.clamp(a+(b-a)*r,0,1);}
}
