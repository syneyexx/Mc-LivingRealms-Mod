package dev.livingrealms.minecraft.network;

import dev.livingrealms.sim.util.TickRateLimiter;
import java.util.UUID;

/** Small anti-spam guard for mutating dashboard actions. Server tick based and per-player. */
public final class DashboardActionLimiter {
    private static final TickRateLimiter<UUID> LIMITER = new TickRateLimiter<>(4);

    private DashboardActionLimiter() {}

    public static boolean allow(UUID player, int tick) {
        return LIMITER.allow(player, tick);
    }

    public static void remove(UUID player) {
        LIMITER.remove(player);
    }

    public static void clear() {
        LIMITER.clear();
    }
}
