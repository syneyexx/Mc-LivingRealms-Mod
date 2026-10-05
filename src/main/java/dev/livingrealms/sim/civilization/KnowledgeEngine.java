package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.util.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Knowledge diffusion, tech spillover benefits, and apprenticeship training.
 */
public final class KnowledgeEngine {
    private KnowledgeEngine() {}

    public static void diffuseKnowledge(SimulationState state){
        for(Faction faction:state.factions())for(Settlement settlement:faction.settlements()){
            SettlementCivilizationState c=state.ensureSettlementCivilization(settlement.id(),faction.id());double scholars=CivilizationSupport.specialistShare(state,settlement.id(),CitizenRole.SCHOLAR),healers=CivilizationSupport.specialistShare(state,settlement.id(),CitizenRole.HEALER),artisans=CivilizationSupport.specialistShare(state,settlement.id(),CitizenRole.ARTISAN);double base=.0008+.003*c.education();
            if(CivilizationSupport.has(settlement,"farm:"))c.adjustKnowledge(KnowledgeDomain.AGRICULTURE,base*(.9+artisans*.2));if(CivilizationSupport.has(settlement,"mine:")||CivilizationSupport.has(settlement,"workshop:"))c.adjustKnowledge(KnowledgeDomain.METALLURGY,base*(.7+artisans*.5));if(CivilizationSupport.has(settlement,"school:"))for(KnowledgeDomain d:KnowledgeDomain.values())c.adjustKnowledge(d,base*(.5+scholars));if(CivilizationSupport.has(settlement,"clinic:"))c.adjustKnowledge(KnowledgeDomain.MEDICINE,base*(1+healers));if(CivilizationSupport.has(settlement,"observatory:"))c.adjustKnowledge(KnowledgeDomain.CARTOGRAPHY,base*.9);if(CivilizationSupport.has(settlement,"factory:"))c.adjustKnowledge(KnowledgeDomain.INDUSTRY,base*(1+artisans*.4));if(CivilizationSupport.has(settlement,"dock:"))c.adjustKnowledge(KnowledgeDomain.NAVIGATION,base*.8);if(CivilizationSupport.has(settlement,"barracks:"))c.adjustKnowledge(KnowledgeDomain.MILITARY,base*.55);if(CivilizationSupport.has(settlement,"keep:")||CivilizationSupport.has(settlement,"workshop:")||CivilizationSupport.has(settlement,"wall:")||CivilizationSupport.has(settlement,"house:"))c.adjustKnowledge(KnowledgeDomain.CONSTRUCTION,base*(.75+CivilizationSupport.specialistShare(state,settlement.id(),CitizenRole.BUILDER)*.45));if(faction.name().equals(WizardTreesSeeder.FACTION_NAME))c.adjustKnowledge(KnowledgeDomain.ARCANA,base*1.5);
        }
        for(TransportRoute route:state.routes())if(route.operational()){
            Settlement a=state.findSettlement(route.fromSettlementId()).orElse(null),b=state.findSettlement(route.toSettlementId()).orElse(null);if(a==null||b==null)continue;Faction ao=state.findSettlementOwner(a.id()).orElse(null),bo=state.findSettlementOwner(b.id()).orElse(null);if(ao==null||bo==null)continue;SettlementCivilizationState ac=state.ensureSettlementCivilization(a.id(),ao.id()),bc=state.ensureSettlementCivilization(b.id(),bo.id());double rate=.003*Mathx.clamp(route.quality()*route.security(),.1,1);for(KnowledgeDomain d:KnowledgeDomain.values()){double av=ac.knowledge(d),bv=bc.knowledge(d);if(av>bv)bc.adjustKnowledge(d,(av-bv)*rate);else if(bv>av)ac.adjustKnowledge(d,(bv-av)*rate);}
        }
        if(state.clock().day()%30==0)applyKnowledgeBenefits(state);
    }

    public static void applyKnowledgeBenefits(SimulationState state){
        for(Faction faction:state.factions()){
            double weighted=0,people=0;for(Settlement s:faction.settlements()){SettlementCivilizationState c=state.ensureSettlementCivilization(s.id(),faction.id());double p=Math.max(1,s.population());double general=(c.knowledge(KnowledgeDomain.AGRICULTURE)+c.knowledge(KnowledgeDomain.METALLURGY)+c.knowledge(KnowledgeDomain.CONSTRUCTION)+c.knowledge(KnowledgeDomain.MEDICINE)+c.knowledge(KnowledgeDomain.INDUSTRY)+c.knowledge(KnowledgeDomain.NAVIGATION))/6.0;weighted+=general*p;people+=p;s.setFoodSecurity(s.foodSecurity()+.0012*(c.knowledge(KnowledgeDomain.AGRICULTURE)-.35));s.improveInfrastructure(.001*c.knowledge(KnowledgeDomain.CONSTRUCTION));}
            if(people<=0)continue;double knowledge=weighted/people;double targetTech=knowledge*1.6;if(targetTech>faction.technology())faction.advanceTechnology(Math.min(.004,(targetTech-faction.technology())*.003));
        }
    }

    public static void trainApprentices(SimulationState state){
        long day=state.clock().day();
        Map<Long,List<SocialCitizen>> bySettlement=new HashMap<>();
        for(SocialCitizen c:state.socialCitizens())if(c.alive()&&c.ageYears(day)>=16)bySettlement.computeIfAbsent(c.settlementId(),k->new ArrayList<>()).add(c);
        for(var entry:bySettlement.entrySet()){
            Settlement settlement=state.findSettlement(entry.getKey()).orElse(null);Faction owner=settlement==null?null:state.findSettlementOwner(settlement.id()).orElse(null);if(settlement==null||owner==null)continue;
            SettlementCivilizationState civ=state.ensureSettlementCivilization(settlement.id(),owner.id());boolean school=CivilizationSupport.has(settlement,"school:");Map<CitizenRole,SocialCitizen> mentorByRole=new EnumMap<>(CitizenRole.class);
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
}
