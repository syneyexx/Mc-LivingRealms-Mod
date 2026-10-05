package dev.livingrealms;

import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.ForeignAdoptionClassifier;
import dev.livingrealms.sim.world.OutlyingSite;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Focused foreign village spacing classification gate. */
public final class ForeignSettlementSpacingTest {
    private ForeignSettlementSpacingTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0xF0RE1L);
        DemoSeeder.seed(state);
        var host = state.factions().getFirst().settlements().getFirst();
        int before = state.factions().stream().mapToInt(f -> f.settlements().size()).sum();

        var far = ForeignAdoptionClassifier.classifyAndAdopt(state,
                new SimPosition(host.position().x() + 60_000, host.position().z() - 40_000),
                "DistantForeign", 180, 200, OutlyingSite.Type.FOREIGN_HAMLET);
        check(far.outcome() == ForeignAdoptionClassifier.Outcome.NEW_SETTLEMENT, "far >2000 → settlement");
        check(far.settlement().origin() == SettlementOrigin.FOREIGN_ADOPTED, "FOREIGN_ADOPTED");
        check(far.settlement().physicallyAnchored(), "anchored");

        var near = ForeignAdoptionClassifier.classifyAndAdopt(state,
                new SimPosition(host.position().x() + 900, host.position().z() + 100),
                "CloseForeign", 90, 100, OutlyingSite.Type.FOREIGN_HAMLET);
        check(near.outcome() == ForeignAdoptionClassifier.Outcome.OUTLYING_SITE, "near <2000 → site");
        check(near.site() != null && near.site().foreign(), "foreign site");
        check(state.factions().stream().mapToInt(f -> f.settlements().size()).sum() == before + 1,
                "only one new settlement (the far one)");

        var dup = ForeignAdoptionClassifier.classifyAndAdopt(state,
                new SimPosition(host.position().x() + 905, host.position().z() + 100),
                "CloseForeignDup", 90, 100, OutlyingSite.Type.FOREIGN_HAMLET);
        check(dup.outcome() == ForeignAdoptionClassifier.Outcome.IDEMPOTENT_SITE
                        || dup.outcome() == ForeignAdoptionClassifier.Outcome.OUTLYING_SITE,
                "duplicate site handled without new settlement: " + dup.outcome());
        check(dup.outcome() != ForeignAdoptionClassifier.Outcome.NEW_SETTLEMENT, "no illegal dense settlement");
        check(SettlementDensitySeeder.MIN_SETTLEMENT_SPACING
                        == ForeignAdoptionClassifier.CANONICAL_SETTLEMENT_SPACING,
                "spacing constants must share authority");
        System.out.println("PASS ForeignSettlementSpacingTest");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
