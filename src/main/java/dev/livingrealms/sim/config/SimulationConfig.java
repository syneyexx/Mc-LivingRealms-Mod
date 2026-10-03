package dev.livingrealms.sim.config;

/** Central deterministic simulation budgets and tuning knobs. */
public record SimulationConfig(
        double physicalRadiusBlocks,
        double regionalRadiusBlocks,
        int maxPhysicalWildlife,
        int maxPhysicalCaravans,
        int maxPhysicalMilitaryEntities,
        int maxPhysicalNavalEntities,
        int constructionBlockOpsPerTick,
        int strategicDaysPerStep,
        double crimeHeatDecayPerDay,
        double reputationDecayPerDay,
        double rebellionThreshold,
        double borderDisputeThreshold
) {
    public static final double MAX_PHYSICAL_RADIUS_BLOCKS = 8_192.0D;
    public static final double MAX_REGIONAL_RADIUS_BLOCKS = 65_536.0D;
    public static final int MAX_PHYSICAL_ENTITY_BUDGET = 8_192;
    public static final int MAX_CONSTRUCTION_BLOCK_OPS_PER_TICK = 32_768;
    public static final int MAX_STRATEGIC_DAYS_PER_STEP = 30;
    public static final double MAX_DECAY_PER_DAY = 100.0D;
    public static final double MAX_BORDER_DISPUTE_THRESHOLD = 1_000_000.0D;

    public SimulationConfig {
        if (!Double.isFinite(physicalRadiusBlocks) || !(physicalRadiusBlocks > 0)
                || physicalRadiusBlocks > MAX_PHYSICAL_RADIUS_BLOCKS) throw new IllegalArgumentException("physicalRadiusBlocks");
        if (!Double.isFinite(regionalRadiusBlocks) || !(regionalRadiusBlocks > physicalRadiusBlocks)
                || regionalRadiusBlocks > MAX_REGIONAL_RADIUS_BLOCKS) throw new IllegalArgumentException("regionalRadiusBlocks");
        if (maxPhysicalWildlife < 0 || maxPhysicalWildlife > MAX_PHYSICAL_ENTITY_BUDGET
                || maxPhysicalCaravans < 0 || maxPhysicalCaravans > MAX_PHYSICAL_ENTITY_BUDGET
                || maxPhysicalMilitaryEntities < 0 || maxPhysicalMilitaryEntities > MAX_PHYSICAL_ENTITY_BUDGET
                || maxPhysicalNavalEntities < 0 || maxPhysicalNavalEntities > MAX_PHYSICAL_ENTITY_BUDGET) {
            throw new IllegalArgumentException("entity budgets");
        }
        if (constructionBlockOpsPerTick < 1 || constructionBlockOpsPerTick > MAX_CONSTRUCTION_BLOCK_OPS_PER_TICK
                || strategicDaysPerStep < 1 || strategicDaysPerStep > MAX_STRATEGIC_DAYS_PER_STEP) {
            throw new IllegalArgumentException("tick budgets");
        }
        if (!Double.isFinite(crimeHeatDecayPerDay) || crimeHeatDecayPerDay < 0 || crimeHeatDecayPerDay > MAX_DECAY_PER_DAY
                || !Double.isFinite(reputationDecayPerDay) || reputationDecayPerDay < 0 || reputationDecayPerDay > MAX_DECAY_PER_DAY) {
            throw new IllegalArgumentException("decay");
        }
        if (!Double.isFinite(rebellionThreshold) || rebellionThreshold < 0 || rebellionThreshold > 1) throw new IllegalArgumentException("rebellionThreshold");
        if (!Double.isFinite(borderDisputeThreshold) || borderDisputeThreshold < 0
                || borderDisputeThreshold > MAX_BORDER_DISPUTE_THRESHOLD) throw new IllegalArgumentException("borderDisputeThreshold");
    }

    public static SimulationConfig defaults() {
        return new SimulationConfig(320, 2048, 160, 24, 96, 32, 320, 1, 1.25, .20, .78, 12.0);
    }
}
