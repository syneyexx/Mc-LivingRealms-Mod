package dev.livingrealms.sim.runtime;

/**
 * Wall-clock budget for one server tick slice. Uses {@link System#nanoTime()} for runtime
 * measurement only — never for simulation determinism.
 */
public final class SimulationTickBudget {
    public static final long HEALTHY_MAX_NANOS = 12_000_000L;
    public static final long SOFT_LIMIT_NANOS = 15_000_000L;
    public static final long HARD_LIMIT_NANOS = 25_000_000L;

    private final long startedNanos;
    private final long allowanceNanos;
    private long spentNanos;

    public SimulationTickBudget(long allowanceNanos) {
        this.startedNanos = System.nanoTime();
        this.allowanceNanos = Math.max(1L, allowanceNanos);
        this.spentNanos = 0L;
    }

    public static SimulationTickBudget forHealthyTick() {
        return new SimulationTickBudget(HEALTHY_MAX_NANOS);
    }

    public long startedNanos() {
        return startedNanos;
    }

    public long allowanceNanos() {
        return allowanceNanos;
    }

    public long spentNanos() {
        return spentNanos;
    }

    public long remainingNanos() {
        return Math.max(0L, allowanceNanos - spentNanos);
    }

    public void recordSpent(long deltaNanos) {
        if (deltaNanos > 0L) {
            spentNanos += deltaNanos;
        }
    }

    public boolean hasRemaining() {
        return spentNanos < allowanceNanos;
    }

    public boolean exceededSoftLimit() {
        return spentNanos > SOFT_LIMIT_NANOS;
    }

    public boolean exceededHardLimit() {
        return spentNanos > HARD_LIMIT_NANOS;
    }
}
