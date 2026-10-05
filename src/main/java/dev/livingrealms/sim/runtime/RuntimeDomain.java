package dev.livingrealms.sim.runtime;

/** Coarse runtime ownership for tick scheduling and perf telemetry. */
public enum RuntimeDomain {
    PROJECTION,
    PRESENTATION,
    DISCOVERY,
    CONSTRUCTION,
    MAINTENANCE,
    SIMULATION,
    CRITICAL
}
