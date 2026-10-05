package dev.livingrealms.api.event;
public record SettlementCaptured(long day, long settlementId, long fromFactionId, long toFactionId, long warId) implements LifecycleEvent {}
