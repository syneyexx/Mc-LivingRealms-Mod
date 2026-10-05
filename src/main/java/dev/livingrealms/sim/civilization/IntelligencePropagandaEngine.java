package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.law.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.util.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Espionage operations and propaganda campaigns.
 */
public final class IntelligencePropagandaEngine {
    private IntelligencePropagandaEngine() {}

    public static void simulateIntelligenceOperations(SimulationState state,DeterministicRng rng){
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

    public static void simulatePropagandaCampaigns(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();
        for(PropagandaCampaign campaign:state.propagandaCampaigns())if(campaign.active()){
            Faction faction=state.findFaction(campaign.factionId()).orElse(null);if(faction==null||day>campaign.endDay()){campaign.finish();continue;}List<Settlement> targets=campaign.settlementId()>0?faction.settlements().stream().filter(s->s.id()==campaign.settlementId()).toList():faction.settlements();if(targets.isEmpty()){campaign.finish();continue;}
            FactionCivilizationState fc=state.ensureFactionCivilization(faction.id());double capacity=Mathx.clamp(.25+.35*fc.culturalInfluence()+.25*fc.education()+.15*(1-faction.government().corruption()),0,1);campaign.setReach(capacity);double effect=.0007*campaign.intensity()*campaign.reach();for(Settlement s:targets){SettlementCivilizationState sc=state.ensureSettlementCivilization(s.id(),faction.id());switch(campaign.theme()){case UNITY->{s.adjustUnrest(-effect);sc.adjustCohesion(effect*.8);}case WAR_EFFORT->{for(Army a:faction.armies())a.adjustMorale(effect*.4);s.adjustUnrest(-effect*.35);}case LEGITIMACY->{faction.government().adjustLegitimacy(effect*.35);s.adjustUnrest(-effect*.45);}case RELIGION->{sc.adjustCohesion(effect*.6);fc.approach(fc.culturalInfluence(),Math.min(1,fc.religiousInfluence()+effect),fc.education(),fc.propaganda(),fc.intelligence(),.001);}case ANTI_BANDIT->{sc.adjustBanditPressure(-effect*.8);s.setPublicOrder(s.publicOrder()+effect*.25);}case RECONSTRUCTION->{s.adjustProsperity(effect*.45);sc.adjustCohesion(effect*.35);}}}
            fc.approach(fc.culturalInfluence(),fc.religiousInfluence(),fc.education(),Math.min(1,fc.propaganda()+effect*3),fc.intelligence(),.003);double cost=Math.min(faction.treasury(),.18*campaign.intensity()*Math.max(1,targets.stream().mapToInt(Settlement::population).sum()/100.0));faction.addTreasury(-cost);
            if(day>=campaign.endDay()){campaign.finish();state.history().add(new WorldEvent(day,"propaganda_campaign_ended","campaign="+campaign.id()+", faction="+faction.id()+", reach="+String.format(Locale.ROOT,"%.2f",campaign.reach())));}
        }
        if(day<=0||day%30!=0)return;
        for(Faction faction:state.factions()){
            if(state.propagandaCampaigns().stream().anyMatch(c->c.active()&&c.factionId()==faction.id()))continue;double unrest=faction.settlements().stream().mapToDouble(Settlement::unrest).average().orElse(0);boolean war=state.wars().stream().anyMatch(w->w.active()&&w.involves(faction.id()));double bandits=faction.settlements().stream().mapToDouble(s->state.ensureSettlementCivilization(s.id(),faction.id()).banditPressure()).average().orElse(0);FactionCivilizationState fc=state.ensureFactionCivilization(faction.id());double infraStress=faction.settlements().stream().mapToDouble(s->1-Math.min(1,s.infrastructure())).average().orElse(0);PropagandaTheme theme=null;if(war)theme=PropagandaTheme.WAR_EFFORT;else if(unrest>.38)theme=PropagandaTheme.UNITY;else if(faction.government().legitimacy()<.42)theme=PropagandaTheme.LEGITIMACY;else if(bandits>.30)theme=PropagandaTheme.ANTI_BANDIT;else if(fc.religiousInfluence()>.62&&CivilizationSupport.hasAny(faction,"temple:"))theme=PropagandaTheme.RELIGION;else if(infraStress>.45)theme=PropagandaTheme.RECONSTRUCTION;if(theme==null)continue;Settlement focus=faction.settlements().stream().max(Comparator.comparingDouble(Settlement::unrest)).orElse(null);double intensity=Mathx.clamp(.35+unrest*.35+(war?.18:0)+rng.between(-.04,.04),.25,.9);PropagandaCampaign campaign=new PropagandaCampaign(state.nextId(),faction.id(),focus==null?0:focus.id(),day,day+29,theme,intensity);state.addPropagandaCampaign(campaign);state.history().add(new WorldEvent(day,"propaganda_campaign_started","campaign="+campaign.id()+", faction="+faction.id()+", theme="+theme));
        }
    }
}
