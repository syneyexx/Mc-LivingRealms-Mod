package dev.livingrealms.sim.world;

/**
 * Spreads large operator day jumps across server ticks so the integrated-server thread
 * never simulates more than {@link #MAX_DAYS_PER_TICK} days in one tick.
 *
 * <p>Headless tests should keep calling {@link SimulationState#advanceToDay(long)}
 * synchronously; this scheduler is for Minecraft tick handlers only.</p>
 */
public final class ManualDayAdvanceScheduler {
    public static final int MAX_DAYS_PER_TICK = 2;

    private long pendingDays;

    public long pendingDays() {
        return pendingDays;
    }

    public boolean hasPending() {
        return pendingDays > 0;
    }

    public void clear() {
        pendingDays = 0;
    }

    /**
     * Queues an absolute target day. Rejects rewind and jumps above
     * {@link SimulationState#MAX_MANUAL_DAY_JUMP}. Returns newly queued days
     * (not including any prior backlog).
     */
    public long enqueueAbsolute(SimulationState state, long targetDay) {
        long current = state.clock().day();
        if (targetDay < current) {
            throw new IllegalArgumentException("target day cannot be before current day " + current);
        }
        long delta = targetDay - current;
        if (delta > SimulationState.MAX_MANUAL_DAY_JUMP) {
            throw new IllegalArgumentException(
                    "maximum manual jump is " + SimulationState.MAX_MANUAL_DAY_JUMP + " days");
        }
        if (delta == 0) return 0;
        long room = SimulationState.MAX_MANUAL_DAY_JUMP - pendingDays;
        long queued = Math.min(delta, Math.max(0, room));
        pendingDays += queued;
        return queued;
    }

    /**
     * Queues a relative day count from the current clock, subject to the same caps.
     */
    public long enqueueRelative(SimulationState state, long days) {
        if (days <= 0) throw new IllegalArgumentException("days must be positive");
        return enqueueAbsolute(state, state.clock().day() + days);
    }

    /**
     * Advances at most {@link #MAX_DAYS_PER_TICK} pending days. Returns days actually simulated.
     */
    public long drainTick(SimulationState state) {
        if (pendingDays <= 0) return 0;
        int step = (int) Math.min(MAX_DAYS_PER_TICK, pendingDays);
        state.advanceDays(step);
        pendingDays -= step;
        return step;
    }
}
