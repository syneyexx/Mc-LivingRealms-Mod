package dev.livingrealms;

import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.ForeignAdoptionClassifier;
import dev.livingrealms.sim.world.OutlyingSite;
import dev.livingrealms.sim.world.SettlementSpacingPolicy;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Focused foreign village classification gate for role-aware settlement spacing. */
public final class ForeignSettlementSpacingTest {
    private ForeignSettlementSpacingTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0xF0CE1L);
        DemoSeeder.seed(state);
        var host = state.factions().getFirst().settlements().getFirst();
        check(host.role() == SettlementRole.CAPITAL, "starter host role");
        int before = state.factions().stream().mapToInt(f -> f.settlements().size()).sum();

        var far = ForeignAdoptionClassifier.classifyAndAdopt(state,
                new SimPosition(host.position().x() + 60_000, host.position().z() - 40_000),
                "DistantForeign", 180, 200, OutlyingSite.Type.FOREIGN_HAMLET);
        check(far.outcome() == ForeignAdoptionClassifier.Outcome.NEW_SETTLEMENT, "distant foreign village should be adopted");
        check(far.settlement().origin() == SettlementOrigin.FOREIGN_ADOPTED, "FOREIGN_ADOPTED");
        check(far.settlement().role() == SettlementRole.VILLAGE, "population 180 should bootstrap village role");
        check(far.settlement().physicallyAnchored(), "foreign geometry must remain anchored");

        // 900 blocks from a capital is legal for a village: no obsolete 2000-block exclusion belt.
        var corridorVillage = ForeignAdoptionClassifier.classifyAndAdopt(state,
                new SimPosition(host.position().x() + 900, host.position().z() + 100),
                "CorridorForeign", 180, 200, OutlyingSite.Type.FOREIGN_HAMLET);
        check(corridorVillage.outcome() == ForeignAdoptionClassifier.Outcome.NEW_SETTLEMENT,
                "capital↔village at ~900 must not be rejected by a global floor");

        // Outside duplicate-footprint radius but inside capital↔village collision floor => attach as a site.
        var blocked = ForeignAdoptionClassifier.classifyAndAdopt(state,
                new SimPosition(host.position().x() + 240, host.position().z()),
                "CloseForeign", 180, 200, OutlyingSite.Type.FOREIGN_HAMLET);
        check(blocked.outcome() == ForeignAdoptionClassifier.Outcome.OUTLYING_SITE,
                "village inside capital collision floor should become outlying site");
        check(blocked.site() != null && blocked.site().foreign(), "foreign outlying site");

        var dup = ForeignAdoptionClassifier.classifyAndAdopt(state,
                new SimPosition(host.position().x() + 245, host.position().z()),
                "CloseForeignDup", 180, 200, OutlyingSite.Type.FOREIGN_HAMLET);
        check(dup.outcome() == ForeignAdoptionClassifier.Outcome.IDEMPOTENT_SITE
                        || dup.outcome() == ForeignAdoptionClassifier.Outcome.BOUND_EXISTING,
                "duplicate physical site must not create another settlement: " + dup.outcome());
        check(state.factions().stream().mapToInt(f -> f.settlements().size()).sum() == before + 2,
                "only the distant and legal corridor villages should become settlements");
        check(SettlementSpacingPolicy.minimumDistance(SettlementRole.CAPITAL, SettlementRole.VILLAGE) == 300.0,
                "capital-village floor pin");
        System.out.println("PASS ForeignSettlementSpacingTest: role-aware adoption without global 2000-block exclusion");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
