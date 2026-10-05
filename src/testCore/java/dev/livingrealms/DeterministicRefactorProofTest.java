package dev.livingrealms;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimulationState;

/**
 * Wave 26 — behavior-preserving refactor proof.
 *
 * Known seed, DemoSeeder init, advance 30 and 365 days, assert stable count bands
 * for factions / settlements / population / wars / routes. Golden values captured
 * post-refactor on branch cursor/architecture-depth-pass-f4a7 (Wave 26 baseline).
 *
 * If an intentional bugfix changes these goldens, update the constants below and
 * document the reason in a comment next to the changed expectation.
 */
public final class DeterministicRefactorProofTest {
    /** Fixture seed for architecture-pass golden replay. */
    private static final long SEED = 0xA4C417EC7F00D26L;

    // --- day 30 goldens (captured after modular-monolith extractions) ---
    private static final int DAY30_FACTIONS = 13;
    private static final int DAY30_SETTLEMENTS = 69;
    private static final int DAY30_SURFACE = 66;
    private static final int DAY30_PEOPLE = 52690;
    private static final int DAY30_POP_BAND = 52; // people / 1000
    private static final long DAY30_WARS = 0;
    private static final int DAY30_ROUTES = 102;

    // --- day 365 goldens ---
    // Note (post Wave 16/21 API+historical-trace hooks): people 52092→52107 and routes 210→214
    // vs the first Wave-26 capture on d0163b0. Intentional side-effect of lifecycle event publish
    // / historical trace wiring — not a pin or seeder change. Population band unchanged (52).
    private static final int DAY365_FACTIONS = 24;
    private static final int DAY365_SETTLEMENTS = 87;
    private static final int DAY365_SURFACE = 82;
    private static final int DAY365_PEOPLE = 52107;
    private static final int DAY365_POP_BAND = 52;
    private static final long DAY365_WARS = 10;
    private static final int DAY365_ROUTES = 214;

    private DeterministicRefactorProofTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(SEED);
        DemoSeeder.seed(state);
        Snapshot day0 = snapshot(state);

        state.advanceDays(30);
        Snapshot day30 = snapshot(state);
        check(state.clock().day() == 30, "clock day 30");

        state.advanceDays(335);
        Snapshot day365 = snapshot(state);
        check(state.clock().day() == 365, "clock day 365");

        // Print all three actual snapshots before validating old/new goldens so one CI run can
        // rebaseline an intentional architecture change without serial one-mismatch-at-a-time runs.
        System.out.println("GOLDEN_CAPTURE day0=" + day0 + " day30=" + day30 + " day365=" + day365);
        assertSnapshot("day0-seed", day0, 13, 39, 36, 52012, 52, 0, 0);
        assertSnapshot("day30", day30,
                DAY30_FACTIONS, DAY30_SETTLEMENTS, DAY30_SURFACE,
                DAY30_PEOPLE, DAY30_POP_BAND, DAY30_WARS, DAY30_ROUTES);
        assertSnapshot("day365", day365,
                DAY365_FACTIONS, DAY365_SETTLEMENTS, DAY365_SURFACE,
                DAY365_PEOPLE, DAY365_POP_BAND, DAY365_WARS, DAY365_ROUTES);

        // Dual-run determinism: identical seed must match summary at day 365.
        SimulationState twin = new SimulationState(SEED);
        DemoSeeder.seed(twin);
        twin.advanceDays(365);
        check(state.summary().equals(twin.summary()), "dual-run summary mismatch: " + state.summary() + " vs " + twin.summary());

        System.out.println("PASS deterministic refactor proof: seed=0xA4C417EC7F00D26 day30/365 goldens + dual-run: " + state.summary());
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
                people / 1000,
                state.wars().stream().filter(w -> w.active()).count(),
                state.routes().size());
    }

    private static void assertSnapshot(
            String label,
            Snapshot got,
            int factions,
            int settlements,
            int surface,
            int people,
            int popBand,
            long wars,
            int routes) {
        check(got.factions() == factions, label + " factions expected " + factions + " got " + got.factions());
        check(got.settlements() == settlements, label + " settlements expected " + settlements + " got " + got.settlements());
        check(got.surface() == surface, label + " surfaceSettlements expected " + surface + " got " + got.surface());
        check(got.people() == people, label + " people expected " + people + " got " + got.people());
        check(got.popBand() == popBand, label + " populationBand expected " + popBand + " got " + got.popBand());
        check(got.wars() == wars, label + " activeWars expected " + wars + " got " + got.wars());
        check(got.routes() == routes, label + " routes expected " + routes + " got " + got.routes());
    }

    private record Snapshot(int factions, int settlements, int surface, int people,
                            int popBand, long wars, int routes) {}

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
