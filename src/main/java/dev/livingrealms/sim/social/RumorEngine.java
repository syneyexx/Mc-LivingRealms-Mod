package dev.livingrealms.sim.social;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.economy.LocalMarketEngine;
import dev.livingrealms.sim.economy.MarketQuote;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Bounded information diffusion through local contact, institutions and canonical trade/road routes. */
public final class RumorEngine {
    public void simulateDay(SimulationState state){
        long day=state.clock().day();
        for(SocialCitizen citizen:state.socialCitizens())if(citizen.alive()&&Math.floorMod(day+citizen.id(),3L)==0)observe(state,citizen,day);
        if(day>0&&day%7==0)seedPriceRumors(state,day);
        Map<Long,List<SocialCitizen>> bySettlement=new LinkedHashMap<>();for(SocialCitizen c:state.socialCitizens())if(c.alive())bySettlement.computeIfAbsent(c.settlementId(),k->new ArrayList<>()).add(c);
        for(var entry:bySettlement.entrySet()){List<SocialCitizen> people=entry.getValue();if(people.size()<2)continue;people.sort(Comparator.comparingLong(SocialCitizen::id));int a=Math.floorMod((int)(day+entry.getKey()),people.size());int b=(a+1+Math.floorMod((int)(day/3),people.size()-1))%people.size();share(people.get(a),people.get(b),day,.82);}
        spreadAlongRoutes(state,bySettlement,day);
    }

    /** Traders carry local scarcity prices as rumors along the road network. */
    private static void seedPriceRumors(SimulationState state,long day){
        for(SocialCitizen citizen:state.socialCitizens()){
            if(!citizen.alive()||citizen.role()!=CitizenRole.TRADER)continue;
            if(Math.floorMod(citizen.id()+day,5L)!=0)continue;
            Settlement settlement=state.findSettlement(citizen.settlementId()).orElse(null);if(settlement==null)continue;
            MarketQuote food=LocalMarketEngine.quote(settlement,ResourceType.FOOD,day);
            String subject="price:food:"+settlement.id();
            if(citizen.latestMemory(m->m.subjectKey().equals(subject)&&m.day()>=day-6).isPresent())continue;
            String tone=food.scarcity()>1.4?"dear":food.scarcity()<.7?"cheap":"fair";
            String summary="Market talk: food is "+tone+" here (about "+String.format(Locale.ROOT,"%.2f",food.unitPrice())+" with ~"+String.format(Locale.ROOT,"%.1f",food.supplyDays())+" days of supply).";
            citizen.remember(new CitizenMemory(day,MemoryType.RUMOR,subject,"market stall",summary,settlement.position(),.48,.78));
            if(food.scarcity()>1.6||food.scarcity()<.55){
                state.history().add(new WorldEvent(day,"price_rumor","settlement="+settlement.id()+", resource=FOOD, price="+String.format(Locale.ROOT,"%.3f",food.unitPrice())+", scarcity="+String.format(Locale.ROOT,"%.2f",food.scarcity())+", faction="+citizen.factionId()));
            }
        }
    }

    private static void observe(SimulationState state,SocialCitizen citizen,long day){
        List<WorldEvent> recent=state.history().recent(120);for(int i=recent.size()-1;i>=0;i--){WorldEvent e=recent.get(i);if(e.day()<day-10||e.type().equals("monthly_snapshot")||!canKnow(citizen,e))continue;String subject=subject(e);if(citizen.latestMemory(m->m.subjectKey().equals(subject)&&m.day()>=e.day()).isPresent())continue;var settlement=state.findSettlement(citizen.settlementId()).orElse(null);if(settlement==null)return;double confidence=institutional(citizen.role())?.92:professionConfidence(citizen.role(),e.type());citizen.remember(new CitizenMemory(e.day(),memoryType(e.type()),subject,institutional(citizen.role())?"official channels":"local talk",humanize(e),settlement.position(),importance(e.type()),confidence));return;}
    }

    private static boolean canKnow(SocialCitizen c,WorldEvent e){
        String m=e.message();if(refersToSettlement(m,c.settlementId())||m.contains("faction="+c.factionId())||m.contains("toFaction="+c.factionId())||m.contains("fromFaction="+c.factionId()))return true;
        String t=e.type().toLowerCase(Locale.ROOT);if(institutional(c.role()))return containsAny(t,"war","crime","bounty","treaty","faction","siege","raid","desert","migrat","epidemic","tribute","spy","assimilat","rebell","capture","assist","structure","underworld","black_market");
        return switch(c.role()){
            case TRADER -> containsAny(t,"trade","shipment","route","migrat","refugee","raid","tribute","market","price","assist","underworld","black_market");
            case HEALER -> containsAny(t,"epidemic","disease","death","demograph","assist","medical");
            case PRIEST -> containsAny(t,"festival","culture","assimilat","death","succession","legend","assist");
            case SCHOLAR -> containsAny(t,"legend","history","succession","assimilat","technology","education","astronom","cartograph","structure");
            case GUARD,OFFICIAL -> true;
            default -> containsAny(t,"assist","structure","crime","migrat");
        };
    }

