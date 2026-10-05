package dev.livingrealms.api.event;
public record FactionWarEnded(long day, long warId, long attackerFactionId, long defenderFactionId, double attackerScore) implements LifecycleEvent {}
