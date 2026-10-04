package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.*;

/**
 * Elevates named citizens into legends from measured deeds — never random spawns.
 * Watches battle memories, elite mastery, rulership and major assistance.
 */
public final class HeroEngine {
    public void simulateDay(SimulationState state){
        long day=state.clock().day();
        for(SocialCitizen citizen:state.socialCitizens()){
            if(!citizen.alive()){onDeath(state,citizen);continue;}
            double score=deedScore(state,citizen);
            if(score<.55)continue;
            upsertLegend(state,citizen,score,day);
        }
    }

    private static void onDeath(SimulationState state,SocialCitizen citizen){
        String key="citizen:"+citizen.id();
        LegendRecord legend=state.legends().stream().filter(l->l.subjectKey().equals(key)).findFirst().orElse(null);
        if(legend==null){
            double score=deedScore(state,citizen);
            if(score<.7&&citizen.socialClass()!=SocialClass.ELITE)return;
            legend=new LegendRecord(state.nextId(),state.clock().day(),citizen.factionId(),citizen.settlementId(),key,citizen.name(),epitaph(citizen,score),Math.min(1,score));
            if(state.legends().size()<SimulationState.MAX_LEGENDS)state.addLegend(legend);
            else return;
        }
        if(legend.renown()>=.65&&!legend.monumented()){
            legend.markMonumented();
            state.history().add(new WorldEvent(state.clock().day(),"hero_monumented","citizen="+citizen.id()+", name="+citizen.name()+", renown="+String.format(java.util.Locale.ROOT,"%.2f",legend.renown())));
        }
    }

    private static void upsertLegend(SimulationState state,SocialCitizen citizen,double score,long day){
        String key="citizen:"+citizen.id();
        Optional<LegendRecord> existing=state.legends().stream().filter(l->l.subjectKey().equals(key)).findFirst();
        if(existing.isPresent()){existing.get().addRenown((score-existing.get().renown())*.05);return;}
        if(state.legends().size()>=SimulationState.MAX_LEGENDS)return;
        state.addLegend(new LegendRecord(state.nextId(),day,citizen.factionId(),citizen.settlementId(),key,citizen.name(),epitaph(citizen,score),Math.min(.85,score*.9)));
        state.history().add(new WorldEvent(day,"hero_recognized","citizen="+citizen.id()+", name="+citizen.name()));
    }

    static double deedScore(SimulationState state,SocialCitizen citizen){
        double score=0;
        long battles=citizen.memories().stream().filter(m->m.type()==MemoryType.WAR_NEWS||(m.type()==MemoryType.LOCAL_EVENT&&m.summary()!=null&&m.summary().toLowerCase(Locale.ROOT).contains("battle"))).count();
        score+=Math.min(.35,battles*.08);
        if(citizen.professionSkill()>.78&&(citizen.socialClass()==SocialClass.ELITE||citizen.socialClass()==SocialClass.PROFESSIONAL))score+=.28;
        DynastyState dynasty=state.dynasties().get(citizen.factionId());
        if(dynasty!=null&&(dynasty.rulerCitizenId()==citizen.id()||dynasty.heirCitizenId()==citizen.id()))score+=.3;
        long helped=citizen.memories().stream().filter(m->m.type()==MemoryType.HELPED_BY||(m.type()==MemoryType.LOCAL_EVENT&&m.summary()!=null&&m.summary().toLowerCase(Locale.ROOT).contains("assist"))).count();
        score+=Math.min(.25,helped*.06);
        score+=citizen.personalInfluence()*.15;
        if(citizen.role()==dev.livingrealms.sim.civilian.CitizenRole.GUARD&&citizen.professionSkill()>.7)score+=.08;
        return Math.min(1,score);
    }

    private static String epitaph(SocialCitizen citizen,double score){
        if(score>.8)return citizen.name()+" whose deeds reshaped "+citizen.role().name().toLowerCase(Locale.ROOT)+" life in the realm.";
        if(citizen.socialClass()==SocialClass.ELITE)return citizen.name()+", remembered among the elite for lasting service.";
        return citizen.name()+" earned lasting renown through hardship and duty.";
    }
}
