package dev.livingrealms.api.event;
public record RulerChanged(long day, long factionId, String previousRuler, String newRuler, String reason) implements LifecycleEvent {
    public RulerChanged { previousRuler = previousRuler == null ? "" : previousRuler; newRuler = newRuler == null ? "" : newRuler; reason = reason == null ? "" : reason; }
}
