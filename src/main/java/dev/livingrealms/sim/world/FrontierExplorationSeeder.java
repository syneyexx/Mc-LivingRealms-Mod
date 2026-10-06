package dev.livingrealms.sim.world;

/**
 * Compatibility shim. Exploration/player proximity must never found settlements.
 * Causal founding lives in {@link SettlementExpansionEngine}.
 */
public final class FrontierExplorationSeeder {
    public static final double GAP_BEFORE_SEED = 3_400.0;
    public static final int MAX_FRONTIER_PER_REALM = 12;

    private FrontierExplorationSeeder() {}

    /** Always returns 0 — player proximity is not a foundation cause. */
    public static int ensureNear(SimulationState state, SimPosition observer) {
        return SettlementExpansionEngine.ensureNear(state, observer);
    }
}
