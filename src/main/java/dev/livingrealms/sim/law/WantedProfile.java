package dev.livingrealms.sim.law;

import java.util.*;

public final class WantedProfile {
    private final String actorKey;
    private final Map<Long,JurisdictionWanted> byFaction=new LinkedHashMap<>();
    private double globalInfamy;
    public WantedProfile(String actorKey){if(actorKey==null||actorKey.isBlank())throw new IllegalArgumentException("actorKey");this.actorKey=actorKey;}
    public String actorKey(){return actorKey;} public double globalInfamy(){return globalInfamy;} public Map<Long,JurisdictionWanted> jurisdictions(){return Collections.unmodifiableMap(byFaction);}
    public JurisdictionWanted in(long factionId){return byFaction.computeIfAbsent(factionId,JurisdictionWanted::new);}
    public Optional<JurisdictionWanted> find(long factionId){return Optional.ofNullable(byFaction.get(factionId));}
    public void addInfamy(double amount){globalInfamy=Math.max(0,globalInfamy+amount);} public void decayInfamy(double amount){globalInfamy=Math.max(0,globalInfamy-Math.max(0,amount));}
    public void restoreInfamy(double value){globalInfamy=Math.max(0,value);}
}
