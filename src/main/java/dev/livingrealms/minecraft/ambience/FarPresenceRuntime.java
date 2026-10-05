package dev.livingrealms.minecraft.ambience;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.SimPosition;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Far-presence cues beyond road activation (~192) and before full construction LOD:
 * settlement smoke silhouettes and ghost road ribbons using cheap particles.
 */
public final class FarPresenceRuntime {
    private static final double SMOKE_MIN = 500;
    private static final double SMOKE_MAX = 700;
    private static final double ROAD_GHOST_MIN = 192;
    private static final double ROAD_GHOST_MAX = 520;

    private FarPresenceRuntime() {}

    public static void tick(ServerLevel level, LivingRealmsSavedData data, long tickCounter) {
        if (level.players().isEmpty()) return;
        for (ServerPlayer player : level.players()) {
            SimPosition from = new SimPosition(player.getX(), player.getZ());
            for (Faction faction : data.state().factions()) {
                for (Settlement settlement : faction.settlements()) {
                    double d = from.distanceTo(settlement.position());
                    if (d < SMOKE_MIN || d > SMOKE_MAX) continue;
                    if (((tickCounter + settlement.id()) & 7L) != 0) continue;
                    int x = (int) Math.round(settlement.position().x());
                    int z = (int) Math.round(settlement.position().z());
                    if (!level.hasChunkAt(new net.minecraft.core.BlockPos(x, level.getSeaLevel(), z))) continue;
                    int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x + .5, y + 8, z + .5, 2, .8, 1.2, .8, .01);
                }
            }
            if ((tickCounter & 15L) != 0) continue;
            int shown = 0;
            for (TransportRoute route : data.state().routes()) {
                if (!route.operational() || shown >= 8) break;
                Settlement a = data.state().findSettlement(route.fromSettlementId()).orElse(null);
                Settlement b = data.state().findSettlement(route.toSettlementId()).orElse(null);
                if (a == null || b == null) continue;
                // Sample midpoints along the route for ghost ribbons.
                for (int i = 1; i <= 3; i++) {
                    double t = i / 4.0;
                    double x = a.position().x() + (b.position().x() - a.position().x()) * t;
                    double z = a.position().z() + (b.position().z() - a.position().z()) * t;
                    double d = from.distanceTo(new SimPosition(x, z));
                    if (d < ROAD_GHOST_MIN || d > ROAD_GHOST_MAX) continue;
                    int ix = (int) Math.round(x), iz = (int) Math.round(z);
                    if (!level.hasChunkAt(new net.minecraft.core.BlockPos(ix, level.getSeaLevel(), iz))) continue;
                    int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);
                    level.sendParticles(ParticleTypes.ASH, ix + .5, y + .15, iz + .5, 1, .4, .02, .4, 0);
                    shown++;
                }
            }
        }
    }
}
