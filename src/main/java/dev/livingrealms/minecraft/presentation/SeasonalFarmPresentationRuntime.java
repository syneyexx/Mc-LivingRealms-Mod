package dev.livingrealms.minecraft.presentation;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.minecraft.construction.WorldMutationGuard;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.presentation.SeasonalFarmPresentation;
import dev.livingrealms.sim.presentation.SeasonalFarmPresentationPlan;
import dev.livingrealms.sim.presentation.SeasonalFarmPresentationPlan.CropVisual;
import dev.livingrealms.sim.presentation.SeasonalFarmPresentationPlan.VisualPlan;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Applies reversible seasonal crop looks to Living Realms-authored farmland only.
 * Never touches player farms, never force-loads chunks, never drops harvest items.
 */
public final class SeasonalFarmPresentationRuntime {
    private static final double ACTIVATION_RADIUS = 96.0D;
    private static final double ACTIVATION_RADIUS_SQR = ACTIVATION_RADIUS * ACTIVATION_RADIUS;
    private static final int MAX_OPS_PER_TICK = 48;
    private static final int SCAN_RADIUS = 10;

    /** packed crop pos -> previous crop BlockState for reversibility. */
    private static final Map<Long, BlockState> PREVIOUS_CROPS = new HashMap<>();
    /** packed farmland pos -> previous farmland BlockState. */
    private static final Map<Long, BlockState> PREVIOUS_FARMLAND = new HashMap<>();
    /** settlementId -> last applied look name (skip no-op churn). */
    private static final Map<Long, String> APPLIED_LOOK = new HashMap<>();

    private SeasonalFarmPresentationRuntime() {}

