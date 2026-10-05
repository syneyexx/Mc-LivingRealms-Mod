package dev.livingrealms;

import dev.livingrealms.sim.persistence.ContentMigrationPolicy;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.SimulationState;
import java.util.HashSet;
import java.util.Set;

/** B2–B4 migration order, densifier idempotency, and no phantom seeder completion keys. */
public final class MigrationOrderTest {
    private MigrationOrderTest() {}

    public static void main(String[] args) {
        decisionGates();
        freshSeedHasNoPhantomFarms();
        denseRealmDoesNotGrowPastTarget();
        densityIsIdempotent();
        System.out.println("PASS migration order: revision gates + empty hamlet completion + dense no-grow + idempotent densifier");
    }

    private static void decisionGates() {
        check(ContentMigrationPolicy.shouldResetMorphology(9), "rev9 resets morphology");
        check(!ContentMigrationPolicy.shouldResetMorphology(11), "rev11 does not reset morphology");
        check(!ContentMigrationPolicy.shouldResetMorphology(14), "current does not reset morphology");
        check(ContentMigrationPolicy.shouldEnsureDensity(9, 14), "rev9 densifies");
        check(ContentMigrationPolicy.shouldEnsureDensity(11, 14), "rev11 densifies toward 14");
        check(!ContentMigrationPolicy.shouldEnsureDensity(14, 14), "current densifier is one-shot");
        check(ContentMigrationPolicy.shouldRejectCorruptPayload(42L, 7L), "bad CRC rejected");
        check(!ContentMigrationPolicy.shouldRejectCorruptPayload(0L, 7L), "legacy integrity path allowed");
    }

    private static void freshSeedHasNoPhantomFarms() {
        SimulationState state = new SimulationState(0xB2B2L);
        DemoSeeder.seed(state);
        for (var faction : state.factions()) {
            if (faction.name().equals("Wizard Trees")) continue;
            for (var settlement : faction.settlements()) {
                for (String key : settlement.completedConstruction()) {
                    check(!key.startsWith("farm:") && !key.startsWith("pasture:") && !key.startsWith("well:"),
                            "fresh seed must not invent farm/pasture/well keys: " + settlement.name() + " " + key);
                }
            }
        }
    }

    private static void denseRealmDoesNotGrowPastTarget() {
        SimulationState state = new SimulationState(0xB3B3L);
        DemoSeeder.seed(state);
        var faction = state.factions().stream().filter(f -> !f.name().equals("Wizard Trees")).findFirst().orElseThrow();
        int before = faction.settlements().size();
        check(before >= SettlementDensitySeeder.TARGET_SETTLEMENTS_PER_REALM,
                "starter realm should already meet target: " + before);
        int added = SettlementDensitySeeder.ensureStarterDensity(state);
        check(added == 0, "densifier must not grow an already-complete world: " + added);
        check(faction.settlements().size() == before, "settlement count must stay put");
    }

    private static void densityIsIdempotent() {
        SimulationState state = new SimulationState(0xB4B4L);
        DemoSeeder.seed(state);
        check(SettlementDensitySeeder.ensureStarterDensity(state) == 0, "second densifier pass must be 0");
        Set<String> names = new HashSet<>();
        for (var f : state.factions()) for (var s : f.settlements()) check(names.add(s.name()), "duplicate " + s.name());
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
