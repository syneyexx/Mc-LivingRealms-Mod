package dev.livingrealms.api.event;
public record CitizenDeath(long day, long settlementId, long citizenId, String cause) implements LifecycleEvent {
    public CitizenDeath { cause = cause == null ? "" : cause; }
}
