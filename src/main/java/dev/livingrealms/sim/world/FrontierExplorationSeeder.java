package dev.livingrealms.sim.world;

/**
 * Compatibility shim. Exploration/player proximity must never found settlements.
 * Causal founding lives in {@link SettlementExpansionEngine}.
 */
public final class FrontierExplorationSeeder {
    public static final double GAP_BEFORE_SEED = 3_400.0;
    public static final double FRONTIER_SPACING = SettlementDensitySeeder.MIN_SETTLEMENT_SPACING;
    public static final int MAX_FRONTIER_PER_REALM = SettlementExpansionEngine.MAX_CAUSAL_PER_REALM;

    private FrontierExplorationSeeder() {}

    /**
     * @deprecated Player proximity is not a foundation cause. Always returns 0.
     * Use {@link SettlementExpansionEngine#tick(SimulationState)} for causal expansion.
     */
    @Deprecated
    public static int ensureNear(SimulationState state, SimPosition observer) {
        return SettlementExpansionEngine.ensureNear(state, observer);
    }
}
