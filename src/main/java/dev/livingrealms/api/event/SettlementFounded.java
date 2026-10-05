package dev.livingrealms.api.event;
public record SettlementFounded(long day, long factionId, long settlementId, String name, String origin) implements LifecycleEvent {
    public SettlementFounded { name = name == null ? "" : name; origin = origin == null ? "" : origin; }
}
