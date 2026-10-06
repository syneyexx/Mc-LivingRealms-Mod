package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.world.RoadsideSite;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Sparse physical projection for canonical {@link RoadsideSite} records.
 * Uses typed provenance; never force-loads chunks; never adopts player builds;
 * never participates in settlement spacing.
 */
public final class RoadsideSiteMaterializer {
    private static final int MAX_OPS_PER_TICK = 24;
    private static final int MAX_SCANNED_PER_TICK = 64;
    /** packed block pos -> site id for authored roadside cells this session. */
    private static final Map<Long, Long> PLACED = new HashMap<>();
    private static int cursor;

    private RoadsideSiteMaterializer() {}

    public static void tick(ServerLevel level, LivingRealmsSavedData data) {
        AuthoredBlockLedger ledger = data.authoredBlocks();
        List<RoadsideSite> sites = data.state().roadsideSites();
        if (sites.isEmpty()) return;
        cursor = Math.floorMod(cursor, sites.size());
        int budget = MAX_OPS_PER_TICK;
        int scanned = 0;
        for (int n = 0; n < sites.size() && scanned < MAX_SCANNED_PER_TICK && budget > 0; n++) {
            RoadsideSite site = sites.get(Math.floorMod(cursor + n, sites.size()));
            scanned++;
            if (!physicallyPresent(site)) continue;
            // materializeSite() refuses unloaded columns, so this loop never force-loads terrain.
            if (materializeSite(level, ledger, site)) budget--;
        }
        cursor = Math.floorMod(cursor + Math.max(1, scanned), sites.size());
    }

    public static void clear() { PLACED.clear(); cursor = 0; }

    private static boolean physicallyPresent(RoadsideSite site) {
        if (site.active()) return true;
        return site.lifecycle() == RoadsideSite.Lifecycle.RUINED
                || site.lifecycle() == RoadsideSite.Lifecycle.ABANDONED;
    }

    private static boolean materializeSite(ServerLevel level, AuthoredBlockLedger ledger, RoadsideSite site) {
        int cx = (int) Math.floor(site.position().x());
        int cz = (int) Math.floor(site.position().z());
        BlockPos probe = new BlockPos(cx, level.getSeaLevel(), cz);
        if (!level.hasChunkAt(probe)) return false;
        boolean touched = false;
        int present = 0;
        for (RoadsideSiteTemplate.Placement placement : RoadsideSiteTemplate.placements(site)) {
            int x = cx + placement.dx(), z = cz + placement.dz();
            BlockPos column = new BlockPos(x, level.getSeaLevel(), z);
            if (!level.hasChunkAt(column)) continue;
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (surface <= level.getMinBuildHeight() + 1 || surface >= level.getMaxBuildHeight() - 5) continue;
            BlockPos ground = new BlockPos(x, surface, z);
            BlockPos pos = ground.above();
            BlockState groundState = level.getBlockState(ground);
            BlockState current = level.getBlockState(pos);
            if (groundState.hasBlockEntity()) continue;
            AuthoredOwnerType existing = ledger.ownerType(pos.getX(), pos.getY(), pos.getZ());
            BlockState desired = placement.state();
            if (existing == AuthoredOwnerType.ROADSIDE_SITE
                    && (current.equals(desired) || RoadsideSiteTemplate.isRoadsideMaterial(current))) {
                PLACED.put(pos.asLong(), site.id());
                present++;
                continue;
            }
            if (current.hasBlockEntity()) continue;
            if (!WorldMutationGuard.isNaturalTerrain(groundState)
                    && ledger.ownerType(ground.getX(), ground.getY(), ground.getZ()) == null
                    && !groundState.is(Blocks.DIRT_PATH)
                    && !groundState.is(Blocks.GRASS_BLOCK)
                    && !groundState.is(Blocks.COBBLESTONE)
                    && !groundState.is(Blocks.STONE_BRICKS)
                    && !groundState.is(Blocks.MOSSY_COBBLESTONE)) {
                continue;
            }
            if (existing != null && existing != AuthoredOwnerType.ROADSIDE_SITE) continue;
            // Respect player builds: never overwrite non-replaceable foreign blocks.
            if (!current.isAir() && !current.canBeReplaced() && !RoadsideSiteTemplate.isRoadsideMaterial(current)) continue;
            if (WorldMutationGuard.trySetAuthored(level, pos, desired, ledger, AuthoredOwnerType.ROADSIDE_SITE, false, false)) {
                PLACED.put(pos.asLong(), site.id());
                present++;
                touched = true;
            }
        }
        return touched || present > 0;
    }


}
