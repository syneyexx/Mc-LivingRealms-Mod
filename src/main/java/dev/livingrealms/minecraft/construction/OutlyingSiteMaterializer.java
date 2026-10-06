package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.world.OutlyingSite;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Chunk-driven physical fabric for canonical {@link OutlyingSite} records.
 *
 * <p>Outlying sites are explicitly not settlements and never affect settlement spacing.
 * This materializer never force-loads chunks and never uses player distance as block authority.</p>
 */
public final class OutlyingSiteMaterializer {
    private static final int MAX_SITES_PER_TICK = 8;
    private static final int MAX_SCANNED_PER_TICK = 48;
    private static int cursor;

    private OutlyingSiteMaterializer() {}

    public static void tick(ServerLevel level, LivingRealmsSavedData data) {
        List<OutlyingSite> sites = data.state().outlyingSites();
        if (sites.isEmpty()) return;
        cursor = Math.floorMod(cursor, sites.size());
        int scanned = 0;
        int materialized = 0;
        AuthoredBlockLedger ledger = data.authoredBlocks();

        for (int n = 0; n < sites.size()
                && scanned < MAX_SCANNED_PER_TICK
                && materialized < MAX_SITES_PER_TICK; n++) {
            OutlyingSite site = sites.get(Math.floorMod(cursor + n, sites.size()));
            scanned++;
            if (!site.active()) continue;
            if (!centerChunkLoaded(level, site)) continue;
            if (materialize(level, ledger, site)) materialized++;
        }
        cursor = Math.floorMod(cursor + Math.max(1, scanned), sites.size());
    }

    public static void clear() {
        cursor = 0;
    }

    private static boolean centerChunkLoaded(ServerLevel level, OutlyingSite site) {
        BlockPos probe = new BlockPos((int)Math.floor(site.position().x()), level.getSeaLevel(),
                (int)Math.floor(site.position().z()));
        return level.hasChunkAt(probe);
    }

    private static boolean materialize(ServerLevel level, AuthoredBlockLedger ledger, OutlyingSite site) {
        int cx = (int)Math.floor(site.position().x());
        int cz = (int)Math.floor(site.position().z());
        int[][] footprint = footprint(site.type());
        boolean present = false;
        boolean touched = false;

        for (int i = 0; i < footprint.length; i++) {
            int x = cx + footprint[i][0];
            int z = cz + footprint[i][1];
            BlockPos probe = new BlockPos(x, level.getSeaLevel(), z);
            if (!level.hasChunkAt(probe)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 5) continue;
            BlockPos pos = new BlockPos(x, y + 1, z);
            if (level.getBlockState(pos).hasBlockEntity()) continue;

            BlockState desired = blockFor(site.type(), i);
            AuthoredOwnerType owner = ledger.ownerType(pos.getX(), pos.getY(), pos.getZ());
            BlockState current = level.getBlockState(pos);
            if (owner == AuthoredOwnerType.OUTLYING_SITE) {
                present = true;
                if (current.equals(desired)) continue;
            } else if (!current.isAir() && !current.canBeReplaced()) {
                continue;
            }

            if (WorldMutationGuard.trySetAuthored(level, pos, desired, ledger,
                    AuthoredOwnerType.OUTLYING_SITE, false, false)) {
                touched = true;
                present = true;
            }
        }
        return touched || present;
    }

    private static int[][] footprint(OutlyingSite.Type type) {
        return switch (type) {
            case FOREIGN_HAMLET -> new int[][]{{0,0},{2,0},{-2,0},{0,2},{0,-2},{3,2},{-3,-2},{2,-3}};
            case FARMSTEAD -> new int[][]{{0,0},{2,0},{-2,0},{0,2},{0,-2},{3,0},{-3,0}};
            case MINING_CAMP -> new int[][]{{0,0},{2,0},{-2,0},{0,2},{0,-2},{2,2},{-2,-2}};
            case ROAD_STATION -> new int[][]{{0,0},{2,0},{-2,0},{0,2},{0,-2}};
            case MILITARY_POST -> new int[][]{{0,0},{2,0},{-2,0},{0,2},{0,-2},{2,2},{2,-2},{-2,2},{-2,-2}};
            case REFUGEE_CAMP -> new int[][]{{0,0},{3,0},{-3,0},{0,3},{0,-3},{3,3},{-3,-3}};
        };
    }

    private static BlockState blockFor(OutlyingSite.Type type, int index) {
        return switch (type) {
            case FOREIGN_HAMLET -> index == 0
                    ? Blocks.CAMPFIRE.defaultBlockState()
                    : (index % 3 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
            case FARMSTEAD -> index == 0
                    ? Blocks.COMPOSTER.defaultBlockState()
                    : (index % 2 == 0 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState());
            case MINING_CAMP -> index == 0
                    ? Blocks.CAMPFIRE.defaultBlockState()
                    : (index % 2 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.OAK_LOG.defaultBlockState());
            case ROAD_STATION -> index == 0
                    ? Blocks.LANTERN.defaultBlockState()
                    : Blocks.OAK_FENCE.defaultBlockState();
            case MILITARY_POST -> index == 0
                    ? Blocks.WHITE_BANNER.defaultBlockState()
                    : (index % 2 == 0 ? Blocks.COBBLESTONE_WALL.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState());
            case REFUGEE_CAMP -> index == 0
                    ? Blocks.CAMPFIRE.defaultBlockState()
                    : (index % 2 == 0 ? Blocks.WHITE_WOOL.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState());
        };
    }
}
