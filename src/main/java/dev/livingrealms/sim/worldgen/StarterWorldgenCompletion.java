package dev.livingrealms.sim.worldgen;

import dev.livingrealms.sim.faction.ConstructionOrigin;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical bootstrap reconciliation for day-zero worldgen fabric.
 *
 * <p>These receipts suppress post-load duplicate baseline construction. They describe what the
 * deterministic world generator owns; they do not place blocks and are safe to create on the main
 * bootstrap thread before every referenced chunk has been explored.</p>
 */
public final class StarterWorldgenCompletion {
    private StarterWorldgenCompletion() {}

    public static int adoptPlannedBaseline(SimulationState state) {
        Objects.requireNonNull(state, "state");
        var layout = StarterCivilizationLayoutPlanner.plan(state.seed());
        return adoptPlannedBaseline(state, SettlementInitialWorldgenPlan.buildAll(layout));
    }

    public static int adoptWizardTreesBaseline(SimulationState state) {
        Objects.requireNonNull(state, "state");
        Map<Long, Settlement> canonical = new HashMap<>();
        for (var faction : state.factions()) for (var settlement : faction.settlements()) {
            canonical.put(settlement.id(), settlement);
        }

        int changes = 0;
        for (WizardTreesInitialWorldgenPlan.SettlementPlan plan
                : WizardTreesInitialWorldgenPlan.build(state)) {
            Settlement settlement = canonical.get(plan.settlementId());
            if (settlement == null) continue;
            for (var intent : plan.intents()) {
                if (settlement.markConstruction(intent.key(), ConstructionOrigin.WORLDGEN)) changes++;
            }
            settlement.markPhysicallyAnchored();
        }
        return changes;
    }

    public static int adoptPlannedBaseline(
            SimulationState state,
            java.util.List<SettlementInitialWorldgenPlan> plans) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(plans, "plans");
        Map<Long, Settlement> canonical = new HashMap<>();
        for (var faction : state.factions()) for (var settlement : faction.settlements()) {
            canonical.put(settlement.id(), settlement);
        }

        int changes = 0;
        for (SettlementInitialWorldgenPlan plan : plans) {
            Settlement settlement = canonical.get(plan.settlementId());
            if (settlement == null) continue;
            for (var intent : plan.intents()) {
                if (settlement.markConstruction(intent.key(), ConstructionOrigin.WORLDGEN)) changes++;
            }
            settlement.markPhysicallyAnchored();
        }
        return changes;
    }
}
