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
        assertSnapshot("day0-seed", state, 13, 39, 36, 52012, 52, 0, 0);

        state.advanceDays(30);
        assertSnapshot("day30", state,
                DAY30_FACTIONS, DAY30_SETTLEMENTS, DAY30_SURFACE,
                DAY30_PEOPLE, DAY30_POP_BAND, DAY30_WARS, DAY30_ROUTES);
        check(state.clock().day() == 30, "clock day 30");

        state.advanceDays(335);
        assertSnapshot("day365", state,
                DAY365_FACTIONS, DAY365_SETTLEMENTS, DAY365_SURFACE,
                DAY365_PEOPLE, DAY365_POP_BAND, DAY365_WARS, DAY365_ROUTES);
        check(state.clock().day() == 365, "clock day 365");

        // Dual-run determinism: identical seed must match summary at day 365.
        SimulationState twin = new SimulationState(SEED);
        DemoSeeder.seed(twin);
        twin.advanceDays(365);
        check(state.summary().equals(twin.summary()), "dual-run summary mismatch: " + state.summary() + " vs " + twin.summary());

        System.out.println("PASS deterministic refactor proof: seed=0xA4C417EC7F00D26 day30/365 goldens + dual-run: " + state.summary());
    }

    private static void assertSnapshot(
            String label,
            SimulationState state,
            int factions,
            int settlements,
            int surface,
            int people,
            int popBand,
            long wars,
            int routes) {
        int gotFactions = state.factions().size();
        int gotSettlements = state.factions().stream().mapToInt(f -> f.settlements().size()).sum();
        int gotSurface = state.factions().stream()
                .filter(f -> !f.name().equals("Wizard Trees"))
                .mapToInt(f -> f.settlements().size())
                .sum();
        int gotPeople = state.factions().stream().mapToInt(Faction::population).sum();
        int gotBand = gotPeople / 1000;
        long gotWars = state.wars().stream().filter(w -> w.active()).count();
        int gotRoutes = state.routes().size();

        check(gotFactions == factions, label + " factions expected " + factions + " got " + gotFactions);
        check(gotSettlements == settlements, label + " settlements expected " + settlements + " got " + gotSettlements);
        check(gotSurface == surface, label + " surfaceSettlements expected " + surface + " got " + gotSurface);
        check(gotPeople == people, label + " people expected " + people + " got " + gotPeople);
        check(gotBand == popBand, label + " populationBand expected " + popBand + " got " + gotBand);
        check(gotWars == wars, label + " activeWars expected " + wars + " got " + gotWars);
        check(gotRoutes == routes, label + " routes expected " + routes + " got " + gotRoutes);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