    private static void spreadAlongRoutes(SimulationState state,Map<Long,List<SocialCitizen>> bySettlement,long day){
        List<TransportRoute> routes=state.routes().stream().filter(TransportRoute::operational).toList();if(routes.isEmpty())return;
        int budget=Math.min(12,routes.size());for(int i=0;i<budget;i++){TransportRoute route=routes.get(Math.floorMod((int)(day*17+i*31),routes.size()));List<SocialCitizen> from=bySettlement.getOrDefault(route.fromSettlementId(),List.of()),to=bySettlement.getOrDefault(route.toSettlementId(),List.of());if(from.isEmpty()||to.isEmpty())continue;SocialCitizen source=pickCourier(from,day+route.id());SocialCitizen target=pickReceiver(to,day+route.id()*3);if(source!=null&&target!=null)share(source,target,day,.72+.18*route.quality());if(route.mode().name().contains("ROAD")||route.mode().name().contains("RIVER")||route.mode().name().contains("SEA")){SocialCitizen reverse=pickCourier(to,day+route.id()*5);SocialCitizen receiver=pickReceiver(from,day+route.id()*7);if(reverse!=null&&receiver!=null)share(reverse,receiver,day,.68+.16*route.security());}}
    }

    private static SocialCitizen pickCourier(List<SocialCitizen> people,long salt){List<SocialCitizen> preferred=people.stream().filter(c->c.role()==CitizenRole.TRADER||c.role()==CitizenRole.OFFICIAL||c.role()==CitizenRole.GUARD).toList();List<SocialCitizen> source=preferred.isEmpty()?people:preferred;return source.isEmpty()?null:source.get(Math.floorMod((int)salt,source.size()));}
    private static SocialCitizen pickReceiver(List<SocialCitizen> people,long salt){return people.isEmpty()?null:people.get(Math.floorMod((int)salt,people.size()));}

    private static void share(SocialCitizen source,SocialCitizen target,long day,double retention){Optional<CitizenMemory> memory=source.latestMemory(m->m.type()!=MemoryType.CONVERSATION&&m.confidence()>=.32&&m.day()>=Math.max(0,day-18));if(memory.isEmpty())return;CitizenMemory m=memory.get();if(target.latestMemory(x->x.subjectKey().equals(m.subjectKey())&&x.day()>=m.day()).isPresent())return;target.remember(new CitizenMemory(day,MemoryType.RUMOR,m.subjectKey(),source.name(),m.summary(),m.position(),m.importance()*.92,m.confidence()*Math.max(.35,Math.min(.96,retention))));}

    private static boolean refersToSettlement(String message,long id){return message.contains("settlement="+id)||message.contains("from="+id)||message.contains("to="+id)||message.contains("target="+id);}
    private static boolean institutional(CitizenRole role){return role==CitizenRole.GUARD||role==CitizenRole.OFFICIAL;}
    private static double professionConfidence(CitizenRole role,String type){String t=type.toLowerCase(Locale.ROOT);if(role==CitizenRole.HEALER&&containsAny(t,"epidemic","disease"))return .88;if(role==CitizenRole.TRADER&&containsAny(t,"trade","route","shipment"))return .84;if(role==CitizenRole.SCHOLAR&&containsAny(t,"legend","history","technology"))return .83;if(role==CitizenRole.PRIEST&&containsAny(t,"festival","culture"))return .8;return .66;}
    private static MemoryType memoryType(String type){String t=type.toLowerCase(Locale.ROOT);if(containsAny(t,"war","siege","battle","raid","desert"))return MemoryType.WAR_NEWS;if(containsAny(t,"crime","bounty","arrest"))return MemoryType.CRIME_WITNESS;if(containsAny(t,"assist"))return MemoryType.HELPED_BY;return MemoryType.RUMOR;}
    private static String subject(WorldEvent e){String t=e.type().toLowerCase(Locale.ROOT);if(containsAny(t,"war","siege","battle"))return "war";if(t.contains("raid"))return "raids";if(containsAny(t,"trade","shipment","market"))return "trade";if(containsAny(t,"crime","bounty","underworld","black_market"))return "crime";if(containsAny(t,"assist"))return "aid";if(containsAny(t,"structure"))return "building";if(containsAny(t,"death","epidemic","disease"))return "health";if(containsAny(t,"migrat","refugee"))return "migration";if(containsAny(t,"festival","culture","assimilat"))return "culture";if(t.contains("tribute"))return "tribute";if(t.contains("spy"))return "politics";if(containsAny(t,"legend","memorial"))return "history";return t;}
    private static double importance(String type){String t=type.toLowerCase(Locale.ROOT);if(containsAny(t,"war","siege","rebell","conquer","capture","epidemic"))return .9;if(containsAny(t,"death","crime","bounty","raid","desert","underworld"))return .74;if(containsAny(t,"migration","tribute","spy","assimilat","assist","structure"))return .62;if(containsAny(t,"trade","faction","festival"))return .52;return .4;}
    private static String humanize(WorldEvent e){String t=e.type().replace('_',' ');return "People are talking about "+t+". "+e.message();}
    private static boolean containsAny(String text,String...parts){for(String p:parts)if(text.contains(p))return true;return false;}
}
