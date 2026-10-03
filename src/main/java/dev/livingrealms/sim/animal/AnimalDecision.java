package dev.livingrealms.sim.animal;

/** Decision plus normalized urgency; urgency can drive goal priority in the Minecraft adapter. */
public record AnimalDecision(AnimalIntent intent, double urgency, String reason) {
    public AnimalDecision {
        if (intent == null) throw new IllegalArgumentException("intent");
        if (!Double.isFinite(urgency) || urgency < 0 || urgency > 1) throw new IllegalArgumentException("urgency");
        reason = reason == null ? "" : reason;
    }
}
