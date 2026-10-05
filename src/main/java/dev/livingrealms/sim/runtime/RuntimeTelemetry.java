package dev.livingrealms.sim.runtime;

/** Lightweight per-domain runtime metrics without per-tick allocations. */
public final class RuntimeTelemetry {
    private static final double EMA_ALPHA = 0.08D;

    private long lastNanos;
    private long peakNanos;
    private double emaNanos;
    private long deferredCount;
    private long executedCount;
    private long starvationRuns;

    public void recordExecution(long durationNanos) {
        if (durationNanos < 0L) {
            durationNanos = 0L;
        }
        lastNanos = durationNanos;
        if (durationNanos > peakNanos) {
            peakNanos = durationNanos;
        }
        if (executedCount == 0L) {
            emaNanos = durationNanos;
        } else {
            emaNanos += EMA_ALPHA * (durationNanos - emaNanos);
        }
        executedCount++;
    }

    public void recordDeferred() {
        deferredCount++;
    }

    public void recordStarvationRun() {
        starvationRuns++;
    }

    public long lastNanos() {
        return lastNanos;
    }

    public long peakNanos() {
        return peakNanos;
    }

    public double emaNanos() {
        return emaNanos;
    }

    public long deferredCount() {
        return deferredCount;
    }

    public long executedCount() {
        return executedCount;
    }

    public long starvationRuns() {
        return starvationRuns;
    }

    public void resetPeaksAndDeferred() {
        peakNanos = lastNanos;
        deferredCount = 0L;
        starvationRuns = 0L;
    }

    public void resetAll() {
        lastNanos = 0L;
        peakNanos = 0L;
        emaNanos = 0D;
        deferredCount = 0L;
        executedCount = 0L;
        starvationRuns = 0L;
    }
}
