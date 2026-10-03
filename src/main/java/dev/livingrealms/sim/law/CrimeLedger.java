package dev.livingrealms.sim.law;

import java.util.*;

public final class CrimeLedger {
    private final Map<String,WantedProfile> profiles=new LinkedHashMap<>();
    private final ArrayDeque<CrimeIncident> incidents=new ArrayDeque<>();
    private final int maxIncidents;
    public CrimeLedger(){this(20_000);} public CrimeLedger(int maxIncidents){this.maxIncidents=Math.max(100,maxIncidents);}
    public WantedProfile profile(String actorKey){return profiles.computeIfAbsent(actorKey,WantedProfile::new);}
    public Optional<WantedProfile> findProfile(String actorKey){return Optional.ofNullable(profiles.get(actorKey));}
    public Map<String,WantedProfile> profiles(){return Collections.unmodifiableMap(profiles);} public List<CrimeIncident> incidents(){return List.copyOf(incidents);}
    public void record(CrimeIncident incident){incidents.addLast(Objects.requireNonNull(incident));while(incidents.size()>maxIncidents)incidents.removeFirst();}
    public void restoreIncident(CrimeIncident incident){record(incident);}
}
