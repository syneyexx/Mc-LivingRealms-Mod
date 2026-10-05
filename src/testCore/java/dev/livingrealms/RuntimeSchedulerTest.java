package dev.livingrealms;

import dev.livingrealms.sim.runtime.RuntimeBudgetController;
import dev.livingrealms.sim.runtime.RuntimeDeferTracker;
import dev.livingrealms.sim.runtime.RuntimePriority;
import dev.livingrealms.sim.runtime.SimulationTickBudget;

/**
 * Headless coverage for runtime budget pressure, deferral, starvation promotion, and hysteresis.
 */
public final class RuntimeSchedulerTest {
    public static void main(String[] args) {
        criticalNeverDeferred();
        decorativeDefersUnderSoftPressure();
        starvationForcesDecorativeRun();
        hysteresisRecovery();
        starvationTrackerPromotion();
        System.out.println("RuntimeSchedulerTest OK");
    }

    private static void criticalNeverDeferred() {
        RuntimeBudgetController controller = new RuntimeBudgetController();
        SimulationTickBudget hard = new SimulationTickBudget(SimulationTickBudget.HEALTHY_MAX_NANOS);
        hard.recordSpent(SimulationTickBudget.HARD_LIMIT_NANOS + 1L);
        controller.endTick(hard);
        check(!controller.mayDefer(RuntimePriority.CRITICAL), "critical may not defer");
        check(controller.shouldRunNow(RuntimePriority.CRITICAL, false), "critical always runs");
    }

    private static void decorativeDefersUnderSoftPressure() {
        RuntimeBudgetController controller = new RuntimeBudgetController();
        SimulationTickBudget soft = new SimulationTickBudget(SimulationTickBudget.HEALTHY_MAX_NANOS);
        soft.recordSpent(SimulationTickBudget.SOFT_LIMIT_NANOS + 1L);
        controller.endTick(soft);
        check(!controller.shouldRunNow(RuntimePriority.DECORATIVE, false), "decorative deferred under soft");
        check(controller.shouldRunNow(RuntimePriority.NORMAL, false), "normal still runs under soft");
        check(controller.shouldRunNow(RuntimePriority.HIGH, false), "high still runs under soft");
    }

    private static void starvationForcesDecorativeRun() {
        RuntimeBudgetController controller = new RuntimeBudgetController();
        SimulationTickBudget hard = new SimulationTickBudget(SimulationTickBudget.HEALTHY_MAX_NANOS);
        hard.recordSpent(SimulationTickBudget.HARD_LIMIT_NANOS + 5L);
        controller.endTick(hard);
        check(!controller.shouldRunNow(RuntimePriority.DECORATIVE, false), "decorative blocked under hard");
        check(controller.shouldRunNow(RuntimePriority.DECORATIVE, true), "starvation promotes decorative");
    }

    private static void hysteresisRecovery() {
        RuntimeBudgetController controller = new RuntimeBudgetController();
        SimulationTickBudget spike = new SimulationTickBudget(SimulationTickBudget.HEALTHY_MAX_NANOS);
        spike.recordSpent(SimulationTickBudget.HARD_LIMIT_NANOS + 1L);
        controller.endTick(spike);
        check(controller.pressure() == RuntimeBudgetController.Pressure.HARD, "hard pressure latched");

        for (int i = 0; i < 3; i++) {
            SimulationTickBudget healthy = new SimulationTickBudget(SimulationTickBudget.HEALTHY_MAX_NANOS);
            healthy.recordSpent(2_000_000L);
            controller.endTick(healthy);
        }
        check(controller.pressure() != RuntimeBudgetController.Pressure.HEALTHY, "still recovering after few healthy ticks");

        for (int i = 0; i < 6; i++) {
            SimulationTickBudget healthy = new SimulationTickBudget(SimulationTickBudget.HEALTHY_MAX_NANOS);
            healthy.recordSpent(1_000_000L);
            controller.endTick(healthy);
        }
        check(controller.pressure() == RuntimeBudgetController.Pressure.HEALTHY, "healthy after hysteresis recovery");
    }

    private static void starvationTrackerPromotion() {
        RuntimeDeferTracker tracker = new RuntimeDeferTracker();
        String task = "presentation.settlement_ambience";
        int max = 4;
        for (int i = 0; i < max - 1; i++) {
            check(!tracker.registerDefer(task, max), "not starving yet");
        }
        check(tracker.registerDefer(task, max), "starvation threshold reached");
        tracker.clearDefer(task);
        check(tracker.consecutiveDeferred(task) == 0, "cleared after run");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
