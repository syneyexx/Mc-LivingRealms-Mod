package dev.livingrealms.api.event;
public record SettlementTierChanged(long day, long settlementId, String previousTier, String newTier, int population) implements LifecycleEvent {
    public SettlementTierChanged { previousTier = previousTier == null ? "" : previousTier; newTier = newTier == null ? "" : newTier; }
}
