package dev.livingrealms.minecraft.network;

import dev.livingrealms.sim.util.TickRateLimiter;
import java.util.UUID;

/** Small abuse guard: dashboard reconstruction is limited per connected player. */
public final class DashboardRequestLimiter {
    private static final TickRateLimiter<UUID> LIMITER = new TickRateLimiter<>(5);

    private DashboardRequestLimiter() {}

    public static boolean allow(UUID playerId, int currentTick) {
        return LIMITER.allow(playerId, currentTick);
    }

    public static void remove(UUID playerId) {
        LIMITER.remove(playerId);
    }

    public static void clear() {
        LIMITER.clear();
    }
}
