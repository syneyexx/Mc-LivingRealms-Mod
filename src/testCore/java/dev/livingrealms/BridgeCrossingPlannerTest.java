package dev.livingrealms;

import dev.livingrealms.sim.worldgen.BridgeCrossingPlanner;
import java.util.ArrayList;
import java.util.List;

/** Validates contiguous bridge objects instead of sparse pillar skeletons. */
public final class BridgeCrossingPlannerTest {
    private BridgeCrossingPlannerTest() {}

    public static void main(String[] args) {
        contiguousWaterBecomesOneAcceptedCrossing();
        overlongSpanIsRejected();
        dryCorridorProducesNoCrossing();
        System.out.println("PASS bridge crossing planner: contiguous decks + reject overlong spans");
    }

    private static void contiguousWaterBecomesOneAcceptedCrossing() {
        List<BridgeCrossingPlanner.Sample> samples = new ArrayList<>();
        samples.add(new BridgeCrossingPlanner.Sample(0, 0, 64, false, 64));
        for (int i = 1; i <= 8; i++) {
            samples.add(new BridgeCrossingPlanner.Sample(i, 0, 65, true, 50));
        }
        samples.add(new BridgeCrossingPlanner.Sample(9, 0, 64, false, 64));

        List<BridgeCrossingPlanner.Crossing> crossings = BridgeCrossingPlanner.plan(samples);
        check(crossings.size() == 1, "expected one crossing");
        BridgeCrossingPlanner.Crossing crossing = crossings.getFirst();
        check(crossing.accepted(), "crossing should be accepted: " + crossing.rejectReason());
        check(crossing.spanBlocks() == 8, "span=" + crossing.spanBlocks());
        check(crossing.deckY() == 65, "deckY");
        check(crossing.abutmentFromX() == 0 && crossing.abutmentToX() == 9, "abutments");
        check(crossing.supportSpacing() >= BridgeCrossingPlanner.MIN_SUPPORT_SPACING, "spacing");
    }

    private static void overlongSpanIsRejected() {
        List<BridgeCrossingPlanner.Sample> samples = new ArrayList<>();
        samples.add(new BridgeCrossingPlanner.Sample(0, 0, 64, false, 64));
        for (int i = 1; i <= BridgeCrossingPlanner.MAX_SPAN_BLOCKS + 5; i++) {
            samples.add(new BridgeCrossingPlanner.Sample(i, 0, 66, true, 40));
        }
        samples.add(new BridgeCrossingPlanner.Sample(
                BridgeCrossingPlanner.MAX_SPAN_BLOCKS + 6, 0, 64, false, 64));
        List<BridgeCrossingPlanner.Crossing> crossings = BridgeCrossingPlanner.plan(samples);
        check(crossings.size() == 1, "expected one overlong crossing");
        check(!crossings.getFirst().accepted(), "overlong must be rejected");
        check(crossings.getFirst().rejectReason().contains("span"), "reason");
    }

    private static void dryCorridorProducesNoCrossing() {
        List<BridgeCrossingPlanner.Sample> samples = List.of(
                new BridgeCrossingPlanner.Sample(0, 0, 64, false, 64),
                new BridgeCrossingPlanner.Sample(1, 0, 64, false, 64),
                new BridgeCrossingPlanner.Sample(2, 0, 65, false, 64));
        check(BridgeCrossingPlanner.plan(samples).isEmpty(), "dry corridor must not invent bridges");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
