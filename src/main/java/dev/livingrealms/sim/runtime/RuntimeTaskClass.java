package dev.livingrealms.sim.runtime;

/** Failure classification for runtime scheduler tasks. */
public enum RuntimeTaskClass {
    CANONICAL_CRITICAL,
    RUNTIME_CRITICAL,
    PROJECTION_RECOVERABLE,
    PRESENTATION_OPTIONAL;

    public boolean swallowFailures() {
        return this == PROJECTION_RECOVERABLE || this == PRESENTATION_OPTIONAL;
    }
}
