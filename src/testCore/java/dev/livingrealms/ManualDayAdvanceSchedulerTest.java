package dev.livingrealms;

import dev.livingrealms.sim.ecology.SpeciesCatalog;
import dev.livingrealms.sim.world.ManualDayAdvanceScheduler;
import dev.livingrealms.sim.world.SimulationState;

/** E9: integrated-server day jumps drain at most two simulated days per tick. */
public final class ManualDayAdvanceSchedulerTest {
    private ManualDayAdvanceSchedulerTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0xDA75_CED1L, SpeciesCatalog.starter());
        ManualDayAdvanceScheduler scheduler = new ManualDayAdvanceScheduler();

        long queued = scheduler.enqueueAbsolute(state, 50);
        check(queued == 50, "enqueue queues full delta");
        check(scheduler.pendingDays() == 50, "pending matches enqueue");

        long first = scheduler.drainTick(state);
        check(first == ManualDayAdvanceScheduler.MAX_DAYS_PER_TICK, "first tick drains at most 2");
        check(state.clock().day() == 2, "clock advanced by drain");
        check(scheduler.pendingDays() == 48, "remaining pending after first drain");

        long second = scheduler.drainTick(state);
        check(second == 2 && state.clock().day() == 4, "second tick also drains 2");

        int ticks = 0;
        while (scheduler.hasPending()) {
            long drained = scheduler.drainTick(state);
            check(drained <= ManualDayAdvanceScheduler.MAX_DAYS_PER_TICK, "never exceeds max days/tick");
            check(drained > 0, "pending drain must make progress");
            ticks++;
            check(ticks < 100, "drain must finish");
        }
        check(state.clock().day() == 50, "full queue reaches target");
        check(scheduler.drainTick(state) == 0, "empty queue is no-op");

        boolean rewindRejected = false;
        try {
            scheduler.enqueueAbsolute(state, 10);
        } catch (IllegalArgumentException expected) {
            rewindRejected = true;
        }
        check(rewindRejected, "scheduler rejects rewind");

        boolean runawayRejected = false;
        try {
            scheduler.enqueueAbsolute(state, state.clock().day() + SimulationState.MAX_MANUAL_DAY_JUMP + 1);
        } catch (IllegalArgumentException expected) {
            runawayRejected = true;
        }
        check(runawayRejected, "scheduler rejects runaway jump");

        // Headless advanceToDay remains synchronous and uncapped by the tick scheduler.
        long sync = state.advanceToDay(60);
        check(sync == 10 && state.clock().day() == 60, "headless advanceToDay stays synchronous");

        System.out.println("PASS manual day advance scheduler: max "
                + ManualDayAdvanceScheduler.MAX_DAYS_PER_TICK
                + " days/tick + sync advanceToDay preserved");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
