package dev.livingrealms.minecraft.ambience;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Bounded settlement ambience/VFX near players. Uses vanilla particles/sounds only —
 * never becomes a second simulation authority.
 */
public final class SettlementAmbienceRuntime {
    private SettlementAmbienceRuntime() {}

    public static void tick(ServerLevel level, LivingRealmsSavedData data, long tickCounter) {
        if (tickCounter % 40L != 0) return;
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        var state = data.state();
        for (ServerPlayer player : players) {
            SimPosition at = new SimPosition(player.getX(), player.getZ());
            Settlement nearest = null;
            double best = Double.POSITIVE_INFINITY;
            for (Faction faction : state.factions()) {
                for (Settlement settlement : faction.settlements()) {
                    double d = settlement.position().distanceTo(at);
                    if (d < best) {
                        best = d;
                        nearest = settlement;
                    }
                }
            }
            if (nearest == null || best > 96) continue;
            emitNear(level, nearest, player, best, tickCounter);
        }
    }

    private static void emitNear(ServerLevel level, Settlement settlement, ServerPlayer player, double distance, long tick) {
        int x = (int) Math.floor(settlement.position().x());
        int z = (int) Math.floor(settlement.position().z());
        BlockPos probe = new BlockPos(x, level.getSeaLevel(), z);
        if (!level.hasChunkAt(probe)) return;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        boolean night = level.getDayTime() % 24000L > 13000L;
        boolean market = settlement.isConstructionCompleted("market:0") || settlement.completedConstruction().stream().anyMatch(k -> k.startsWith("market:"));
        boolean industry = settlement.completedConstruction().stream().anyMatch(k -> k.startsWith("factory:") || k.startsWith("workshop:"));
        boolean harbor = settlement.completedConstruction().stream().anyMatch(k -> k.startsWith("dock:") || k.startsWith("port:"));
        boolean festival = settlement.completedConstruction().stream().anyMatch(k -> k.contains("festival") || k.startsWith("plaza:"));

        // Smoke/chimney dust near industrial districts — performance-bounded.
        if (industry && tick % 80L == 0 && distance < 64) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x + 2.5, y + 6, z + 1.5, 2, 0.4, 0.6, 0.4, 0.01);
        }
        // Harbor spray / ambient water cue.
        if (harbor && tick % 120L == 0 && distance < 72) {
            level.sendParticles(ParticleTypes.SPLASH, x - 3.0, y + 1.0, z + 4.0, 4, 0.8, 0.2, 0.8, 0.02);
            if (distance < 48) {
                level.playSound(null, x, y, z, SoundEvents.WATER_AMBIENT, SoundSource.AMBIENT, 0.25F, 1.0F);
            }
        }
        // Market bustle cue daytime only.
        if (market && !night && tick % 160L == 0 && distance < 40) {
            level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.AMBIENT, 0.15F, 1.05F);
        }
        // Night security hush — occasional torch spark near center.
        if (night && tick % 200L == 0 && distance < 56) {
            level.sendParticles(ParticleTypes.FLAME, x + 0.5, y + 2.2, z + 0.5, 1, 0.05, 0.05, 0.05, 0.0);
        }
        // Festival sparkle when plazas exist and settlement is prosperous.
        if (festival && settlement.prosperity() > .55 && tick % 100L == 0 && distance < 48) {
            level.sendParticles(ParticleTypes.END_ROD, x + 0.5, y + 3.0, z + 0.5, 3, 1.2, 0.8, 1.2, 0.01);
        }
        // Construction dust when infrastructure is mid-range (active growth signal).
        if (settlement.infrastructure() > .2 && settlement.infrastructure() < .85 && tick % 140L == 0 && distance < 60) {
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0.3, 0.05, 0.3, 0.01);
        }
    }
}
