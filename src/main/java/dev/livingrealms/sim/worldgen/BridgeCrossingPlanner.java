package dev.livingrealms.sim.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Detects contiguous water / deep-void crossings along a graded route and turns them into one
 * planned bridge object per span.
 *
 * <p>A crossing is not a sparse column of pillars. It has abutments, a continuous deck elevation,
 * width, span length and deterministic support spacing. Invalid spans (too long, missing banks)
 * are marked rejected so callers can replan or skip rather than emitting stone skeletons.</p>
 */
public final class BridgeCrossingPlanner {
    public static final int MAX_SPAN_BLOCKS = 96;
    public static final int MIN_SUPPORT_SPACING = 4;
    public static final int MAX_SUPPORT_SPACING = 8;

    public record Sample(int x, int z, int deckY, boolean water, int groundY) {
        public Sample {
            if (deckY < groundY && water) {
                // Water columns may report ground below deck; keep as authored.
            }
        }
    }

    public record Crossing(
            int startIndex,
            int endIndex,
            int abutmentFromX,
            int abutmentFromZ,
            int abutmentToX,
            int abutmentToZ,
            int deckY,
            int spanBlocks,
            int supportSpacing,
            boolean accepted,
            String rejectReason
    ) {
        public Crossing {
            rejectReason = rejectReason == null ? "" : rejectReason;
        }

        public boolean containsIndex(int index) {
            return index >= startIndex && index <= endIndex;
        }
    }

    private BridgeCrossingPlanner() {}

    public static List<Crossing> plan(List<Sample> samples) {
        Objects.requireNonNull(samples, "samples");
        if (samples.isEmpty()) return List.of();

        List<Crossing> out = new ArrayList<>();
        int i = 0;
        while (i < samples.size()) {
            if (!needsEngineeredDeck(samples.get(i))) {
                i++;
                continue;
            }
            int start = i;
            while (i < samples.size() && needsEngineeredDeck(samples.get(i))) i++;
            int end = i - 1;
            out.add(buildCrossing(samples, start, end));
        }
        return List.copyOf(out);
    }

    /** True when this route column should ride an engineered deck rather than natural ground. */
    public static boolean needsEngineeredDeck(Sample sample) {
        Objects.requireNonNull(sample, "sample");
        if (sample.water()) return true;
        return sample.deckY() > sample.groundY() + 3;
    }

    public static Crossing findCovering(List<Crossing> crossings, int index) {
        Objects.requireNonNull(crossings, "crossings");
        for (Crossing crossing : crossings) {
            if (crossing.containsIndex(index)) return crossing;
        }
        return null;
    }

    private static Crossing buildCrossing(List<Sample> samples, int start, int end) {
        Sample first = samples.get(start);
        Sample last = samples.get(end);
        int span = end - start + 1;

        int abutmentFromX = first.x();
        int abutmentFromZ = first.z();
        int abutmentToX = last.x();
        int abutmentToZ = last.z();
        if (start > 0) {
            Sample bank = samples.get(start - 1);
            abutmentFromX = bank.x();
            abutmentFromZ = bank.z();
        }
        if (end + 1 < samples.size()) {
            Sample bank = samples.get(end + 1);
            abutmentToX = bank.x();
            abutmentToZ = bank.z();
        }

        int deckY = first.deckY();
        for (int i = start; i <= end; i++) {
            deckY = Math.max(deckY, samples.get(i).deckY());
        }

        boolean hasFromBank = start > 0 && !needsEngineeredDeck(samples.get(start - 1));
        boolean hasToBank = end + 1 < samples.size() && !needsEngineeredDeck(samples.get(end + 1));

        if (span > MAX_SPAN_BLOCKS) {
            return rejected(start, end, abutmentFromX, abutmentFromZ, abutmentToX, abutmentToZ,
                    deckY, span, "span exceeds " + MAX_SPAN_BLOCKS);
        }
        if (!hasFromBank || !hasToBank) {
            // Endpoint water is still buildable when the route begins/ends at a dock/gate on water,
            // but a mid-route open-ended span without either bank is rejected.
            if (!hasFromBank && !hasToBank && start > 0 && end + 1 < samples.size()) {
                return rejected(start, end, abutmentFromX, abutmentFromZ, abutmentToX, abutmentToZ,
                        deckY, span, "missing abutments");
            }
        }

        int supportSpacing = clamp(
                Math.max(MIN_SUPPORT_SPACING, span / 8),
                MIN_SUPPORT_SPACING,
                MAX_SUPPORT_SPACING);
        return new Crossing(
                start, end,
                abutmentFromX, abutmentFromZ, abutmentToX, abutmentToZ,
                deckY, span, supportSpacing,
                true, "");
    }

    private static Crossing rejected(
            int start, int end,
            int fromX, int fromZ, int toX, int toZ,
            int deckY, int span, String reason) {
        return new Crossing(
                start, end, fromX, fromZ, toX, toZ, deckY, span,
                MAX_SUPPORT_SPACING, false, reason);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
