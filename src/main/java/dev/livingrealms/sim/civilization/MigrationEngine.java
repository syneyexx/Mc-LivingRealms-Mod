package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.util.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Migration columns, refugee camps, and household attachment/move.
 */
public final class MigrationEngine {
    private MigrationEngine() {}

    public static void considerMigration(SimulationState state){
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

    public static void advanceMigrationGroups(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();for(MigrationGroup group:state.migrationGroups())if(group.active()){
            Settlement source=state.findSettlement(group.sourceSettlementId()).orElse(null);if(source==null){group.turnBack();continue;}
            if(group.status()==MigrationStatus.CAMPED){Settlement camp=state.findSettlement(group.campSettlementId()).orElse(null);if(camp==null){group.turnBack();continue;}Faction campOwner=state.findSettlementOwner(camp.id()).orElse(null);if(campOwner!=null){SettlementCivilizationState cc=state.ensureSettlementCivilization(camp.id(),campOwner.id());camp.setDevelopmentPriority(camp.foodSecurity()<.55?DevelopmentPriority.FOOD:camp.housingShortage()>0?DevelopmentPriority.HOUSING:DevelopmentPriority.BALANCED);cc.adjustRefugeePressure(-.006);if(camp.tier().ordinal()>=Settlement.Tier.HAMLET.ordinal()||day-group.createdDay()>180){if(camp.name().startsWith("Refugee Camp "))camp.rename("Haven "+group.id());group.settle();state.history().add(new WorldEvent(day,"refugee_camp_became_settlement","group="+group.id()+", settlement="+camp.id()+", name="+camp.name()+", people="+camp.population()));}}continue;}
            Settlement target=group.targetSettlementId()>0?state.findSettlement(group.targetSettlementId()).orElse(null):null;double distance=target==null?600:Math.max(100,source.position().distanceTo(target.position()));group.advance(Mathx.clamp(95.0/distance,.03,.35));group.consume(.008+.012*(1-group.health()));
            if(group.food()<.3){Settlement aid=CivilizationSupport.nearestSettlement(state,source.position().lerp(target==null?source.position():target.position(),group.progress()),260);if(aid!=null&&aid.foodSecurity()>.68&&rng.chance(.18)){group.resupply(.10);state.history().add(new WorldEvent(day,"refugees_aided","group="+group.id()+", settlement="+aid.id()));}}
            if(group.food()<.2&&rng.chance(.08)){int lost=Math.min(group.people(),Math.max(1,group.people()/40));group.losePeople(lost);state.history().add(new WorldEvent(day,"refugee_hardship","group="+group.id()+", losses="+lost));}
            if(!group.active()||group.progress()<1)continue;
            if(target!=null&&acceptsMigration(state,group,target)){Faction owner=state.findSettlementOwner(target.id()).orElseThrow();target.addPopulation(group.people());state.ensureSettlementCivilization(target.id(),owner.id()).adjustRefugeePressure(.04+Math.min(.18,group.people()/500.0));moveAttachedHouseholds(state,group,owner.id(),target.id());group.settle();state.history().add(new WorldEvent(day,"migration_arrived","group="+group.id()+", settlement="+target.id()+", people="+group.people()+", reason="+group.reason()));}
            else establishRefugeeCamp(state,group,source);
        }
    }

    public static boolean acceptsMigration(SimulationState state,MigrationGroup group,Settlement target){Faction owner=state.findSettlementOwner(target.id()).orElse(null);if(owner==null)return false;FactionCivilizationState fc=state.ensureFactionCivilization(owner.id());SettlementCivilizationState sc=state.ensureSettlementCivilization(target.id(),owner.id());double capacity=Mathx.clamp((target.housing()-target.population()+30)/(double)Math.max(30,target.population()),0,1);double acceptance=fc.refugeeAcceptance()*.45+target.publicOrder()*.18+target.foodSecurity()*.18+capacity*.19-sc.refugeePressure()*.20;Faction origin=state.findFaction(group.originFactionId()).orElse(null);if(origin!=null&&origin.id()!=owner.id()){DiplomaticRelation rel=origin.relations().get(owner.id());if(rel!=null&&(rel.status()==RelationStatus.HOSTILE||rel.status()==RelationStatus.WAR))acceptance-=.45;}return acceptance>.42;}

    public static void establishRefugeeCamp(SimulationState state,MigrationGroup group,Settlement source){
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

    public static void attachHouseholds(SimulationState state,MigrationGroup group,long sourceSettlementId,int people){int represented=0;List<HouseholdState> candidates=state.households().stream().filter(HouseholdState::active).filter(h->h.settlementId()==sourceSettlementId).sorted(Comparator.comparingLong(HouseholdState::id)).toList();for(HouseholdState h:candidates){if(represented>=people||group.householdIds().size()>=8)break;if(group.addHousehold(h.id()))represented+=h.representedPeople();}}

    public static void moveAttachedHouseholds(SimulationState state,MigrationGroup group,long factionId,long settlementId){for(long hid:group.householdIds())state.findHousehold(hid).ifPresent(h->{h.migrate(factionId,settlementId);for(long cid:h.memberIds())state.findSocialCitizen(cid).filter(SocialCitizen::alive).ifPresent(c->{c.migrateTo(factionId,settlementId);SocialPopulationEngine.rebindAfterMigration(state,c);});});}

    public static MigrationReason migrationReason(Settlement s,Faction owner,SettlementCivilizationState c){
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

    public static boolean acceptableMigrationTarget(SimulationState state,Faction sourceOwner,Settlement target){Faction owner=state.findSettlementOwner(target.id()).orElse(null);if(owner==null)return false;if(owner.id()==sourceOwner.id())return true;DiplomaticRelation rel=sourceOwner.relations().get(owner.id());return rel==null||rel.status()==RelationStatus.NEUTRAL||rel.status()==RelationStatus.FRIENDLY||rel.status()==RelationStatus.ALLIED;}

    public static double migrationScore(SimulationState state,Settlement s){Faction owner=state.findSettlementOwner(s.id()).orElse(null);if(owner==null)return 0;SettlementCivilizationState c=state.ensureSettlementCivilization(s.id(),owner.id());double housing=Mathx.clamp((s.housing()-s.population()+20)/(double)Math.max(20,s.population()),0,1);return s.foodSecurity()*.28+s.publicOrder()*.23+s.prosperity()*.16+housing*.15+(1-c.diseasePressure())*.12+owner.government().stability()*.06;}
}
