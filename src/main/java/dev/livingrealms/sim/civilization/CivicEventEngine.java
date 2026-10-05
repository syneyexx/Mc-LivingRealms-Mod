package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.util.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Civic festivals/rituals and holy-order presence effects.
 */
public final class CivicEventEngine {
    private CivicEventEngine() {}

    public static void simulateCivicEvents(SimulationState state,DeterministicRng rng){
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
            boolean culturalSite=CivilizationSupport.has(settlement,"temple:")||CivilizationSupport.has(settlement,"tavern:")||CivilizationSupport.has(settlement,"market:");if(!culturalSite)continue;
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
            if(coronation&&CivilizationSupport.has(settlement,"keep:"))type=CivicEventType.CORONATION;
            else if(mourning&&CivilizationSupport.has(settlement,"temple:"))type=CivicEventType.MOURNING;
            else if(victory&&CivilizationSupport.has(settlement,"tavern:")&&!underSiege)type=CivicEventType.VICTORY_FEAST;
            else if(wedding&&CivilizationSupport.has(settlement,"keep:")&&!underSiege)type=CivicEventType.WEDDING_FEAST;
            else if(holy&&CivilizationSupport.has(settlement,"temple:")&&!underSiege&&!epidemic)type=CivicEventType.RELIGIOUS_RITUAL;
            else if(day%30==0&&!underSiege&&!epidemic&&!(atWar&&sc.banditPressure()>.55)){
                if(season==CivilizationCalendar.Season.AUTUMN&&CivilizationSupport.has(settlement,"farm:"))type=CivicEventType.HARVEST_FESTIVAL;
                else if(CivilizationSupport.has(settlement,"temple:")&&fc.religiousInfluence()>.56)type=CivicEventType.RELIGIOUS_RITUAL;
                else if(CivilizationSupport.has(settlement,"market:")&&fc.mercantileTradition()>.55)type=CivicEventType.MARKET_FAIR;
                else if(CivilizationSupport.has(settlement,"tavern:")||CivilizationSupport.has(settlement,"market:"))type=CivicEventType.HARVEST_FESTIVAL;
            }
            if(type==null)continue;
            double intensity=Mathx.clamp(.32+.28*settlement.prosperity()+.2*sc.culturalCohesion()+rng.between(-.05,.05)+(holy?.08:0),.2,.92);
            CivicEvent event=new CivicEvent(state.nextId(),faction.id(),settlement.id(),day,day+2,type,civicTitle(type,settlement,faith),intensity);state.addCivicEvent(event);
            state.history().add(new WorldEvent(day,"civic_event_started","event="+event.id()+", type="+type+", settlement="+settlement.id()));
            for(SocialCitizen c:state.socialCitizens())if(c.alive()&&c.settlementId()==settlement.id()&&Math.floorMod(c.id()+day,5)==0)c.remember(new CitizenMemory(day,MemoryType.LOCAL_EVENT,"civic:"+event.id(),event.title(),"The community gathered for "+event.title()+".",settlement.position(),.42,1));
        }
        if(day>0&&day%30==0)applyHolyOrderPresence(state);
    }

    public static void applyHolyOrderPresence(SimulationState state){
        for(Faction faction:state.factions()){
            FactionCivilizationState fc=state.ensureFactionCivilization(faction.id());
            FaithCatalog.FaithProfile faith=FaithCatalog.of(fc.faithName());
            for(Settlement settlement:faction.settlements()){
                if(!CivilizationSupport.has(settlement,"temple:"))continue;
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

    public static String civicTitle(CivicEventType type,Settlement settlement,FaithCatalog.FaithProfile faith){return switch(type){case HARVEST_FESTIVAL->settlement.name()+" Harvest Feast";case RELIGIOUS_RITUAL->settlement.name()+" "+faith.primaryDeity()+" Rite";case VICTORY_FEAST->settlement.name()+" Victory Feast";case MOURNING->settlement.name()+" Day of Mourning";case CORONATION->settlement.name()+" Coronation";case MARKET_FAIR->settlement.name()+" Market Fair";case WEDDING_FEAST->settlement.name()+" Wedding Feast";};}
}
