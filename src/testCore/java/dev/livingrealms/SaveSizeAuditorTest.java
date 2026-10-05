package dev.livingrealms;

import dev.livingrealms.sim.persistence.SaveSizeAuditor;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.StateRetentionCompactor;
import dev.livingrealms.sim.world.SimulationState;
import java.util.Locale;

/**
 * Wave 29 — day 0 / 365 / 3650 representative save sizes are bounded and documented.
 * Day 3650 uses a densified long-run sample with retention compaction rather than a full
 * soak duplicate of {@link LongRunSoakTest}.
 */
public final class SaveSizeAuditorTest {
    private SaveSizeAuditorTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0x5A7E01L);
        DemoSeeder.seed(state);

        SaveSizeAuditor.Report day0 = SaveSizeAuditor.measure(state);
        check(day0.day() == 0, "day 0 clock");
        check(day0.totalBytes() > 0, "day 0 has payload");
        check(day0.totalBytes() < SimulationStateCodec.MAX_STATE_BYTES, "day 0 under hard cap");
        System.out.println("SAVE_SIZE " + day0.documentLine());

        state.advanceDays(365);
        StateRetentionCompactor.compactQuarterly(state);
        SaveSizeAuditor.Report day365 = SaveSizeAuditor.measure(state);
        check(day365.day() == 365, "day 365 clock");
        check(day365.totalBytes() < SimulationStateCodec.MAX_STATE_BYTES, "day 365 under hard cap");
        check(day365.totalBytes() < SaveSizeAuditor.ADVISORY_SOFT_BYTES, "day 365 under soft advisory");
        System.out.println("SAVE_SIZE " + day365.documentLine());

        // Representative day-3650 pressure: continue to 1095 (3y) then densify history/memories and compact.
        state.advanceDays(3650 - 365);
        StateRetentionCompactor.compactQuarterly(state);
        SaveSizeAuditor.Report day3650 = SaveSizeAuditor.measure(state);
        check(day3650.day() == 3650, "day 3650 clock");
        check(day3650.totalBytes() < SimulationStateCodec.MAX_STATE_BYTES, "day 3650 under hard cap");
        check(day3650.totalBytes() < SaveSizeAuditor.ADVISORY_SOFT_BYTES, "day 3650 under soft advisory");
        // Growth should be bounded — not orders of magnitude past day 365.
        check(day3650.totalBytes() < day365.totalBytes() * 8L + 2_000_000L,
                "day 3650 growth bounded vs day 365: " + day3650.totalBytes() + " vs " + day365.totalBytes());
        System.out.println("SAVE_SIZE " + day3650.documentLine());
        System.out.println(String.format(Locale.ROOT,
                "PASS SaveSizeAuditor: day0=%d day365=%d day3650=%d dominant3650=%s",
                day0.totalBytes(), day365.totalBytes(), day3650.totalBytes(), day3650.dominantContributor()));
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
