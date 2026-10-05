package dev.livingrealms.minecraft.runtime;

import dev.livingrealms.sim.runtime.RuntimeDomain;
import dev.livingrealms.sim.runtime.RuntimePriority;
import dev.livingrealms.sim.runtime.RuntimeTaskClass;

public interface RuntimeTask {
    String id();

    RuntimeDomain domain();

    RuntimePriority priority();

    /** Expected cadence when not deferred. */
    int normalIntervalTicks();

    /** Consecutive deferrals before starvation promotion forces execution. */
    int maxDeferredTicks();

    default boolean critical() {
        return priority() == RuntimePriority.CRITICAL;
    }

    /** Wave 34 failure classification. */
    default RuntimeTaskClass failureClass() {
        if (critical() || priority() == RuntimePriority.CRITICAL) {
            return RuntimeTaskClass.CANONICAL_CRITICAL;
        }
        return switch (domain()) {
            case SIMULATION, CRITICAL -> RuntimeTaskClass.CANONICAL_CRITICAL;
            case CONSTRUCTION, MAINTENANCE -> RuntimeTaskClass.RUNTIME_CRITICAL;
            case PROJECTION, DISCOVERY -> RuntimeTaskClass.PROJECTION_RECOVERABLE;
            case PRESENTATION -> RuntimeTaskClass.PRESENTATION_OPTIONAL;
        };
    }

    boolean isDue(long tickCounter);

    void execute(RuntimeTaskContext ctx);
}
