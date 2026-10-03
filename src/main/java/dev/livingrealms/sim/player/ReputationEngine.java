package dev.livingrealms.sim.player;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.law.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Membership, reputation, service progression and expulsion rules. */
public final class ReputationEngine {
    private static final double JOIN_REPUTATION=10;
    private static final double MAX_JOIN_BOUNTY=25;
    private static final double EXPULSION_BOUNTY=180;
    private static final double EXPULSION_REPUTATION=-45;

    public FactionJoinResult join(SimulationState state,String actorKey,long factionId){
        Objects.requireNonNull(state);PlayerStanding standing=state.playerStanding(actorKey);
        Faction faction=state.findFaction(factionId).orElse(null);
        if(faction==null)return new FactionJoinResult(false,"unknown_faction",standing.rank());
        if(standing.isMember())return new FactionJoinResult(false,standing.isMemberOf(factionId)?"already_member":"already_member_elsewhere",standing.rank());
        if(standing.reputationWith(factionId)<JOIN_REPUTATION)return new FactionJoinResult(false,"insufficient_reputation",standing.rank());
        double bounty=state.crimeLedger().findProfile(actorKey).flatMap(p->p.find(factionId)).map(JurisdictionWanted::bounty).orElse(0.0);
        if(bounty>MAX_JOIN_BOUNTY)return new FactionJoinResult(false,"wanted",standing.rank());
        if(state.activeCustody(actorKey,factionId).isPresent())return new FactionJoinResult(false,"in_custody",standing.rank());
        standing.join(factionId,state.clock().day());
        state.history().add(new WorldEvent(state.clock().day(),"faction_joined","actor="+actorKey+", faction="+factionId));
        return new FactionJoinResult(true,"joined",standing.rank());
    }

    public boolean leave(SimulationState state,String actorKey){
        PlayerStanding standing=state.findPlayerStanding(actorKey).orElse(null);if(standing==null||!standing.isMember())return false;
        long factionId=standing.memberFactionId();if(standing.rank()==FactionRank.RULER)return false;standing.leave(false);state.history().add(new WorldEvent(state.clock().day(),"faction_left","actor="+actorKey+", faction="+factionId));return true;
    }

    public void grantService(SimulationState state,String actorKey,long factionId,double points){
        if(points<=0||!Double.isFinite(points))throw new IllegalArgumentException("points");
        PlayerStanding standing=state.playerStanding(actorKey);if(!standing.isMemberOf(factionId))throw new IllegalStateException("not a member");
        standing.grantService(points);standing.adjustReputation(factionId,Math.min(10,points*.025));
        FactionRank before=standing.rank();if(standing.refreshRank()&&standing.rank()!=before)state.history().add(new WorldEvent(state.clock().day(),"faction_promoted","actor="+actorKey+", faction="+factionId+", rank="+standing.rank()));
    }

    public void onCrime(SimulationState state,CrimeIncident incident,CrimeResult result){
        if(!result.registered())return;
        PlayerStanding standing=state.playerStanding(incident.actorKey());
        double loss=Math.min(65,result.notorietyAdded()*.6+result.bountyAdded()*.035+(incident.type().violent()?8:0));
        standing.adjustReputation(incident.jurisdictionFactionId(),-loss);
        if(standing.isMemberOf(incident.jurisdictionFactionId())) maybeExpel(state,standing,incident.jurisdictionFactionId(),"criminal_conduct");
    }

    public void simulateDay(SimulationState state){
        for(PlayerStanding standing:state.playerStandings().values()){
            for(var e:new ArrayList<>(standing.reputations().entrySet())){
                if(state.findFaction(e.getKey()).isEmpty())continue;
                double v=e.getValue();if(Math.abs(v)>.05)standing.adjustReputation(e.getKey(),-Math.copySign(Math.min(.02,Math.abs(v)),v));
            }
            if(!standing.isMember())continue;
            if(state.findFaction(standing.memberFactionId()).isEmpty()){standing.leave(true);continue;}
            maybeExpel(state,standing,standing.memberFactionId(),"standing_below_threshold");
            if(standing.isMember())standing.refreshRank();
        }
    }

    private static void maybeExpel(SimulationState state,PlayerStanding standing,long factionId,String reason){
        if(standing.rank()==FactionRank.RULER)return;
        double bounty=state.crimeLedger().findProfile(standing.actorKey()).flatMap(p->p.find(factionId)).map(JurisdictionWanted::bounty).orElse(0.0);
        if(bounty<EXPULSION_BOUNTY&&standing.reputationWith(factionId)>EXPULSION_REPUTATION)return;
        standing.leave(true);state.history().add(new WorldEvent(state.clock().day(),"faction_expelled","actor="+standing.actorKey()+", faction="+factionId+", reason="+reason));
    }
}
