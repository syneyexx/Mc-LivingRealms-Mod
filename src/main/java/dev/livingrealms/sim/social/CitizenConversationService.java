package dev.livingrealms.sim.social;

import dev.livingrealms.sim.civilization.CivicEvent;
import dev.livingrealms.sim.civilization.SettlementCivilizationState;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimulationState;
import java.util.*;

/**
 * Grounded, no-LLM person-to-person conversation. Uses the same bounded memories as RumorEngine,
 * so visible conversations can genuinely transmit information without creating a second knowledge system.
 */
public final class CitizenConversationService {
    private CitizenConversationService(){}
    public static Exchange converse(SimulationState state,SocialCitizen first,SocialCitizen second){
        Objects.requireNonNull(state);Objects.requireNonNull(first);Objects.requireNonNull(second);
        if(first.id()==second.id()||!first.alive()||!second.alive())throw new IllegalArgumentException("conversation participants");
        if(first.settlementId()!=second.settlementId())throw new IllegalArgumentException("conversation settlement");
        long day=state.clock().day();
        SocialCitizen source=chooseSource(first,second,day),listener=source==first?second:first;
        Optional<CitizenMemory> candidate=source.latestMemory(m->shareable(m,day));
        boolean transferred=false;String subject="";String sourceLine,reply;
        if(candidate.isPresent()){
            CitizenMemory memory=candidate.get();subject=memory.subjectKey();
            boolean known=listener.latestMemory(m->m.subjectKey().equals(memory.subjectKey())&&m.day()>=memory.day()).isPresent();
            sourceLine=shorten(memory.summary(),118);
            if(!known){
                double confidence=Math.max(.18,memory.confidence()*.82);
                listener.remember(new CitizenMemory(day,MemoryType.RUMOR,memory.subjectKey(),source.name(),memory.summary(),memory.position(),memory.importance()*.90,confidence));
                transferred=true;reply=confidence>=.62?"I hadn't heard that. I'll keep it in mind.":"I'll remember you said that, but I'd like to hear it confirmed.";
            }else reply="I've heard something similar.";
        }else{
            Settlement settlement=state.findSettlement(first.settlementId()).orElseThrow();Faction faction=state.findFaction(first.factionId()).orElse(null);SettlementCivilizationState civ=faction==null?null:state.ensureSettlementCivilization(settlement.id(),faction.id());
            CivicEvent event=state.civicEvents().stream().filter(CivicEvent::active).filter(e->e.settlementId()==settlement.id()&&e.startDay()<=day&&e.endDay()>=day).max(Comparator.comparingDouble(CivicEvent::intensity).thenComparingLong(CivicEvent::id)).orElse(null);sourceLine=event==null?smallTalk(settlement,civ,source):"Are you going to "+event.title()+" today?";reply=event==null?smallReply(listener):(event.attendance()>.55?"Most of the town seems to be. I may go as well.":"Perhaps. It has not drawn everyone yet.");
        }
        first.relationship("citizen:"+second.id()).adjust(.006,0,0,0,.004);second.relationship("citizen:"+first.id()).adjust(.006,0,0,0,.004);
        return source==first?new Exchange(sourceLine,reply,transferred,subject):new Exchange(reply,sourceLine,transferred,subject);
    }
    private static SocialCitizen chooseSource(SocialCitizen a,SocialCitizen b,long day){long ar=latestShareableDay(a,day),br=latestShareableDay(b,day);if(ar!=br)return ar>br?a:b;return Math.floorMod(Objects.hash(a.id(),b.id(),day),2)==0?a:b;}
    private static long latestShareableDay(SocialCitizen c,long day){return c.latestMemory(m->shareable(m,day)).map(CitizenMemory::day).orElse(-1L);}
    private static boolean shareable(CitizenMemory m,long day){return m.type()!=MemoryType.CONVERSATION&&m.confidence()>=.28&&m.importance()>=.18&&m.day()>=Math.max(0,day-30)&&!m.summary().isBlank();}
    private static String smallTalk(Settlement settlement,SettlementCivilizationState civ,SocialCitizen speaker){
        if(civ!=null){if(civ.diseasePressure()>.52)return "There has been too much sickness around "+settlement.name()+" lately.";if(civ.banditPressure()>.48)return "People are still worried about the roads around "+settlement.name()+".";if(civ.waterSecurity()<.42)return "Clean water is becoming a concern here.";if(settlement.foodSecurity()<.42)return "Food stores have been tighter than people would like.";}
        return switch(speaker.role()){case FARMER->"The fields keep everyone busy this season.";case TRADER->"Trade has been keeping the market busy.";case GUARD->"The patrol has been quiet enough for now.";case HEALER->"I hope people stay healthy this week.";case SCHOLAR,TEACHER->"There is always more to learn than time to teach it.";default->"It's good to hear another familiar voice.";};
    }
    private static String smallReply(SocialCitizen listener){return switch(listener.role()){case TRADER->"We'll see what the market brings.";case GUARD->"I'll keep my eyes open.";case HEALER->"Take care of yourself.";default->"Aye. We'll manage.";};}
    private static String shorten(String value,int max){String s=value.replace('\n',' ').trim();return s.length()<=max?s:s.substring(0,Math.max(0,max-1))+"…";}
    public record Exchange(String firstLine,String secondLine,boolean transferred,String subjectKey){public Exchange{firstLine=Objects.requireNonNullElse(firstLine,"");secondLine=Objects.requireNonNullElse(secondLine,"");subjectKey=Objects.requireNonNullElse(subjectKey,"");}}
}
