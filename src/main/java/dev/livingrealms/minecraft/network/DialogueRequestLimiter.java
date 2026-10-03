package dev.livingrealms.minecraft.network;
import dev.livingrealms.sim.util.TickRateLimiter;import java.util.UUID;
/** Prevents free-text chat packet spam on the integrated server. */
public final class DialogueRequestLimiter {private static final TickRateLimiter<UUID> LIMITER=new TickRateLimiter<>(3);private DialogueRequestLimiter(){}public static boolean allow(UUID id,int tick){return LIMITER.allow(id,tick);}public static void remove(UUID id){LIMITER.remove(id);}public static void clear(){LIMITER.clear();}}
