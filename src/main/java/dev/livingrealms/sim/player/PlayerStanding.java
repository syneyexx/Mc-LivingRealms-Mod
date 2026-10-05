package dev.livingrealms.sim.player;

import dev.livingrealms.sim.util.Mathx;
import java.util.*;

/** Persistent faction reputation, influence and career state for one player/actor key. */
public final class PlayerStanding {
    private final String actorKey;
    private final Map<Long,Double> reputationByFaction=new LinkedHashMap<>();
    private final Map<Long,EnumMap<InfluenceInstitution,Double>> influenceByFaction=new LinkedHashMap<>();
    private long memberFactionId;
    private FactionRank rank=FactionRank.OUTSIDER;
    private long joinedDay=-1;
    private double servicePoints;
    private int expulsions;
    private CareerTrack careerTrack=CareerTrack.POLITICAL;
    private int careerRankIndex;
    private double careerService;

    public PlayerStanding(String actorKey){
        if(actorKey==null||actorKey.isBlank()) throw new IllegalArgumentException("actorKey");
        this.actorKey=actorKey;
    }

    public String actorKey(){return actorKey;}
    public Map<Long,Double> reputations(){return Collections.unmodifiableMap(reputationByFaction);}
    public double reputationWith(long factionId){return reputationByFaction.getOrDefault(factionId,0.0);}
    public long memberFactionId(){return memberFactionId;}
    public FactionRank rank(){return rank;}
    public long joinedDay(){return joinedDay;}
    public double servicePoints(){return servicePoints;}
    public int expulsions(){return expulsions;}
    public boolean isMember(){return memberFactionId>0&&rank!=FactionRank.OUTSIDER;}
    public boolean isMemberOf(long factionId){return isMember()&&memberFactionId==factionId;}
    public boolean isRulerOf(long factionId){return isMemberOf(factionId)&&rank==FactionRank.RULER;}
    public Map<Long,EnumMap<InfluenceInstitution,Double>> influences(){return Collections.unmodifiableMap(influenceByFaction);}
    public CareerTrack careerTrack(){return careerTrack;} public int careerRankIndex(){return careerRankIndex;} public double careerService(){return careerService;}
    public CareerRank careerRank(){return CareerRank.at(careerTrack,careerRankIndex);}

    public double influenceWith(long factionId,InfluenceInstitution institution){
        Objects.requireNonNull(institution);
        EnumMap<InfluenceInstitution,Double> map=influenceByFaction.get(factionId);
        return map==null?0.0:map.getOrDefault(institution,0.0);
    }
    public Map<InfluenceInstitution,Double> influencesWith(long factionId){
        EnumMap<InfluenceInstitution,Double> map=influenceByFaction.get(factionId);
        if(map==null)return Map.of();
        return Collections.unmodifiableMap(map);
    }
    public double adjustInfluence(long factionId,InfluenceInstitution institution,double delta){
        if(factionId<=0||institution==null||!Double.isFinite(delta))throw new IllegalArgumentException("influence");
        EnumMap<InfluenceInstitution,Double> map=influenceByFaction.computeIfAbsent(factionId,k->new EnumMap<>(InfluenceInstitution.class));
        double next=Mathx.clamp(map.getOrDefault(institution,0.0)+delta,0,100);
        map.put(institution,next);return next;
    }
    public void restoreInfluence(long factionId,InfluenceInstitution institution,double value){
        if(factionId<=0||institution==null||!Double.isFinite(value))throw new IllegalArgumentException("influence");
        EnumMap<InfluenceInstitution,Double> map=influenceByFaction.computeIfAbsent(factionId,k->new EnumMap<>(InfluenceInstitution.class));
        map.put(institution,Mathx.clamp(value,0,100));
    }

    public double adjustReputation(long factionId,double delta){
        if(factionId<=0||!Double.isFinite(delta)) throw new IllegalArgumentException("reputation");
        double next=Mathx.clamp(reputationWith(factionId)+delta,-100,100);
        reputationByFaction.put(factionId,next);
        return next;
    }

