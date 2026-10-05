package dev.livingrealms.minecraft.construction;

import dev.livingrealms.sim.construction.BuildOperation;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.EntranceAccessPlanner;
import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.construction.StructureAccessValidator;
import dev.livingrealms.sim.construction.StructureGeometryRules;
import dev.livingrealms.sim.construction.StructureRole;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Samples loaded world geometry around a finished building and feeds
 * {@link StructureAccessValidator}. Never force-loads chunks.
 */
public final class WorldStructureAccessProbe {
    private WorldStructureAccessProbe() {}

    public static boolean requiresAccessGate(StructureRole role) {
        return StructureGeometryRules.requiresAllDoors(role);
    }

    /**
     * Returns a validator verdict for the building's door approach. Unloaded approach samples
     * defer (pass=true with GATE_PASSAGE kind used as soft defer — callers should treat
     * {@link #shouldDefer(StructureAccessValidator.Verdict)} separately).
     */
    public static StructureAccessValidator.Verdict probe(ServerLevel level, ConstructionIntent intent,
                                                         List<BuildOperation> operations) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(operations, "operations");
        if (!requiresAccessGate(intent.role())) {
            return StructureAccessValidator.Verdict.pass(StructureAccessValidator.AccessKind.FLAT, "role_exempt");
        }
        BuildOperation door = null;
        for (BuildOperation op : operations) {
            if (op.slot() == PaletteSlot.DOOR) {
                door = op;
                break;
            }
        }
        if (door == null) {
            return StructureAccessValidator.Verdict.fail(StructureAccessValidator.AccessKind.SEALED_WALL, "no_door");
        }
        BlockPos doorPos = new BlockPos(door.x(), door.y(), door.z());
        if (level.getChunkSource().getChunkNow(doorPos.getX() >> 4, doorPos.getZ() >> 4) == null) {
            return StructureAccessValidator.Verdict.pass(StructureAccessValidator.AccessKind.FLAT, "defer_unloaded");
        }

        // Approach: one block outside door along intended facing (local -Z rotated).
        int turns = Math.floorMod(intent.rotationQuarterTurns(), 4);
        int stepX = 0, stepZ = -1;
        for (int i = 0; i < turns; i++) {
            int t = stepX;
            stepX = -stepZ;
            stepZ = t;
        }
        BlockPos approach = doorPos.offset(stepX, 0, stepZ);
        BlockPos street = approach.offset(stepX, 0, stepZ);
        if (level.getChunkSource().getChunkNow(street.getX() >> 4, street.getZ() >> 4) == null) {
            return StructureAccessValidator.Verdict.pass(StructureAccessValidator.AccessKind.FLAT, "defer_unloaded");
        }

        BlockState approachState = level.getBlockState(approach);
        BlockState belowApproach = level.getBlockState(approach.below());
        if (!approachState.getFluidState().isEmpty()
                && approachState.getFluidState().getAmount() >= 8
                && intent.role() != StructureRole.DOCK) {
            return StructureAccessValidator.Verdict.fail(StructureAccessValidator.AccessKind.DEEP_WATER, "deep water without dock");
        }
        if (approachState.is(BlockTags.WALLS) || approachState.is(net.minecraft.world.level.block.Blocks.IRON_BARS)) {
            return StructureAccessValidator.Verdict.fail(StructureAccessValidator.AccessKind.SEALED_WALL, "entrance sealed");
        }
        if (isSolidBlocking(approachState) && !isWalkableSurface(approachState)) {
            return StructureAccessValidator.Verdict.fail(StructureAccessValidator.AccessKind.BLOCKED_STRUCTURE, "blocked approach");
        }
        if (belowApproach.isAir()) {
            return StructureAccessValidator.Verdict.fail(StructureAccessValidator.AccessKind.FLOATING_FLOOR, "floating approach");
        }

        int doorFloorY = door.y() - 1;
        int approachY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, street.getX(), street.getZ()) - 1;
        if (EntranceAccessPlanner.isExtremeSite(doorFloorY, approachY)) {
            return StructureAccessValidator.Verdict.fail(StructureAccessValidator.AccessKind.CLIFF, "extreme site");
        }
        boolean roadConnected = looksLikeRoad(level, street) || looksLikeRoad(level, approach)
                || hasPathOpNear(operations, street.getX(), street.getZ(), 3)
                || hasAccessFixNear(operations, approach.getX(), approach.getZ());
        return StructureAccessValidator.validateGrade(doorFloorY, approachY, roadConnected);
    }

    public static boolean shouldDefer(StructureAccessValidator.Verdict verdict) {
        return verdict != null && "defer_unloaded".equals(verdict.reason());
    }

    private static boolean isSolidBlocking(BlockState state) {
        return state.blocksMotion() && !isWalkableSurface(state);
    }

    private static boolean isWalkableSurface(BlockState state) {
        return state.is(BlockTags.SLABS) || state.is(BlockTags.STAIRS) || state.is(BlockTags.WOOL_CARPETS)
                || state.is(net.minecraft.world.level.block.Blocks.DIRT_PATH)
                || state.is(net.minecraft.world.level.block.Blocks.STONE_BRICKS)
                || state.is(BlockTags.PLANKS)
                || state.isAir();
    }

    private static boolean looksLikeRoad(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(net.minecraft.world.level.block.Blocks.DIRT_PATH)
                || state.is(net.minecraft.world.level.block.Blocks.GRAVEL)
                || state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE)
                || state.is(BlockTags.STONE_BRICKS)) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(net.minecraft.world.level.block.Blocks.DIRT_PATH)
                || below.is(net.minecraft.world.level.block.Blocks.GRAVEL)
                || below.is(net.minecraft.world.level.block.Blocks.COBBLESTONE);
    }

    private static boolean hasPathOpNear(List<BuildOperation> operations, int x, int z, int radius) {
        for (BuildOperation op : operations) {
            if (op.slot() != PaletteSlot.PATH && op.slot() != PaletteSlot.FOUNDATION) continue;
            if (Math.abs(op.x() - x) <= radius && Math.abs(op.z() - z) <= radius) return true;
        }
        return false;
    }

    private static boolean hasAccessFixNear(List<BuildOperation> operations, int x, int z) {
        for (BuildOperation op : operations) {
            if (op.slot() != PaletteSlot.PATH && op.slot() != PaletteSlot.FOUNDATION) continue;
            if (Math.abs(op.x() - x) <= 2 && Math.abs(op.z() - z) <= 2) return true;
        }
        return false;
    }
}
