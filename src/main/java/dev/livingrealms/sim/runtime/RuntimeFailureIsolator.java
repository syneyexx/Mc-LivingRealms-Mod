package dev.livingrealms.sim.runtime;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Isolates non-critical runtime task failures (Wave 34). */
public final class RuntimeFailureIsolator {
    public record Outcome(boolean ran, boolean failed, boolean disabled, boolean reenabled) {
        public static Outcome ranOk() { return new Outcome(true, false, false, false); }
        public static Outcome skippedWhileDisabled() { return new Outcome(false, false, true, false); }
        public static Outcome failedAndDisabled() { return new Outcome(true, true, true, false); }
    }

    private final ConcurrentHashMap<String, Long> disabledUntilTick = new ConcurrentHashMap<>();
    private final long disableTicks;
    private final StructuredErrorReporter reporter;

    public RuntimeFailureIsolator(long disableTicks, StructuredErrorReporter reporter) {
        if (disableTicks < 1) throw new IllegalArgumentException("disableTicks");
        this.disableTicks = disableTicks;
        this.reporter = Objects.requireNonNull(reporter, "reporter");
    }

    public boolean isDisabled(String taskId, long tick) {
        Long until = disabledUntilTick.get(taskId);
        if (until == null) return false;
        if (tick >= until) {
            disabledUntilTick.remove(taskId, until);
            return false;
        }
        return true;
    }

    public Outcome execute(String taskId, RuntimeTaskClass taskClass, long tick,
                           StructuredErrorReporter.Context context, Runnable body) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(taskClass, "taskClass");
        Objects.requireNonNull(body, "body");
        if (!taskClass.swallowFailures()) {
            body.run();
            return Outcome.ranOk();
        }
        if (isDisabled(taskId, tick)) return Outcome.skippedWhileDisabled();
        try {
            body.run();
            return Outcome.ranOk();
        } catch (RuntimeException failure) {
            disabledUntilTick.put(taskId, tick + disableTicks);
            StructuredErrorReporter.Context ctx = context == null
                    ? StructuredErrorReporter.Context.of("runtime", taskId, "disable_task_" + disableTicks + "_ticks")
                    : context;
            reporter.report(tick, ctx, failure);
            return Outcome.failedAndDisabled();
        }
    }

    public void clear() { disabledUntilTick.clear(); reporter.clear(); }
    public int disabledCount() { return disabledUntilTick.size(); }
    public StructuredErrorReporter reporter() { return reporter; }

    public static RuntimeFailureIsolator withLogging(long disableTicks, long logCooldownTicks, Consumer<String> sink) {
        return new RuntimeFailureIsolator(disableTicks, new StructuredErrorReporter(logCooldownTicks, sink));
    }
}
