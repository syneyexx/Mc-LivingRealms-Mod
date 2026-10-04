package dev.livingrealms.sim.construction;

import java.util.List;

/**
 * @param completedJobKeys jobs whose required geometry is physically acceptable and may be marked complete
 * @param rejectedJobKeys jobs that finished their cursor but failed physical validation (must not mark complete)
 */
public record BuildTickResult(
        int attempted,
        int applied,
        int skipped,
        int blocked,
        List<String> completedJobKeys,
        List<String> rejectedJobKeys
) {
    public BuildTickResult {
        completedJobKeys = List.copyOf(completedJobKeys);
        rejectedJobKeys = List.copyOf(rejectedJobKeys);
    }

    /** Backward-compatible constructor used by older tests. */
    public BuildTickResult(int attempted, int applied, int skipped, int blocked, List<String> completedJobKeys) {
        this(attempted, applied, skipped, blocked, completedJobKeys, List.of());
    }
}
