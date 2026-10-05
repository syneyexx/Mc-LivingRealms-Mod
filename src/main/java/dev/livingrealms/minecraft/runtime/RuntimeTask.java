package dev.livingrealms.minecraft.runtime;

import dev.livingrealms.sim.runtime.RuntimeDomain;
import dev.livingrealms.sim.runtime.RuntimePriority;

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

    boolean isDue(long tickCounter);

    void execute(RuntimeTaskContext ctx);
}
