package dev.livingrealms.sim.player;

import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
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
 *
 * <p>Founding begins as a small camp (not an instant town). First civic anchor is a town hall;
 * castles come later through normal development.</p>
 */
public final class PlayerSettlementFounder {
    public record Result(boolean success,String reason,long factionId,long settlementId,String realmName,String settlementName) {}

    /** Hard product floor shared with {@link SettlementDensitySeeder}. */
    public static final double MIN_SETTLEMENT_SPACING = SettlementDensitySeeder.MIN_SETTLEMENT_SPACING;
    /** Founding-camp population — small camp, not an instant city. */
    public static final int FOUNDING_POPULATION = 6;
    public static final int FOUNDING_HOUSING = 8;
    public static final int FOUNDING_ARMY = 4;

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
            return fail("Cannot found here: "+blocker.name()+" is "+Math.round(dist)
                    +" blocks away; "+Math.round(need)+" blocks are required. Move ~"
                    +Math.round(remain)+"m "+dir+" into open wilderness.");
        }

        long factionId=state.nextId(), settlementId=state.nextId();
        String realmName="Realm of "+settlement;
        Faction faction=new Faction(factionId,realmName,player);
        faction.restoreTreasury(420);
        faction.restoreTechnology(.18);
        Settlement capital=new Settlement(settlementId,settlement,position,FOUNDING_POPULATION,FOUNDING_HOUSING,
                SettlementOrigin.PLAYER_FOUNDED,true,DevelopmentMode.HYBRID);
        faction.addSettlement(capital);
        faction.addArmy(new Army(state.nextId(),factionId,new SimPosition(position.x()+12,position.z()+10),FOUNDING_ARMY));
        stock(faction,ResourceType.GRAIN,420);stock(faction,ResourceType.BREAD,280);
        stock(faction,ResourceType.WOOD,220);stock(faction,ResourceType.STONE,160);
        stock(faction,ResourceType.IRON,24);stock(faction,ResourceType.TOOLS,18);stock(faction,ResourceType.TEXTILES,20);
        capital.stockpile().add(ResourceType.GRAIN,80);
        capital.stockpile().add(ResourceType.BREAD,60);
        for(Faction other:state.factions()){
            double initial=initialOpinion(state.seed(),factionId,other.id());
            faction.relationWith(other.id()).adjust(initial);
            other.relationWith(factionId).adjust(initial);
        }
        state.addFaction(faction);
        standing.restoreReputation(factionId,100);
        standing.assumeRule(factionId,state.clock().day());
        // First civic anchor is a town hall / charter hall — not an instant keep or free farm.
        state.requestConstructionCatchup(20);
        capital.requestLandmark("town_hall:0");
        state.history().add(new WorldEvent(state.clock().day(),"player_realm_founded",
                player+" founded founding camp "+realmName+" at "+settlement+" (HYBRID development), faction="+factionId+", settlement="+settlementId));
        dev.livingrealms.api.LivingRealmsApi.publish(new dev.livingrealms.api.event.SettlementFounded(
                state.clock().day(),factionId,settlementId,settlement,SettlementOrigin.PLAYER_FOUNDED.name()));
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
