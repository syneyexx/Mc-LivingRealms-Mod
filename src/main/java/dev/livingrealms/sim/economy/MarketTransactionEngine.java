package dev.livingrealms.sim.economy;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Atomic canonical side of a player market transaction.
 * Minecraft inventory/emerald movement is performed by the adapter only after a quote is accepted.
 */
public final class MarketTransactionEngine {
    public static final int PACKAGE_UNITS = 8;
    private static final double BUY_SPREAD = 1.08;
    private static final double SELL_SPREAD = .82;
    private static final Set<ResourceType> PLAYER_TRADABLE = EnumSet.of(
            ResourceType.FOOD, ResourceType.WOOD, ResourceType.STONE, ResourceType.IRON,
            ResourceType.COAL, ResourceType.COPPER, ResourceType.GOLD, ResourceType.FUEL,
            ResourceType.AMMUNITION, ResourceType.TEXTILES);

    public enum Side { BUY_FROM_REALM, SELL_TO_REALM }

    private MarketTransactionEngine() {}

    public static Set<ResourceType> playerTradable(){return Set.copyOf(PLAYER_TRADABLE);}

    public static Offer quote(Faction faction,ResourceType resource,Side side){
        Objects.requireNonNull(faction,"faction");Objects.requireNonNull(resource,"resource");Objects.requireNonNull(side,"side");
        if(!PLAYER_TRADABLE.contains(resource))return new Offer(resource,side,PACKAGE_UNITS,0,0,faction.stockpile().get(resource),faction.treasury(),false,"not_player_tradable");
        double unit=MarketEngine.unitPrice(faction,resource);
        double spread=side==Side.BUY_FROM_REALM?BUY_SPREAD:SELL_SPREAD;
        int emeralds=Math.max(1,side==Side.BUY_FROM_REALM?(int)Math.ceil(unit*PACKAGE_UNITS*spread):(int)Math.floor(unit*PACKAGE_UNITS*spread));
        if(side==Side.BUY_FROM_REALM&&faction.stockpile().get(resource)+1e-9<PACKAGE_UNITS)return new Offer(resource,side,PACKAGE_UNITS,emeralds,unit,faction.stockpile().get(resource),faction.treasury(),false,"insufficient_realm_stock");
        if(side==Side.SELL_TO_REALM&&faction.treasury()+1e-9<emeralds)return new Offer(resource,side,PACKAGE_UNITS,emeralds,unit,faction.stockpile().get(resource),faction.treasury(),false,"insufficient_realm_treasury");
        return new Offer(resource,side,PACKAGE_UNITS,emeralds,unit,faction.stockpile().get(resource),faction.treasury(),true,"ok");
    }

    /** Commit only after the Minecraft adapter has proven the player's physical side can succeed. */
    public static Result commit(Faction faction,Offer expected){
        Objects.requireNonNull(faction,"faction");Objects.requireNonNull(expected,"expected");
        if(Math.abs(faction.stockpile().get(expected.resource())-expected.stockAtQuote())>1e-9||Math.abs(faction.treasury()-expected.treasuryAtQuote())>1e-9){Offer current=quote(faction,expected.resource(),expected.side());return new Result(false,"quote_changed",current);}
        Offer current=quote(faction,expected.resource(),expected.side());
        if(!current.available())return new Result(false,current.reason(),current);
        if(current.units()!=expected.units()||current.emeralds()!=expected.emeralds())return new Result(false,"quote_changed",current);
        if(expected.side()==Side.BUY_FROM_REALM){
            double taken=faction.stockpile().take(expected.resource(),expected.units());
            if(Math.abs(taken-expected.units())>1e-9)return new Result(false,"stock_race",current);
            faction.addTreasury(expected.emeralds());
        }else{
            if(faction.treasury()+1e-9<expected.emeralds())return new Result(false,"treasury_race",current);
            faction.addTreasury(-expected.emeralds());
            faction.stockpile().add(expected.resource(),expected.units());
        }
        return new Result(true,"ok",current);
    }

    public record Offer(ResourceType resource,Side side,double units,int emeralds,double marketUnitPrice,double stockAtQuote,double treasuryAtQuote,boolean available,String reason){
        public Offer{Objects.requireNonNull(resource);Objects.requireNonNull(side);if(!Double.isFinite(units)||units<=0)throw new IllegalArgumentException("units");if(emeralds<0)throw new IllegalArgumentException("emeralds");if(!Double.isFinite(marketUnitPrice)||marketUnitPrice<0)throw new IllegalArgumentException("marketUnitPrice");if(!Double.isFinite(stockAtQuote)||stockAtQuote<0||!Double.isFinite(treasuryAtQuote)||treasuryAtQuote<0)throw new IllegalArgumentException("quote state");reason=reason==null?"":reason;}
    }
    public record Result(boolean success,String reason,Offer effectiveOffer){public Result{reason=reason==null?"":reason;Objects.requireNonNull(effectiveOffer);}}
}
