package dev.livingrealms.sim.construction;

/**
 * Historical growth layers revealed in settlement layout. Used by planners to bias
 * district age, density and street hierarchy — not a second settlement authority.
 */
public enum SettlementGrowthLayer {
    HISTORIC_CORE(0, StreetType.MARKET_STREET, 1.0),
    EARLY_EXPANSION(1, StreetType.RESIDENTIAL_STREET, 0.92),
    MARKET_QUARTER(2, StreetType.COMMERCIAL_STREET, 0.95),
    ARTISAN_QUARTER(3, StreetType.RESIDENTIAL_LANE, 0.88),
    DEFENSIVE_EXPANSION(4, StreetType.ARTERIAL, 0.90),
    SUBURBAN_EXPANSION(5, StreetType.RESIDENTIAL_LANE, 0.80),
    INDUSTRIAL_EXPANSION(6, StreetType.ARTERIAL, 0.85),
    HIGH_DENSITY_EXPANSION(7, StreetType.BOULEVARD, 0.98);

    private final int ringIndex;
    private final StreetType preferredStreet;
    private final double densityFactor;

    SettlementGrowthLayer(int ringIndex, StreetType preferredStreet, double densityFactor) {
        this.ringIndex = ringIndex;
        this.preferredStreet = preferredStreet;
        this.densityFactor = densityFactor;
    }

    public int ringIndex() { return ringIndex; }
    public StreetType preferredStreet() { return preferredStreet; }
    public double densityFactor() { return densityFactor; }

    public static SettlementGrowthLayer forRing(int ring, SettlementMorphology morph, int tierOrdinal) {
        int r = Math.max(0, ring);
        if (r == 0) return HISTORIC_CORE;
        if (r == 1) {
            return morph == SettlementMorphology.COASTAL_PORT || morph == SettlementMorphology.MARKET_CROSS
                    ? MARKET_QUARTER : EARLY_EXPANSION;
        }
        if (r == 2) {
            if (morph == SettlementMorphology.INDUSTRIAL_EDGE) return INDUSTRIAL_EXPANSION;
            if (morph == SettlementMorphology.WALLED_CORE || morph == SettlementMorphology.RADIAL_CAPITAL) return DEFENSIVE_EXPANSION;
            return ARTISAN_QUARTER;
        }
        if (r == 3) return SUBURBAN_EXPANSION;
        if (tierOrdinal >= 4 && morph == SettlementMorphology.INDUSTRIAL_EDGE) return INDUSTRIAL_EXPANSION;
        if (tierOrdinal >= 5) return HIGH_DENSITY_EXPANSION;
        return SUBURBAN_EXPANSION;
    }
}
