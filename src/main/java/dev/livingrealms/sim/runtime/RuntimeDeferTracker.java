package dev.livingrealms.sim.runtime;

import java.util.HashMap;
import java.util.Map;

/** Tracks consecutive deferrals per task id for starvation promotion. */
public final class RuntimeDeferTracker {
    private final Map<String, Integer> consecutiveDeferred = new HashMap<>();

    public boolean registerDefer(String taskId, int maxDeferredTicks) {
        int next = consecutiveDeferred.getOrDefault(taskId, 0) + 1;
        consecutiveDeferred.put(taskId, next);
        return next >= Math.max(1, maxDeferredTicks);
    }

    public void clearDefer(String taskId) {
        consecutiveDeferred.remove(taskId);
    }

    public int consecutiveDeferred(String taskId) {
        return consecutiveDeferred.getOrDefault(taskId, 0);
    }

    public void clearAll() {
        consecutiveDeferred.clear();
    }
}
