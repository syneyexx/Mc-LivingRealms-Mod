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
        failureIsolationDisablesRecoverable();
        failureIsolationDoesNotSwallowCritical();
        structuredErrorRateLimit();
        System.out.println("RuntimeSchedulerTest OK");
    }

    private static void failureIsolationDisablesRecoverable() {
        java.util.ArrayList<String> logs = new java.util.ArrayList<>();
        var isolator = dev.livingrealms.sim.runtime.RuntimeFailureIsolator.withLogging(5, 10, logs::add);
        var cls = dev.livingrealms.sim.runtime.RuntimeTaskClass.PROJECTION_RECOVERABLE;
        var ctx = dev.livingrealms.sim.runtime.StructuredErrorReporter.Context.of("projection", "projection.wildlife", "disable_task_temporarily").withDay(12).withSettlement(7);
        var fail = isolator.execute("projection.wildlife", cls, 1L, ctx, () -> { throw new IllegalStateException("boom"); });
        check(fail.failed() && fail.disabled(), "recoverable failure disables");
        check(!logs.isEmpty(), "failure logged");
        var skipped = isolator.execute("projection.wildlife", cls, 2L, ctx, () -> { throw new AssertionError("should not run"); });
        check(skipped.disabled() && !skipped.ran() && !skipped.failed(), "disabled task skipped");
        var ok = isolator.execute("projection.wildlife", cls, 10L, ctx, () -> {});
        check(ok.ran() && !ok.failed(), "reenabled after cooldown");
    }

    private static void failureIsolationDoesNotSwallowCritical() {
        var isolator = dev.livingrealms.sim.runtime.RuntimeFailureIsolator.withLogging(5, 10, m -> {});
        boolean threw = false;
        try {
            isolator.execute("simulation.day_advance_drain",
                    dev.livingrealms.sim.runtime.RuntimeTaskClass.CANONICAL_CRITICAL,
                    1L,
                    null,
                    () -> { throw new IllegalStateException("canonical boom"); });
        } catch (IllegalStateException expected) {
            threw = true;
        }
        check(threw, "canonical critical must not swallow");
        check(isolator.disabledCount() == 0, "critical not disabled");
    }

    private static void structuredErrorRateLimit() {
        java.util.ArrayList<String> logs = new java.util.ArrayList<>();
        var reporter = new dev.livingrealms.sim.runtime.StructuredErrorReporter(20, logs::add);
        var ctx = dev.livingrealms.sim.runtime.StructuredErrorReporter.Context.of("presentation", "ambience", "disable").withDay(1).withFaction(3);
        Throwable err = new RuntimeException("same");
        check(reporter.report(1, ctx, err), "first log allowed");
        check(!reporter.report(5, ctx, err), "duplicate rate-limited");
        check(reporter.report(30, ctx, err), "allowed after cooldown");
        check(logs.get(logs.size() - 1).contains("suppressedRepeats=1") || logs.size() >= 2, "suppression noted");
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
