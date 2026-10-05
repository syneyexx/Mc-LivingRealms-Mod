package dev.livingrealms.sim.runtime;

/**
 * Decides whether non-critical runtime work may be deferred based on measured tick cost.
 * Applies hysteresis so a single fast tick does not immediately reopen decorative work.
 */
public final class RuntimeBudgetController {
    public enum Pressure {
        HEALTHY,
        SOFT,
        HARD
    }

    private Pressure pressure = Pressure.HEALTHY;
    private int recoveryTicks;

    public Pressure pressure() {
        return pressure;
    }

    public void reset() {
        pressure = Pressure.HEALTHY;
        recoveryTicks = 0;
    }

    public void beginTick(SimulationTickBudget budget) {
        // Pressure from the previous tick informs deferral for this tick.
    }

    public void endTick(SimulationTickBudget budget) {
        Pressure observed = observedPressure(budget);
        if (observed.ordinal() > pressure.ordinal()) {
            pressure = observed;
            recoveryTicks = recoveryTicksRequired(pressure);
        } else if (pressure != Pressure.HEALTHY) {
            if (observed == Pressure.HEALTHY && recoveryTicks > 0) {
                recoveryTicks--;
            } else if (observed.ordinal() < pressure.ordinal()) {
                recoveryTicks = Math.max(0, recoveryTicks - 1);
            }
            if (recoveryTicks == 0 && observed == Pressure.HEALTHY) {
                pressure = Pressure.HEALTHY;
            }
        }
    }

    /**
     * @return true when the task should run this tick; false when it may be deferred.
     */
    public boolean shouldRunNow(RuntimePriority priority, boolean starvation) {
        if (priority == RuntimePriority.CRITICAL || starvation) {
            return true;
        }
        return switch (pressure) {
            case HEALTHY -> true;
            case SOFT -> priority.ordinal() <= RuntimePriority.NORMAL.ordinal();
            case HARD -> priority.ordinal() <= RuntimePriority.HIGH.ordinal();
        };
    }

    public boolean mayDefer(RuntimePriority priority) {
        return priority != RuntimePriority.CRITICAL;
    }

    private static Pressure observedPressure(SimulationTickBudget budget) {
        if (budget.exceededHardLimit()) {
            return Pressure.HARD;
        }
        if (budget.exceededSoftLimit()) {
            return Pressure.SOFT;
        }
        return Pressure.HEALTHY;
    }

    private static int recoveryTicksRequired(Pressure pressure) {
        return switch (pressure) {
            case HEALTHY -> 0;
            case SOFT -> 2;
            case HARD -> 4;
        };
    }
}
