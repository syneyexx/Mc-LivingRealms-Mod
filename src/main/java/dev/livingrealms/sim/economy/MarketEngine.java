package dev.livingrealms.sim.economy;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.util.Mathx;
import java.util.*;

/** Scarcity-driven strategic pricing used by trade contracts and UIs. */
public final class MarketEngine {
    private static final EnumMap<ResourceType,Double> BASE=new EnumMap<>(ResourceType.class);
    static {
        BASE.put(ResourceType.FOOD,.45);BASE.put(ResourceType.WOOD,.65);BASE.put(ResourceType.STONE,.55);BASE.put(ResourceType.IRON,1.8);
        BASE.put(ResourceType.COAL,1.2);BASE.put(ResourceType.COPPER,1.5);BASE.put(ResourceType.GOLD,8.0);BASE.put(ResourceType.FUEL,2.2);
        BASE.put(ResourceType.AMMUNITION,4.5);BASE.put(ResourceType.TOOLS,3.2);BASE.put(ResourceType.MACHINERY,12.0);BASE.put(ResourceType.TEXTILES,2.0);
        BASE.put(ResourceType.GRAIN,.38);BASE.put(ResourceType.FLOUR,.42);BASE.put(ResourceType.BREAD,.55);
        BASE.put(ResourceType.MEAT,.70);BASE.put(ResourceType.ALE,.48);BASE.put(ResourceType.WOOL,.90);
    }
    private MarketEngine(){}

    public static double basePrice(ResourceType resource){return BASE.getOrDefault(resource,1.0);}

    public static MarketQuote quote(Faction faction,ResourceType resource){
        int pop=Math.max(1,faction.population());
        double dailyNeed=localDailyNeed(pop,resource);
        double supply=faction.stockpile().get(resource);
        // Realm quotes see treasury + all settlement barns/granaries.
        for(Settlement s:faction.settlements())supply+=s.stockpile().get(resource);
        double days=dailyNeed<=0?999:supply/dailyNeed;
        double scarcity=Mathx.clamp(1.4-Math.log1p(days)/Math.log(8),.2,2.5);
        double prosperity=faction.settlements().stream().mapToDouble(Settlement::prosperity).average().orElse(.5);
        double price=basePrice(resource)*scarcity*(.88+.24*prosperity)*(1+faction.government().corruption()*.18);
        return new MarketQuote(resource,Math.max(.01,price),days,scarcity);
    }
    public static double unitPrice(Faction faction,ResourceType resource){return quote(faction,resource).unitPrice();}
    public static Map<ResourceType,MarketQuote> all(Faction faction){EnumMap<ResourceType,MarketQuote> out=new EnumMap<>(ResourceType.class);for(ResourceType r:ResourceType.values())out.put(r,quote(faction,r));return Collections.unmodifiableMap(out);}
    public static double localDailyNeed(int pop,ResourceType r){return switch(r){
        case FOOD,BREAD -> pop*.20;
        case GRAIN,FLOUR -> pop*.16;
        case MEAT -> pop*.05;
        case ALE -> pop*.02;
        case TEXTILES,WOOL -> pop*.002;
        case TOOLS -> pop*.0012;
        case FUEL -> pop*.0008;
        case AMMUNITION -> pop*.00015;
        case WOOD -> pop*.002;
        case STONE -> pop*.001;
        default -> Math.max(1,pop*.0004);
    };}
    public static double localDaysOfSupply(Settlement settlement,ResourceType resource){
        double need=localDailyNeed(Math.max(1,settlement.population()),resource);
        if(need<=0)return 999;
        return settlement.stockpile().get(resource)/need;
    }
}
