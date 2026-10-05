package dev.livingrealms.api.event;
public record CrimeCommitted(long day, long factionId, String actorKey, String crimeType, boolean witnessed, double bounty) implements LifecycleEvent {
    public CrimeCommitted { actorKey = actorKey == null ? "" : actorKey; crimeType = crimeType == null ? "" : crimeType; }
}
