package dev.livingrealms.api.event;

/** Immutable lifecycle notification for extension consumers. Listeners must not mutate canonical state. */
public sealed interface LifecycleEvent
        permits SettlementFounded, SettlementCaptured, SettlementTierChanged, FactionWarStarted, FactionWarEnded,
                CitizenDeath, RulerChanged, ConstructionCompleted, CrimeCommitted, TradeCompleted {
    long day();
}
