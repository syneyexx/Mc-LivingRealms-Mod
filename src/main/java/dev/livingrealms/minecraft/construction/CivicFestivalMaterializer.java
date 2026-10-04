package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.civilization.CivicEvent;
import dev.livingrealms.sim.civilization.CivicFestivalDecorationPlanner;
import dev.livingrealms.sim.civilization.CivicFestivalDecorationPlanner.Decoration;
import dev.livingrealms.sim.civilization.CivicFestivalDecorationPlanner.Kind;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Temporary physical festival decorations for active civic events.
 * Only {@link AuthoredOwnerType#CIVIC_FESTIVAL} blocks are placed/removed; player builds are never adopted.
 */
public final class CivicFestivalMaterializer {
    private static final double ACTIVATION_RADIUS = 288.0D;
    private static final double ACTIVATION_RADIUS_SQR = ACTIVATION_RADIUS * ACTIVATION_RADIUS;
    private static final int MAX_OPS_PER_TICK = 48;
    /** packed block pos -> event id for authored festival cells we placed this session. */
    private static final Map<Long, Long> PLACED = new HashMap<>();

    private CivicFestivalMaterializer() {}

    public static void tick(ServerLevel level, LivingRealmsSavedData data) {
        if (level.players().isEmpty()) return;
        AuthoredBlockLedger ledger = data.authoredBlocks();
        Set<Long> activeEventIds = new HashSet<>();
        for (CivicEvent event : data.state().civicEvents()) if (event.active()) activeEventIds.add(event.id());

        int budget = MAX_OPS_PER_TICK;
        // Cleanup finished festivals first.
        var placedIt = PLACED.entrySet().iterator();
        while (placedIt.hasNext() && budget > 0) {
            var entry = placedIt.next();
            if (activeEventIds.contains(entry.getValue())) continue;
            BlockPos pos = BlockPos.of(entry.getKey());
            if (!level.hasChunkAt(pos)) continue;
            if (ledger.ownerType(pos.getX(), pos.getY(), pos.getZ()) == AuthoredOwnerType.CIVIC_FESTIVAL) {
                BlockState current = level.getBlockState(pos);
                if (isFestivalBlock(current)) {
                    level.removeBlock(pos, false);
                    ledger.forget(pos.getX(), pos.getY(), pos.getZ());
                    budget--;
                } else {
                    // Player replaced our decoration — forget ownership without destroying their block.
                    ledger.forget(pos.getX(), pos.getY(), pos.getZ());
                }
            }
            placedIt.remove();
        }

        for (Decoration decoration : CivicFestivalDecorationPlanner.planActive(data.state())) {
            if (budget <= 0) break;
            Settlement settlement = data.state().findSettlement(decoration.settlementId()).orElse(null);
            if (settlement == null) continue;
            SimPosition abs = CivicFestivalDecorationPlanner.absolutePosition(settlement, decoration);
            if (!nearPlayer(level, abs.x(), abs.z())) continue;
            BlockPos groundProbe = new BlockPos((int) Math.floor(abs.x()), level.getSeaLevel(), (int) Math.floor(abs.z()));
            if (!level.hasChunkAt(groundProbe)) continue;
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, groundProbe.getX(), groundProbe.getZ()) - 1;
            if (surface <= level.getMinBuildHeight() + 1 || surface >= level.getMaxBuildHeight() - 4) continue;
            BlockPos ground = new BlockPos(groundProbe.getX(), surface, groundProbe.getZ());
            BlockPos pos = ground.above();
            BlockState groundState = level.getBlockState(ground);
            BlockState current = level.getBlockState(pos);
            if (groundState.hasBlockEntity() || current.hasBlockEntity()) continue;
            if (!WorldMutationGuard.isNaturalTerrain(groundState)
                    && ledger.ownerType(ground.getX(), ground.getY(), ground.getZ()) == null
                    && !groundState.is(Blocks.DIRT_PATH)
                    && !groundState.is(Blocks.GRASS_BLOCK)
                    && !groundState.is(Blocks.COBBLESTONE)
                    && !groundState.is(Blocks.STONE_BRICKS)) {
                continue;
            }
            AuthoredOwnerType existing = ledger.ownerType(pos.getX(), pos.getY(), pos.getZ());
            if (existing != null && existing != AuthoredOwnerType.CIVIC_FESTIVAL) continue;
            BlockState desired = blockFor(decoration.kind(), decoration.eventId(), decoration.slot());
            if (existing == AuthoredOwnerType.CIVIC_FESTIVAL && (current.equals(desired) || isFestivalBlock(current))) {
                PLACED.put(pos.asLong(), decoration.eventId());
                continue;
            }
            if (!current.isAir() && !current.canBeReplaced() && !isFestivalBlock(current)) continue;
            if (WorldMutationGuard.trySetAuthored(level, pos, desired, ledger, AuthoredOwnerType.CIVIC_FESTIVAL, false, false)) {
                PLACED.put(pos.asLong(), decoration.eventId());
                budget--;
            }
        }
    }

    public static void clear() { PLACED.clear(); }

    private static boolean nearPlayer(ServerLevel level, double x, double z) {
        return level.players().stream().anyMatch(p -> {
            double dx = p.getX() - x, dz = p.getZ() - z;
            return dx * dx + dz * dz <= ACTIVATION_RADIUS_SQR;
        });
    }

    private static BlockState blockFor(Kind kind, long eventId, int slot) {
        int variant = Math.floorMod(Long.hashCode(eventId) + slot * 17, 3);
        return switch (kind) {
            case BANNER -> switch (variant) {
                case 0 -> Blocks.WHITE_BANNER.defaultBlockState();
                case 1 -> Blocks.RED_BANNER.defaultBlockState();
                default -> Blocks.BLUE_BANNER.defaultBlockState();
            };
            case TORCH -> Blocks.TORCH.defaultBlockState();
            case CARPET -> switch (variant) {
                case 0 -> Blocks.RED_CARPET.defaultBlockState();
                case 1 -> Blocks.YELLOW_CARPET.defaultBlockState();
                default -> Blocks.WHITE_CARPET.defaultBlockState();
            };
            case FLOWER -> switch (variant) {
                case 0 -> Blocks.POPPY.defaultBlockState();
                case 1 -> Blocks.OXEYE_DAISY.defaultBlockState();
                default -> Blocks.DANDELION.defaultBlockState();
            };
            case STALL -> Blocks.OAK_FENCE.defaultBlockState();
        };
    }

    private static boolean isFestivalBlock(BlockState state) {
        return state.is(Blocks.WHITE_BANNER) || state.is(Blocks.RED_BANNER) || state.is(Blocks.BLUE_BANNER)
                || state.is(Blocks.TORCH) || state.is(Blocks.RED_CARPET) || state.is(Blocks.YELLOW_CARPET)
                || state.is(Blocks.WHITE_CARPET) || state.is(Blocks.POPPY) || state.is(Blocks.OXEYE_DAISY)
                || state.is(Blocks.DANDELION) || state.is(Blocks.OAK_FENCE);
    }
}
