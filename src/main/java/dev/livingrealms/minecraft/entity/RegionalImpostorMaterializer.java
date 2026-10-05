package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.config.RuntimeProjectionPolicy;
import dev.livingrealms.sim.presentation.RegionalImpostorPlanner;
import dev.livingrealms.sim.world.SimPosition;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Spawns/reconciles cheap regional impostors on a slow cadence (100–200 ticks). */
public final class RegionalImpostorMaterializer {
    private RegionalImpostorMaterializer() {}

    public static void tick(MinecraftServer server, LivingRealmsSavedData data) {
        ServerLevel level = server.overworld();
        List<SimPosition> players = level.players().stream().map(p -> new SimPosition(p.getX(), p.getZ())).toList();
        if (players.isEmpty()) {
            for (RegionalImpostorEntity e : RegionalImpostorIndex.loaded()) e.dematerialize();
            return;
        }
        var config = data.state().config();
        List<RegionalImpostorPlanner.Token> desired = RegionalImpostorPlanner.plan(
                data.state(),
                players,
                RuntimeProjectionPolicy.regionalImpostorInnerRadius(config),
                RuntimeProjectionPolicy.regionalImpostorOuterRadius(config),
                RuntimeProjectionPolicy.regionalImpostorBudget(config));
        Set<String> wanted = new HashSet<>();
        for (RegionalImpostorPlanner.Token t : desired) wanted.add(t.key());
        for (RegionalImpostorEntity e : RegionalImpostorIndex.loaded()) {
            if (!wanted.contains(e.projectionKey())) e.dematerialize();
        }
        for (RegionalImpostorPlanner.Token t : desired) {
            if (RegionalImpostorIndex.forKey(t.key()) != null) continue;
            int x = (int) Math.floor(t.x()), z = (int) Math.floor(t.z());
            BlockPos probe = new BlockPos(x, level.getSeaLevel(), z);
            if (!level.hasChunkAt(probe)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            RegionalImpostorEntity e = ModEntities.REGIONAL_IMPOSTOR.get().create(level);
            if (e == null) continue;
            e.initialize(mapKind(t.kind()), t.canonicalId(), t.slot(), t.rgb());
            e.moveTo(x + .5, y + 0.1, z + .5, 0, 0);
            level.addFreshEntity(e);
        }
    }

    private static RegionalImpostorEntity.Kind mapKind(RegionalImpostorPlanner.Kind kind) {
        return switch (kind) {
            case MILITARY_BANNER -> RegionalImpostorEntity.Kind.MILITARY_BANNER;
            case HERD -> RegionalImpostorEntity.Kind.HERD;
            case CARAVAN_DUST -> RegionalImpostorEntity.Kind.CARAVAN_DUST;
            case SETTLEMENT_BUSTLE -> RegionalImpostorEntity.Kind.SETTLEMENT_BUSTLE;
            case SAIL -> RegionalImpostorEntity.Kind.SAIL;
            case MIGRATION -> RegionalImpostorEntity.Kind.MIGRATION;
        };
    }
}
