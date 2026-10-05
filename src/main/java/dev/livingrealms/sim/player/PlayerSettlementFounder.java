package dev.livingrealms.sim.player;

import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.Objects;

/**
 * Creates a player-led realm that participates in the normal Living Realms simulation.
 *
 * <p>Settlements must sit at least {@link #MIN_SETTLEMENT_SPACING} blocks apart so realms have
 * real wilderness between them and inter-city travel/diplomacy is inevitable.</p>
 */
public final class PlayerSettlementFounder {
    public record Result(boolean success,String reason,long factionId,long settlementId,String realmName,String settlementName) {}

    /** Hard product floor shared with {@link dev.livingrealms.sim.world.SettlementDensitySeeder}. */
    public static final double MIN_SETTLEMENT_SPACING = SettlementDensitySeeder.MIN_SETTLEMENT_SPACING;

    private PlayerSettlementFounder(){}

    public static Result found(SimulationState state,String actorKey,String playerName,String settlementName,SimPosition position){
        Objects.requireNonNull(state,"state");Objects.requireNonNull(position,"position");
        String actor=clean(actorKey,80), player=clean(playerName,32), settlement=clean(settlementName,40);
        if(actor.length()<3)return fail("invalid_player");
        if(player.isBlank())player="Player";
        if(settlement.length()<2)return fail("settlement name must be 2-40 characters");
        PlayerStanding standing=state.playerStanding(actor);
        if(standing.isMember())return fail("leave your current faction before founding a realm");
        if(state.factions().stream().anyMatch(f->f.name().equalsIgnoreCase("Realm of "+settlement)))return fail("realm name already exists");
        if(state.factions().stream().flatMap(f->f.settlements().stream()).anyMatch(s->s.name().equalsIgnoreCase(settlement)))return fail("settlement name already exists");

        Settlement blocker=nearestBlocking(state,position);
        if(blocker!=null){
            double need=requiredSpacing(blocker);
            double dist=blocker.position().distanceTo(position);
            double remain=Math.max(1.0,need-dist);
            String dir=cardinalAway(blocker.position(),position);
            return fail("too close to "+blocker.name()
                    +" ("+Math.round(dist)+"m, need "+Math.round(need)+"m). Move ~"
                    +Math.round(remain)+"m "+dir+" into open wilderness (settlements need "
                    +Math.round(MIN_SETTLEMENT_SPACING)+"m clearance).");
        }

        long factionId=state.nextId(), settlementId=state.nextId();
        String realmName="Realm of "+settlement;
        Faction faction=new Faction(factionId,realmName,player);
        faction.restoreTreasury(2_750);
        faction.restoreTechnology(.24);
        Settlement capital=new Settlement(settlementId,settlement,position,24,48);
        faction.addSettlement(capital);
        faction.addArmy(new Army(state.nextId(),factionId,new SimPosition(position.x()+24,position.z()+18),18));
        stock(faction,ResourceType.FOOD,2_200);stock(faction,ResourceType.WOOD,1_100);stock(faction,ResourceType.STONE,1_450);
        stock(faction,ResourceType.IRON,180);stock(faction,ResourceType.TOOLS,140);stock(faction,ResourceType.TEXTILES,120);stock(faction,ResourceType.AMMUNITION,90);
        for(Faction other:state.factions()){
            double initial=initialOpinion(state.seed(),factionId,other.id());
            faction.relationWith(other.id()).adjust(initial);
            other.relationWith(factionId).adjust(initial);
        }
        state.addFaction(faction);
        standing.restoreReputation(factionId,100);
        standing.assumeRule(factionId,state.clock().day());
        state.history().add(new WorldEvent(state.clock().day(),"player_realm_founded",player+" founded "+realmName+" at "+settlement));
        return new Result(true,"ok",factionId,settlementId,realmName,settlement);
    }

    /** Nearest settlement that still blocks founding at {@code position}, or null if clear. */
    public static Settlement nearestBlocking(SimulationState state,SimPosition position){
        Settlement best=null;double bestSlack=Double.POSITIVE_INFINITY;
        for(Faction faction:state.factions()){
            for(Settlement settlement:faction.settlements()){
                double need=requiredSpacing(settlement);
                double dist=settlement.position().distanceTo(position);
                if(dist>=need)continue;
                double slack=need-dist;
                if(slack<bestSlack){bestSlack=slack;best=settlement;}
            }
        }
        return best;
    }

    /** Uniform clearance — larger cities do not get softer founding rules. */
    public static double requiredSpacing(Settlement settlement){
        Objects.requireNonNull(settlement,"settlement");
        return MIN_SETTLEMENT_SPACING;
    }

    /** Suggest a cardinal direction away from the blocking settlement. */
    public static String cardinalAway(SimPosition fromBlocker,SimPosition player){
        double dx=player.x()-fromBlocker.x(), dz=player.z()-fromBlocker.z();
        if(Math.abs(dx)<8 && Math.abs(dz)<8)return "north";
        if(Math.abs(dx)>=Math.abs(dz))return dx>=0?"east":"west";
        return dz>=0?"south":"north";
    }

    private static void stock(Faction faction,ResourceType type,double amount){faction.stockpile().add(type,amount);}
    private static double initialOpinion(long seed,long a,long b){long z=seed^(a*0x9E3779B97F4A7C15L)^(b*0xD1B54A32D192ED03L);z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;z^=z>>>31;return ((z>>>11)&0xFFL)/255.0*20.0-6.0;}
    private static Result fail(String reason){return new Result(false,reason,0,0,"","");}
    private static String clean(String value,int max){if(value==null)return "";String s=value.strip().replaceAll("\\s+"," ");if(s.length()>max)s=s.substring(0,max).strip();return s;}
}
