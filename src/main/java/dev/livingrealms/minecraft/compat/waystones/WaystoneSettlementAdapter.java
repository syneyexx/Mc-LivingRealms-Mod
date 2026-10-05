package dev.livingrealms.minecraft.compat.waystones;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.minecraft.compat.WaystoneSettlementRuntime;
import net.minecraft.server.level.ServerLevel;

/**
 * Wave 22 — Waystones soft-compat adapter boundary.
 * Optional Waystones must not leak into canonical sim; this facade keeps calls at the MC edge.
 */
public final class WaystoneSettlementAdapter {
    private WaystoneSettlementAdapter() {}

    public static void tick(ServerLevel level, LivingRealmsSavedData data) {
        WaystoneSettlementRuntime.tick(level, data);
    }
}