    public void restoreReputation(long factionId,double value){
        if(factionId<=0||!Double.isFinite(value)) throw new IllegalArgumentException("reputation");
        reputationByFaction.put(factionId,Mathx.clamp(value,-100,100));
    }

    /** Drops a reputation row for a missing/stale faction id (projection index repair only). */
    public boolean clearReputation(long factionId){
        return reputationByFaction.remove(factionId)!=null;
    }

    /** Drops influence rows for a missing/stale faction id (projection index repair only). */
    public boolean clearInfluence(long factionId){
        return influenceByFaction.remove(factionId)!=null;
    }

    public void join(long factionId,long day){
        if(factionId<=0||day<0||isMember()) throw new IllegalStateException("membership");
        memberFactionId=factionId;rank=FactionRank.CITIZEN;joinedDay=day;servicePoints=0;
        careerTrack=CareerTrack.POLITICAL;careerRankIndex=0;careerService=0;
    }

    public void assumeRule(long factionId,long day){if(factionId<=0||day<0)throw new IllegalArgumentException("rule");if(isMember()&&!isMemberOf(factionId))throw new IllegalStateException("member elsewhere");memberFactionId=factionId;rank=FactionRank.RULER;joinedDay=joinedDay<0?day:joinedDay;servicePoints=Math.max(servicePoints,2000);careerTrack=CareerTrack.POLITICAL;careerRankIndex=Math.max(careerRankIndex,CareerRank.ranks(CareerTrack.POLITICAL).length-1);}

    public void leave(boolean expelled){
        memberFactionId=0;rank=FactionRank.OUTSIDER;joinedDay=-1;servicePoints=0;
        if(expelled) expulsions++;
    }

    public void grantService(double amount){
        if(!isMember()||amount<0||!Double.isFinite(amount)) throw new IllegalArgumentException("service");
        servicePoints=Math.max(0,servicePoints+amount);
    }

    public boolean grantCareerService(CareerTrack track,double amount){
        if(track==null||amount<0||!Double.isFinite(amount))throw new IllegalArgumentException("career");
        int before=careerRankIndex;
        if(careerTrack!=track){careerTrack=track;careerRankIndex=0;careerService=0;}
        careerService=Math.max(0,careerService+amount);
        careerRankIndex=CareerRank.resolveIndex(careerTrack,careerService);
        return careerRankIndex!=before;
    }

    public void restoreCareer(CareerTrack track,int rankIndex,double service){
        if(track==null||rankIndex<0||rankIndex>=CareerRank.ranks(track).length||service<0||!Double.isFinite(service))throw new IllegalArgumentException("career");
        careerTrack=track;careerRankIndex=rankIndex;careerService=service;
    }

    public boolean refreshRank(){
        if(!isMember()||rank==FactionRank.RULER) return false;
        FactionRank next=FactionRank.CITIZEN;
        for(FactionRank candidate:FactionRank.values()) {
            if(candidate==FactionRank.OUTSIDER) continue;
            if(servicePoints>=candidate.serviceRequired()&&reputationWith(memberFactionId)>=candidate.reputationRequired()) next=candidate;
        }
        if(next==rank) return false;
        rank=next;return true;
    }

    public void restoreMembership(long factionId,FactionRank restoredRank,long restoredJoinedDay,double restoredService,int restoredExpulsions){
        if(restoredRank==null||restoredService<0||!Double.isFinite(restoredService)||restoredExpulsions<0) throw new IllegalArgumentException("membership");
        if(factionId<=0||restoredRank==FactionRank.OUTSIDER){memberFactionId=0;rank=FactionRank.OUTSIDER;joinedDay=-1;servicePoints=0;expulsions=restoredExpulsions;return;}
        memberFactionId=factionId;rank=restoredRank;joinedDay=Math.max(0,restoredJoinedDay);servicePoints=restoredService;expulsions=restoredExpulsions;
    }
}
