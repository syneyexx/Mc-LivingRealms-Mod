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
        int[][] offsets = offsetsFor(site.type());
        int target = Math.max(1, Math.min(offsets.length, footprintBudget(site)));
        boolean touched = false;
        int placed = 0;
        for (int i = 0; i < offsets.length && placed < target; i++) {
            int idx = Math.floorMod(i + Long.hashCode(site.id()), offsets.length);
            int x = cx + offsets[idx][0], z = cz + offsets[idx][1];
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
            BlockState desired = blockFor(site, i);
            if (existing == AuthoredOwnerType.ROADSIDE_SITE
                    && (current.equals(desired) || isRoadsideMaterial(current))) {
                PLACED.put(pos.asLong(), site.id());
                placed++;
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
            if (!current.isAir() && !current.canBeReplaced() && !isRoadsideMaterial(current)) continue;
            if (WorldMutationGuard.trySetAuthored(level, pos, desired, ledger, AuthoredOwnerType.ROADSIDE_SITE, false, false)) {
                PLACED.put(pos.asLong(), site.id());
                placed++;
                touched = true;
            }
        }
        return touched || placed > 0;
    }

    private static int footprintBudget(RoadsideSite site) {
        return switch (site.lifecycle()) {
            case RUINED -> 2;
            case ABANDONED -> 3;
            case REPAIRED, ACTIVE -> switch (site.type()) {
                case WAYSTATION, TRAVELER_CAMP, SHEPHERD_CAMP, HUNTER_CAMP -> 6;
                case SHRINE, LOGGING_SITE, TOLL_POST, BATTLEFIELD_MEMORIAL -> 5;
                case MILESTONE, ABANDONED_CART, GALLOWS -> 3;
            };
        };
    }

    private static int[][] offsetsFor(RoadsideSite.Type type) {
        return switch (type) {
            case MILESTONE, ABANDONED_CART, GALLOWS -> new int[][]{{0, 0}, {1, 0}, {0, 1}};
            case SHRINE, TOLL_POST -> new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            default -> new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 1}, {-2, -1}, {1, -2}};
        };
    }

    private static BlockState blockFor(RoadsideSite site, int index) {
        boolean ruined = site.lifecycle() == RoadsideSite.Lifecycle.RUINED
                || site.lifecycle() == RoadsideSite.Lifecycle.ABANDONED;
        return switch (site.type()) {
            case WAYSTATION -> index == 0
                    ? (ruined ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.CAMPFIRE.defaultBlockState())
                    : (index == 1 ? Blocks.OAK_FENCE.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
            case SHRINE -> index == 0
                    ? Blocks.STONE_BRICKS.defaultBlockState()
                    : (index == 1 ? Blocks.TORCH.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
            case MILESTONE -> Blocks.COBBLESTONE_WALL.defaultBlockState();
            case TRAVELER_CAMP -> index == 0
                    ? Blocks.CAMPFIRE.defaultBlockState()
                    : (index % 2 == 0 ? Blocks.WHITE_WOOL.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState());
            case HUNTER_CAMP -> index == 0
                    ? Blocks.CAMPFIRE.defaultBlockState()
                    : Blocks.OAK_LOG.defaultBlockState();
            case SHEPHERD_CAMP -> index == 0
                    ? Blocks.HAY_BLOCK.defaultBlockState()
                    : Blocks.OAK_FENCE.defaultBlockState();
            case LOGGING_SITE -> index == 0
                    ? Blocks.STRIPPED_OAK_LOG.defaultBlockState()
                    : Blocks.OAK_LOG.defaultBlockState();
            case TOLL_POST -> index == 0
                    ? Blocks.OAK_FENCE.defaultBlockState()
                    : (index == 1 ? Blocks.WHITE_BANNER.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
            case ABANDONED_CART -> index == 0
                    ? Blocks.OAK_FENCE.defaultBlockState()
                    : Blocks.OAK_PLANKS.defaultBlockState();
            case BATTLEFIELD_MEMORIAL -> index == 0
                    ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                    : Blocks.STONE_BRICK_WALL.defaultBlockState();
            case GALLOWS -> index == 0
                    ? Blocks.OAK_FENCE.defaultBlockState()
                    : Blocks.OAK_FENCE.defaultBlockState();
        };
    }

    private static boolean isRoadsideMaterial(BlockState s) {
        return s.is(Blocks.CAMPFIRE) || s.is(Blocks.OAK_FENCE) || s.is(Blocks.OAK_PLANKS)
                || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.MOSSY_STONE_BRICKS) || s.is(Blocks.TORCH)
                || s.is(Blocks.COBBLESTONE_WALL) || s.is(Blocks.STONE_BRICK_WALL)
                || s.is(Blocks.WHITE_WOOL) || s.is(Blocks.OAK_LOG) || s.is(Blocks.STRIPPED_OAK_LOG)
                || s.is(Blocks.HAY_BLOCK) || s.is(Blocks.WHITE_BANNER)
                || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.COBBLESTONE);
    }
}
