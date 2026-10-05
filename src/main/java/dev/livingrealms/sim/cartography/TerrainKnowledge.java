package dev.livingrealms.sim.cartography;

/**
 * Honest strategic-map terrain knowledge. The map must never invent elevation and present it as
 * real world geography.
 */
public enum TerrainKnowledge {
    /** World-derived sample from chunks that have actually been available. */
    ACTUAL,
    /** Coarse canonical ecology/geography tint — visibly estimated, no fake contours. */
    REGIONAL_ESTIMATE,
    /** No evidence — parchment/fog only. */
    UNKNOWN;

    public boolean isActual() { return this == ACTUAL; }
    public boolean allowsElevationShading() { return this == ACTUAL; }
    public boolean isEstimated() { return this == REGIONAL_ESTIMATE; }
}
