package dev.livingrealms.minecraft.runtime;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.minecraft.SpeciesDataRegistry;
import dev.livingrealms.sim.runtime.RuntimeBudgetController;
import dev.livingrealms.sim.runtime.RuntimeDeferTracker;
import dev.livingrealms.sim.runtime.RuntimePressureBridge;
import dev.livingrealms.sim.runtime.RuntimeTelemetryRegistry;
import dev.livingrealms.sim.runtime.SimulationTickBudget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.MinecraftServer;

public final class LivingRealmsRuntimeScheduler {
    private final List<RuntimeTask> tasks;
    private final RuntimeBudgetController budgetController = new RuntimeBudgetController();
    private final RuntimeDeferTracker deferTracker = new RuntimeDeferTracker();
    private final RuntimeTelemetryRegistry telemetry = new RuntimeTelemetryRegistry();
    private final LivingRealmsRuntimeTaskCatalog.SpeciesReloadState speciesReloadState =
            new LivingRealmsRuntimeTaskCatalog.SpeciesReloadState();
    private long tickCounter;

    public LivingRealmsRuntimeScheduler() {
        this.tasks = List.copyOf(LivingRealmsRuntimeTaskCatalog.defaultTasks(speciesReloadState));
    }

    public RuntimeTelemetryRegistry telemetry() {
        return telemetry;
    }

    public long tickCounter() {
        return tickCounter;
    }

    public void reset() {
        tickCounter = 0L;
        speciesReloadState.reset();
        deferTracker.clearAll();
        budgetController.reset();
        RuntimePressureBridge.reset();
        telemetry.resetAll();
    }

    public void tick(MinecraftServer server) {
        if (!SpeciesDataRegistry.ready()) {
            return;
        }
        tickCounter++;
        SimulationTickBudget budget = SimulationTickBudget.forHealthyTick();
        budgetController.beginTick(budget);
        RuntimePressureBridge.publish(budgetController.pressure());
        var data = SimulationRuntime.data(server);
        RuntimeTaskContext ctx = new RuntimeTaskContext(server, data, tickCounter, budget);

        List<RuntimeTask> due = new ArrayList<>();
        for (RuntimeTask task : tasks) {
            if (task.isDue(tickCounter)) {
                due.add(task);
            }
        }
        due.sort(Comparator
                .comparingInt((RuntimeTask task) -> task.priority().ordinal())
                .thenComparing(RuntimeTask::id));

        for (RuntimeTask task : due) {
            boolean starvation = deferTracker.consecutiveDeferred(task.id()) >= task.maxDeferredTicks();
            if (!budgetController.shouldRunNow(task.priority(), starvation)) {
                if (budgetController.mayDefer(task.priority())) {
                    deferTracker.registerDefer(task.id(), task.maxDeferredTicks());
                    telemetry.domain(task.domain()).recordDeferred();
                    continue;
                }
            }
            if (!budget.hasRemaining() && !task.critical() && !starvation) {
                if (budgetController.mayDefer(task.priority())) {
                    deferTracker.registerDefer(task.id(), task.maxDeferredTicks());
                    telemetry.domain(task.domain()).recordDeferred();
                    continue;
                }
            }

            long started = System.nanoTime();
            task.execute(ctx);
            long elapsed = System.nanoTime() - started;
            budget.recordSpent(elapsed);
            telemetry.domain(task.domain()).recordExecution(elapsed);
            if (starvation) {
                telemetry.domain(task.domain()).recordStarvationRun();
            }
            deferTracker.clearDefer(task.id());
        }

        budgetController.endTick(budget);
    }
}
