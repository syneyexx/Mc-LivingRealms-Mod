package dev.livingrealms.sim.construction;

import java.util.List;

public record BuildTickResult(int attempted, int applied, int skipped, int blocked, List<String> completedJobKeys) {
    public BuildTickResult { completedJobKeys = List.copyOf(completedJobKeys); }
}
