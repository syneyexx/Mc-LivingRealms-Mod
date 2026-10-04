package dev.livingrealms.sim.player;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.law.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Membership, reputation, influence, service progression and expulsion rules. */
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
        standing.adjustInfluence(factionId,InfluenceInstitution.COMMONERS,4);
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
        bumpInfluenceForService(standing,factionId,points);
        if(standing.rank()==FactionRank.SOLDIER||standing.rank()==FactionRank.OFFICER||standing.rank()==FactionRank.NOBLE){
            if(standing.grantCareerService(CareerTrack.MILITARY,points))state.history().add(new WorldEvent(state.clock().day(),"career_promoted","actor="+actorKey+", track=MILITARY, rank="+standing.careerRank().title()));
        }
        FactionRank before=standing.rank();if(standing.refreshRank()&&standing.rank()!=before)state.history().add(new WorldEvent(state.clock().day(),"faction_promoted","actor="+actorKey+", faction="+factionId+", rank="+standing.rank()));
    }

    public void grantCareerService(SimulationState state,String actorKey,CareerTrack track,double points){
        if(points<=0||!Double.isFinite(points)||track==null)throw new IllegalArgumentException("career");
        PlayerStanding standing=state.playerStanding(actorKey);
        if(standing.grantCareerService(track,points))state.history().add(new WorldEvent(state.clock().day(),"career_promoted","actor="+actorKey+", track="+track+", rank="+standing.careerRank().title()));
        if(standing.isMember()){
            InfluenceInstitution inst=switch(track){
                case MILITARY -> InfluenceInstitution.MILITARY; case POLITICAL -> InfluenceInstitution.CROWN;
                case ECONOMIC -> InfluenceInstitution.MERCHANTS; case RELIGIOUS -> InfluenceInstitution.CLERGY;
            };
            standing.adjustInfluence(standing.memberFactionId(),inst,Math.min(8,points*.04));
        }
    }

    public void onCrime(SimulationState state,CrimeIncident incident,CrimeResult result){
        if(!result.registered())return;
        PlayerStanding standing=state.playerStanding(incident.actorKey());
        double loss=Math.min(65,result.notorietyAdded()*.6+result.bountyAdded()*.035+(incident.type().violent()?8:0));
        standing.adjustReputation(incident.jurisdictionFactionId(),-loss);
        standing.adjustInfluence(incident.jurisdictionFactionId(),InfluenceInstitution.UNDERWORLD,Math.min(12,loss*.15));
        standing.adjustInfluence(incident.jurisdictionFactionId(),InfluenceInstitution.CROWN,-Math.min(10,loss*.12));
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

    private static void bumpInfluenceForService(PlayerStanding standing,long factionId,double points){
        double bump=Math.min(6,points*.03);
        InfluenceInstitution inst=switch(standing.rank()){
            case SOLDIER,OFFICER -> InfluenceInstitution.MILITARY;
            case NOBLE,RULER -> InfluenceInstitution.CROWN;
            case CITIZEN -> InfluenceInstitution.COMMONERS;
            default -> InfluenceInstitution.COMMONERS;
        };
        standing.adjustInfluence(factionId,inst,bump);
        if(standing.careerTrack()==CareerTrack.ECONOMIC)standing.adjustInfluence(factionId,InfluenceInstitution.MERCHANTS,bump*.5);
        if(standing.careerTrack()==CareerTrack.RELIGIOUS)standing.adjustInfluence(factionId,InfluenceInstitution.CLERGY,bump*.5);
        if(standing.careerTrack()==CareerTrack.POLITICAL)standing.adjustInfluence(factionId,InfluenceInstitution.CROWN,bump*.35);
    }

    private static void maybeExpel(SimulationState state,PlayerStanding standing,long factionId,String reason){
        if(standing.rank()==FactionRank.RULER)return;
        double bounty=state.crimeLedger().findProfile(standing.actorKey()).flatMap(p->p.find(factionId)).map(JurisdictionWanted::bounty).orElse(0.0);
        if(bounty<EXPULSION_BOUNTY&&standing.reputationWith(factionId)>EXPULSION_REPUTATION)return;
        standing.leave(true);state.history().add(new WorldEvent(state.clock().day(),"faction_expelled","actor="+standing.actorKey()+", faction="+factionId+", reason="+reason));
    }
}
