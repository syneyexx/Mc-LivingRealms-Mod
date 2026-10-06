package dev.livingrealms;

import dev.livingrealms.sim.persistence.SaveSizeAuditor;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.StateRetentionCompactor;
import dev.livingrealms.sim.world.SimulationState;
import java.util.Locale;

/**
 * Day 0 / 365 save-size regression on a second deterministic seed.
 * The canonical 3650-day hard/soft size gate lives in {@link LongRunSoakTest} so core CI
 * does not simulate an extra redundant decade.
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

        System.out.println(String.format(Locale.ROOT,
                "PASS SaveSizeAuditor: day0=%d day365=%d dominant365=%s",
                day0.totalBytes(), day365.totalBytes(), day365.dominantContributor()));
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
