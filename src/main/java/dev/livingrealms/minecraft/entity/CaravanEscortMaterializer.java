package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.config.RuntimeProjectionPolicy;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.military.MilitaryUnitClass;
import dev.livingrealms.sim.world.SimPosition;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Near-player escort guards for high-value caravans. Canonical escort strength stays on TradeShipment;
 * these entities are temporary projections only, namespaced as {@code armyId = -shipmentId}.
 */
public final class CaravanEscortMaterializer {
    private static final int MAX_ESCORTS = 24;

    private CaravanEscortMaterializer() {}

    public static void tick(MinecraftServer server, LivingRealmsSavedData data) {
        ServerLevel level = server.overworld();
        List<SimPosition> players = level.players().stream().map(p -> new SimPosition(p.getX(), p.getZ())).toList();
        if (players.isEmpty()) return;
        double radius = RuntimeProjectionPolicy.escortRadiusBlocks(data.state().config());
        Set<String> wanted = new HashSet<>();
        int spawned = 0;
        for (TradeShipment shipment : data.state().shipments()) {
            if (shipment.arrived() || shipment.escortStrength() < .45 || shipment.lossState() == TradeShipment.LossState.TOTAL) continue;
            SimPosition pos = shipment.position();
            if (players.stream().noneMatch(p -> p.distanceTo(pos) <= radius)) continue;
            int guards = Math.min(4, Math.max(1, (int) Math.round(shipment.escortStrength() * 3)));
            for (int slot = 0; slot < guards && spawned < MAX_ESCORTS; slot++) {
                String key = "escort:" + shipment.id() + ":" + slot;
                wanted.add(key);
                if (MilitaryUnitIndex.forSlot(-shipment.id(), slot) != null) {
                    spawned++;
                    continue;
                }
                double ang = (Math.PI * 2 * slot) / guards;
                int x = (int) Math.floor(pos.x() + Math.cos(ang) * 4);
                int z = (int) Math.floor(pos.z() + Math.sin(ang) * 4);
                BlockPos probe = new BlockPos(x, level.getSeaLevel(), z);
                if (!level.hasChunkAt(probe)) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                MilitaryUnitEntity e = ModEntities.MILITARY_UNIT.get().create(level);
                if (e == null) continue;
                e.initializeEscortProjection(shipment.id(), shipment.sellerFactionId(), slot, MilitaryUnitClass.INFANTRY);
                e.moveTo(x + .5, y, z + .5, 0, 0);
                if (level.noCollision(e)) {
                    level.addFreshEntity(e);
                    spawned++;
                }
            }
        }
        for (MilitaryUnitEntity e : MilitaryUnitIndex.loaded()) {
            if (!e.isEscort()) continue;
            String key = "escort:" + e.escortShipmentId() + ":" + e.projectionSlot();
            if (!wanted.contains(key)) e.dematerialize();
        }
    }
}
