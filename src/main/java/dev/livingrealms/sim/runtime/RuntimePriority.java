package dev.livingrealms.sim.runtime;

/** Relative urgency when the tick budget is tight. Lower ordinal = more urgent. */
public enum RuntimePriority {
    CRITICAL,
    HIGH,
    NORMAL,
    LOW,
    DECORATIVE;

    public boolean moreUrgentThan(RuntimePriority other) {
        return ordinal() < other.ordinal();
    }
}
