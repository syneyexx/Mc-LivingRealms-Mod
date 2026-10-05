package dev.livingrealms.minecraft.presentation;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.minecraft.entity.FactionCitizenEntity;
import dev.livingrealms.minecraft.entity.FactionCitizenIndex;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.presentation.CivicChoreographyDirector;
import dev.livingrealms.sim.presentation.CivicChoreographyDirector.CooldownBook;
import dev.livingrealms.sim.presentation.CivicChoreographyDirector.LoadedCitizen;
import dev.livingrealms.sim.presentation.CivicChoreographyDirector.Order;
import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Physical runtime for civic choreography cues. Moves already-loaded representative citizens
 * toward relevant sites near players. No chunk force-loads, no global pathfinding, no economy writes.
 */
public final class CivicChoreographyRuntime {
    private static final CooldownBook COOLDOWNS = new CooldownBook();
    private static final Map<Long, Long> LAST_SETTLEMENT_TICK = new HashMap<>();
    private static final long SETTLEMENT_SCAN_COOLDOWN = 40L;
    private static final int MAX_SETTLEMENTS_PER_TICK = 4;
    private static final int MAX_ORDERS_PER_TICK = 24;

    private CivicChoreographyRuntime() {}

    public static void tick(ServerLevel level, LivingRealmsSavedData data, long tickCounter) {
        if (level == null || data == null) return;
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        List<FactionCitizenEntity> loaded = FactionCitizenIndex.loaded().stream()
                .filter(e -> e.isAlive() && !e.isRemoved() && e.settlementId() > 0)
                .toList();
        if (loaded.isEmpty()) return;

        var state = data.state();
        int ordersBudget = MAX_ORDERS_PER_TICK;
        int settlementsBudget = MAX_SETTLEMENTS_PER_TICK;

        for (Faction faction : state.factions()) {
            if (settlementsBudget <= 0 || ordersBudget <= 0) break;
            for (Settlement settlement : faction.settlements()) {
                if (settlementsBudget <= 0 || ordersBudget <= 0) break;
                if (!nearAnyPlayer(players, settlement.position(), CivicChoreographyDirector.NEAR_PLAYER_RADIUS)) {
                    continue;
                }
                Long last = LAST_SETTLEMENT_TICK.get(settlement.id());
                if (last != null && tickCounter - last < SETTLEMENT_SCAN_COOLDOWN) continue;

                BlockPos probe = new BlockPos(
                        (int) Math.floor(settlement.position().x()),
                        level.getSeaLevel(),
                        (int) Math.floor(settlement.position().z()));
                // Never force-load — only choreograph when the settlement chunk is already present.
                if (!level.hasChunkAt(probe)) continue;

                List<LoadedCitizen> citizens = new ArrayList<>();
                List<FactionCitizenEntity> entities = new ArrayList<>();
                for (FactionCitizenEntity entity : loaded) {
                    if (entity.settlementId() != settlement.id()) continue;
                    if (entity.isSpeaking()) continue;
                    citizens.add(new LoadedCitizen(entity.citizenId(), entity.projectionSlot(), entity.role()));
                    entities.add(entity);
                }
                if (citizens.isEmpty()) continue;

                List<Order> orders = CivicChoreographyDirector.planOrders(
                        state, faction, settlement, citizens, tickCounter, COOLDOWNS);
                LAST_SETTLEMENT_TICK.put(settlement.id(), tickCounter);
                settlementsBudget--;
                if (orders.isEmpty()) continue;

                for (Order order : orders) {
                    if (ordersBudget <= 0) break;
                    FactionCitizenEntity entity = findEntity(entities, order);
                    if (entity == null) continue;
                    dispatch(level, entity, order);
                    ordersBudget--;
                }
            }
        }
    }

    public static void clear() {
        COOLDOWNS.clear();
        LAST_SETTLEMENT_TICK.clear();
    }

    /** Exposed for headless/core tests of cooldown book wiring. */
    public static CooldownBook cooldowns() {
        return COOLDOWNS;
    }

    private static void dispatch(ServerLevel level, FactionCitizenEntity entity, Order order) {
        int x = (int) Math.floor(order.targetX());
        int z = (int) Math.floor(order.targetZ());
        BlockPos probe = new BlockPos(x, level.getSeaLevel(), z);
        if (!level.hasChunkAt(probe)) return;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        // Local navigation only — PathfinderMob handles short walks; no custom global A*.
        entity.getNavigation().moveTo(x + 0.5D, y, z + 0.5D, order.speed());
    }

    private static FactionCitizenEntity findEntity(List<FactionCitizenEntity> entities, Order order) {
        for (FactionCitizenEntity entity : entities) {
            if (order.citizenId() > 0 && entity.citizenId() == order.citizenId()) return entity;
            if (entity.projectionSlot() == order.projectionSlot()) return entity;
        }
        return null;
    }

    private static boolean nearAnyPlayer(List<ServerPlayer> players, SimPosition pos, double radius) {
        double r2 = radius * radius;
        for (ServerPlayer player : players) {
            double dx = player.getX() - pos.x();
            double dz = player.getZ() - pos.z();
            if (dx * dx + dz * dz <= r2) return true;
        }
        return false;
    }
}
