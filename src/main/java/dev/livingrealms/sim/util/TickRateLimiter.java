package dev.livingrealms.sim.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Small thread-safe tick based rate limiter.
 *
 * <p>A backwards tick value is treated as a clock/server reset and starts a new window. This is
 * important for integrated singleplayer where a Minecraft client process can host multiple logical
 * servers/worlds over its lifetime.</p>
 */
public final class TickRateLimiter<K> {
    private final int minTicksBetweenActions;
    private final Map<K, Integer> lastTick = new HashMap<>();

    public TickRateLimiter(int minTicksBetweenActions) {
        if (minTicksBetweenActions < 1) throw new IllegalArgumentException("minTicksBetweenActions");
        this.minTicksBetweenActions = minTicksBetweenActions;
    }

    public synchronized boolean allow(K key, int currentTick) {
        Objects.requireNonNull(key, "key");
        Integer previous = lastTick.get(key);
        if (previous != null && currentTick >= previous && currentTick - previous < minTicksBetweenActions) return false;
        lastTick.put(key, currentTick);
        return true;
    }

    public synchronized void remove(K key) {
        if (key != null) lastTick.remove(key);
    }

    public synchronized void clear() {
        lastTick.clear();
    }

    public synchronized int trackedKeys() {
        return lastTick.size();
    }
}
