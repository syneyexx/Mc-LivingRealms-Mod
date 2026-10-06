package dev.livingrealms.sim.worldgen;

import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.WizardTreesPlanner;
import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WizardTreesSeeder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable day-zero Wizard Trees construction plan.
 *
 * <p>Canonical IDs come from the seeded faction, while population/housing/positions come from the
 * frozen starter specs. Reloading an evolved save therefore cannot move future unexplored chunks or
 * silently worldgen extra homes unlocked by later population growth.</p>
 */
public final class WizardTreesInitialWorldgenPlan {
    public record SettlementPlan(
            String stableKey,
            long factionId,
            long settlementId,
            String settlementName,
            SimPosition center,
            List<ConstructionIntent> intents
    ) {
        public SettlementPlan {
            if (stableKey == null || stableKey.isBlank()) throw new IllegalArgumentException("stableKey");
            if (factionId <= 0 || settlementId <= 0) throw new IllegalArgumentException("owner ids");
            if (settlementName == null || settlementName.isBlank()) throw new IllegalArgumentException("settlementName");
            center = Objects.requireNonNull(center, "center");
            intents = List.copyOf(Objects.requireNonNull(intents, "intents"));
        }
    }

    private WizardTreesInitialWorldgenPlan() {}

    public static List<SettlementPlan> build(SimulationState state) {
        Objects.requireNonNull(state, "state");
        Faction faction = state.factions().stream()
                .filter(WizardTreesSeeder::isWizardTrees)
                .findFirst().orElse(null);
        if (faction == null) return List.of();

        List<SettlementPlan> out = new ArrayList<>();
        for (WizardTreesSeeder.StarterSettlementSpec spec : WizardTreesSeeder.starterSettlements()) {
            Settlement canonical = faction.settlements().stream()
                    .filter(s -> s.name().equals(spec.name()))
                    .findFirst().orElse(null);
            if (canonical == null) continue;

            Settlement frozen = new Settlement(
                    canonical.id(), spec.name(), spec.position(), spec.population(), spec.housing(),
                    SettlementOrigin.WIZARD_TREES, true, DevelopmentMode.AUTO, SettlementRole.SPECIAL);
            List<ConstructionIntent> intents = WizardTreesPlanner.plan(faction, frozen);
            out.add(new SettlementPlan(
                    "wizard_trees/" + spec.stableKey(),
                    faction.id(),
                    canonical.id(),
                    spec.name(),
                    spec.position(),
                    intents));
        }
        return List.copyOf(out);
    }
}
