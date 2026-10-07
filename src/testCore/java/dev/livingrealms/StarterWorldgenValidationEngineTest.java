package dev.livingrealms;

import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import dev.livingrealms.sim.worldgen.StarterWorldgenValidationEngine;
import java.util.List;

/** Multi-seed structural validation gate for starter civilization planning. */
public final class StarterWorldgenValidationEngineTest {
    private StarterWorldgenValidationEngineTest() {}

    public static void main(String[] args) {
        long[] seeds = {0L, 1L, 42L, 60606L, 0xA57E2L, -991122L};
        for (long seed : seeds) {
            var layout = StarterCivilizationLayoutPlanner.plan(seed);
            var report = StarterWorldgenValidationEngine.validate(layout);
            check(report.settlementsChecked() >= 204, "settlement count seed=" + seed);
            check(report.routesChecked() > 0, "routes seed=" + seed);
            check(report.metrics().capitalsMissingKeep() == 0,
                    "capital keep missing seed=" + seed);
            check(report.metrics().disconnectedStreetGraphs() == 0,
                    "disconnected streets seed=" + seed + " issues=" + summarize(report));
            check(report.ok(),
                    "validation errors seed=" + seed + " " + summarize(report));
            check(report.metrics().buildingsWithRoadAccessRatio()
                            >= StarterWorldgenValidationEngine.MIN_ROAD_ACCESS_RATIO,
                    "road access ratio seed=" + seed + " "
                            + report.metrics().buildingsWithRoadAccessRatio());
        }
        System.out.println("PASS starter worldgen validation engine: multi-seed structural invariants");
    }

    private static String summarize(StarterWorldgenValidationEngine.Report report) {
        List<String> errors = report.issues().stream()
                .filter(i -> i.severity() == StarterWorldgenValidationEngine.Issue.Severity.ERROR)
                .limit(8)
                .map(i -> i.code() + ":" + i.detail())
                .toList();
        return errors.toString();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
