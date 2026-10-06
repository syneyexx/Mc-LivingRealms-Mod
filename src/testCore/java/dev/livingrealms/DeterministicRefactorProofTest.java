package dev.livingrealms;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.SimulationState;

/**
 * Determinism proof for the current hierarchical world-fabric architecture.
 *
 * <p>The previous test pinned exact counts from the retired 36-surface / 3-per-realm layout.
 * This version keeps the stronger invariant that two independent simulations with the same seed
 * must be byte-for-byte-equivalent at the observable snapshot level through day 365, while also
 * pinning the current starter-density envelope.</p>
 */
public final class DeterministicRefactorProofTest {
    private static final long SEED = 0xA4C417EC7F00D26L;

    private DeterministicRefactorProofTest() {}

    public static void main(String[] args) {
        SimulationState a = new SimulationState(SEED);
        SimulationState b = new SimulationState(SEED);
        DemoSeeder.seed(a);
        DemoSeeder.seed(b);

        Snapshot a0 = snapshot(a), b0 = snapshot(b);
        check(a0.equals(b0), "day0 deterministic mismatch: " + a0 + " vs " + b0);
        check(a0.factions() == 13, "starter faction count");
        check(a0.surface() >= SettlementDensitySeeder.MIN_SURFACE_STARTER_SETTLEMENTS
                        && a0.surface() <= SettlementDensitySeeder.MAX_SURFACE_STARTER_SETTLEMENTS,
                "day0 surface starter envelope: " + a0.surface());
        check(a0.settlements() == a0.surface() + 3,
                "Wizard Trees should remain three non-surface colonies");
        check(a0.routes() > 0, "starter regional road graph must already exist");
        check(a0.roadsideSites() > 0, "starter inhabited routes must already have corridor fabric");

        a.advanceDays(30);
        b.advanceDays(30);
        Snapshot a30 = snapshot(a), b30 = snapshot(b);
        check(a30.equals(b30), "day30 deterministic mismatch: " + a30 + " vs " + b30);
        check(a.clock().day() == 30 && b.clock().day() == 30, "clock day 30");

        a.advanceDays(335);
        b.advanceDays(335);
        Snapshot a365 = snapshot(a), b365 = snapshot(b);
        check(a365.equals(b365), "day365 deterministic mismatch: " + a365 + " vs " + b365);
        check(a.clock().day() == 365 && b.clock().day() == 365, "clock day 365");
        check(a.summary().equals(b.summary()),
                "dual-run summary mismatch: " + a.summary() + " vs " + b.summary());

        System.out.println("PASS deterministic world fabric: day0=" + a0
                + " day30=" + a30 + " day365=" + a365);
    }

    private static Snapshot snapshot(SimulationState state) {
        int people = state.factions().stream().mapToInt(Faction::population).sum();
        return new Snapshot(
                state.factions().size(),
                state.factions().stream().mapToInt(f -> f.settlements().size()).sum(),
                state.factions().stream()
                        .filter(f -> !f.name().equals("Wizard Trees"))
                        .mapToInt(f -> f.settlements().size()).sum(),
                people,
                state.wars().stream().filter(w -> w.active()).count(),
                state.routes().size(),
                state.roadsideSites().size());
    }

    private record Snapshot(
            int factions,
            int settlements,
            int surface,
            int people,
            long wars,
            int routes,
            int roadsideSites
    ) {}

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