    public static void tick(ServerLevel level, LivingRealmsSavedData data, long tickCounter) {
        if (level == null || data == null) return;
        if (tickCounter % 100L != 0L) return;
        if (level.players().isEmpty()) return;

        AuthoredBlockLedger ledger = data.authoredBlocks();
        var state = data.state();
        int budget = MAX_OPS_PER_TICK;

        for (Faction faction : state.factions()) {
            if (budget <= 0) break;
            for (Settlement settlement : faction.settlements()) {
                if (budget <= 0) break;
                if (!nearPlayer(level, settlement.position().x(), settlement.position().z())) continue;
                BlockPos centerProbe = new BlockPos(
                        (int) Math.floor(settlement.position().x()),
                        level.getSeaLevel(),
                        (int) Math.floor(settlement.position().z()));
                if (!level.hasChunkAt(centerProbe)) continue;

                VisualPlan plan = SeasonalFarmPresentationPlan.fromLook(
                        SeasonalFarmPresentation.forSettlement(state, settlement));
                String lookKey = plan.look().name();
                String previousLook = APPLIED_LOOK.get(settlement.id());
                // Still refresh periodically even if look unchanged — restore drift / player edits on our cells.
                boolean lookChanged = previousLook == null || !previousLook.equals(lookKey);
                APPLIED_LOOK.put(settlement.id(), lookKey);

                int cx = centerProbe.getX();
                int cz = centerProbe.getZ();
                for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS && budget > 0; dx++) {
                    for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS && budget > 0; dz++) {
                        BlockPos farmProbe = new BlockPos(cx + dx, level.getSeaLevel(), cz + dz);
                        if (!level.hasChunkAt(farmProbe)) continue;
                        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + dx, cz + dz) - 1;
                        if (surface <= level.getMinBuildHeight() + 1 || surface >= level.getMaxBuildHeight() - 2) continue;
                        // Surface + one below covers farmland under short crops without chunk loads.
                        for (int y = surface; y >= surface - 1 && budget > 0; y--) {
                            BlockPos farmPos = new BlockPos(cx + dx, y, cz + dz);
                            if (!isLrFarmland(level, ledger, farmPos)) continue;
                            BlockPos cropPos = farmPos.above();
                            if (!level.hasChunkAt(cropPos)) continue;
                            if (applyCell(level, ledger, farmPos, cropPos, plan, lookChanged)) {
                                budget--;
                            }
                            break;
                        }
                    }
                }
            }
        }

        // Revert presentation cells whose settlement look left the session map (settlement gone).
        if (tickCounter % 400L == 0L) {
            revertOrphans(level, ledger, state);
        }
    }

    /** Test/GameTest entry: apply one visual plan to an LR-authored farmland cell. */
    public static boolean tryApplyAuthoredCell(
            ServerLevel level,
            AuthoredBlockLedger ledger,
            BlockPos farmPos,
            VisualPlan plan
    ) {
        if (level == null || ledger == null || farmPos == null || plan == null) return false;
        if (!level.hasChunkAt(farmPos)) return false;
        return applyCell(level, ledger, farmPos, farmPos.above(), plan, true);
    }

    public static void clear() {
        PREVIOUS_CROPS.clear();
        PREVIOUS_FARMLAND.clear();
        APPLIED_LOOK.clear();
    }

    /** Test helper: count tracked presentation cells. */
    public static int trackedCropCells() {
        return PREVIOUS_CROPS.size();
    }

    private static boolean applyCell(
            ServerLevel level,
            AuthoredBlockLedger ledger,
            BlockPos farmPos,
            BlockPos cropPos,
            VisualPlan plan,
            boolean lookChanged
    ) {
        AuthoredOwnerType farmOwner = ledger.ownerType(farmPos.getX(), farmPos.getY(), farmPos.getZ());
        if (farmOwner == null) return false;
        // Only settlement/infrastructure farmland (LR-authored or safely adopted via materializer).
        if (farmOwner != AuthoredOwnerType.SETTLEMENT_STRUCTURE
                && farmOwner != AuthoredOwnerType.INFRASTRUCTURE) {
            return false;
        }

        BlockState farmCurrent = level.getBlockState(farmPos);
        BlockState cropCurrent = level.getBlockState(cropPos);

        // Never adopt or overwrite unknown player crop geometry sitting on our farmland.
        AuthoredOwnerType cropOwner = ledger.ownerType(cropPos.getX(), cropPos.getY(), cropPos.getZ());
        if (!cropCurrent.isAir()
                && !cropCurrent.canBeReplaced()
                && cropOwner == null
                && !isPresentationCrop(cropCurrent)
                && !PREVIOUS_CROPS.containsKey(cropPos.asLong())) {
            return false;
        }

        BlockState desiredFarm = Blocks.FARMLAND.defaultBlockState()
                .setValue(FarmBlock.MOISTURE, plan.farmlandMoisture());
        BlockState desiredCrop = desiredCrop(plan.crop());

        boolean changed = false;
        long farmKey = farmPos.asLong();
        long cropKey = cropPos.asLong();

        if (!farmCurrent.equals(desiredFarm)) {
            PREVIOUS_FARMLAND.putIfAbsent(farmKey, farmCurrent);
            if (WorldMutationGuard.trySetAuthored(
                    level, farmPos, desiredFarm, ledger, farmOwner, false, false)) {
                changed = true;
            }
        } else if (lookChanged) {
            PREVIOUS_FARMLAND.putIfAbsent(farmKey, farmCurrent);
        }

        if (!cropCurrent.equals(desiredCrop)) {
            PREVIOUS_CROPS.putIfAbsent(cropKey, cropCurrent);
            // Suppress drops so presentation swaps never duplicate harvest into the world.
            if (setPresentationBlock(level, cropPos, desiredCrop, ledger, farmOwner)) {
                changed = true;
            }
        } else if (lookChanged) {
            PREVIOUS_CROPS.putIfAbsent(cropKey, cropCurrent);
        }
        return changed;
    }

    /**
     * Presentation write that restores ledger ownership without claiming foreign blocks.
     * Uses suppress-drops flags so crop age changes never spill wheat items.
     */
    private static boolean setPresentationBlock(
            ServerLevel level,
            BlockPos pos,
            BlockState target,
            AuthoredBlockLedger ledger,
            AuthoredOwnerType owner
    ) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState current = level.getBlockState(pos);
        if (current.equals(target)) {
            return true;
        }
        AuthoredOwnerType existing = ledger.ownerType(pos.getX(), pos.getY(), pos.getZ());
        if (existing != null && existing != owner && !AuthoredOwnerType.allowsOverwrite(existing, owner)) {
            return false;
        }
        if (current.hasBlockEntity()) return false;
        if (!current.isAir() && !current.canBeReplaced() && !isPresentationCrop(current)
                && existing == null) {
            return false;
        }
        if (!ledger.canRecord(pos.getX(), pos.getY(), pos.getZ())) return false;
        BlockState previous = current;
        if (!level.setBlock(pos, target, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS)) return false;
        if (!ledger.record(pos.getX(), pos.getY(), pos.getZ(), owner)) {
            level.setBlock(pos, previous, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            return false;
        }
        return true;
    }

    private static BlockState desiredCrop(CropVisual visual) {
        int age = SeasonalFarmPresentationPlan.wheatAge(visual);
        if (age >= 0) {
            return Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, age);
        }
        return switch (visual) {
            case SNOW_COVER -> Blocks.SNOW.defaultBlockState();
            case WATER_SHEET -> Blocks.LIGHT_BLUE_CARPET.defaultBlockState();
            case DEAD_BUSH -> Blocks.DEAD_BUSH.defaultBlockState();
            default -> Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 4);
        };
    }

    private static boolean isLrFarmland(ServerLevel level, AuthoredBlockLedger ledger, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.FARMLAND)
                && !PREVIOUS_FARMLAND.containsKey(pos.asLong())) {
            // Allow restoring from temporary non-farmland only if we previously authored it.
            return false;
        }
        AuthoredOwnerType owner = ledger.ownerType(pos.getX(), pos.getY(), pos.getZ());
        return owner == AuthoredOwnerType.SETTLEMENT_STRUCTURE || owner == AuthoredOwnerType.INFRASTRUCTURE;
    }

    private static boolean isPresentationCrop(BlockState state) {
        return state.is(Blocks.WHEAT)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.LIGHT_BLUE_CARPET)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.AIR);
    }

    private static void revertOrphans(ServerLevel level, AuthoredBlockLedger ledger,
                                      dev.livingrealms.sim.world.SimulationState state) {
        Iterator<Map.Entry<Long, BlockState>> cropIt = PREVIOUS_CROPS.entrySet().iterator();
        while (cropIt.hasNext()) {
            Map.Entry<Long, BlockState> entry = cropIt.next();
            BlockPos pos = BlockPos.of(entry.getKey());
            if (!level.hasChunkAt(pos)) continue;
            // Keep entries while any nearby settlement still has an applied look.
            boolean keep = false;
            for (Long sid : APPLIED_LOOK.keySet()) {
                Settlement settlement = state.findSettlement(sid).orElse(null);
                if (settlement == null) continue;
                double dx = settlement.position().x() - pos.getX();
                double dz = settlement.position().z() - pos.getZ();
                if (dx * dx + dz * dz <= (SCAN_RADIUS + 8) * (SCAN_RADIUS + 8.0)) {
                    keep = true;
                    break;
                }
            }
            if (keep) continue;
            BlockState previous = entry.getValue();
            AuthoredOwnerType owner = ledger.ownerType(pos.getX(), pos.getY(), pos.getZ());
            if (owner != null) {
                level.setBlock(pos, previous, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
            cropIt.remove();
        }
    }

    private static boolean nearPlayer(ServerLevel level, double x, double z) {
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            if (dx * dx + dz * dz <= ACTIVATION_RADIUS_SQR) return true;
        }
        return false;
    }
}
