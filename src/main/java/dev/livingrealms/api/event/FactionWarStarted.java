package dev.livingrealms.api.event;
public record FactionWarStarted(long day, long warId, long attackerFactionId, long defenderFactionId, String goal, long targetSettlementId) implements LifecycleEvent {
    public FactionWarStarted { goal = goal == null ? "" : goal; }
}
