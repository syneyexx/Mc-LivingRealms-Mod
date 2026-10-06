package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.worldgen.ModWorldgenAttachments;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared Minecraft mutation/provenance authority for all Living Realms block writers.
 *
 * <p>Rules:
 * <ul>
 *   <li>Natural terrain and replaceable vegetation may be transformed when explicitly allowed.</li>
 *   <li>Leaves may be cleared as vegetation; generic {@link BlockTags#LOGS} are not free to destroy.</li>
 *   <li>Unknown player/mod structures, block entities, and foreign LR owner classes are preserved.</li>
 *   <li>A destructive write that cannot be recorded in the provenance ledger is refused.</li>
 * </ul>
 */
public final class WorldMutationGuard {
    private WorldMutationGuard() {}

    public enum Classification {
        AIR_OR_REPLACEABLE,
        NATURAL_TERRAIN,
        NATURAL_LEAVES,
        NATURAL_TREE_LOG,
        AUTHORED_COMPATIBLE,
        AUTHORED_FOREIGN_OWNER,
        BLOCK_ENTITY,
        UNKNOWN_STRUCTURE
    }

    public record Decision(boolean mayMutate, Classification classification, String reason) {
        public static Decision allow(Classification c) { return new Decision(true, c, ""); }
        public static Decision deny(Classification c, String reason) { return new Decision(false, c, reason); }
    }

    public static Classification classify(ServerLevel level, BlockPos pos, BlockState state, AuthoredBlockLedger ledger, AuthoredOwnerType requestedOwner) {
        if (state == null) return Classification.UNKNOWN_STRUCTURE;
        // Block entities always win over historical provenance: a player/mod machine or container
        // placed later must never be bulldozed merely because this coordinate was once LR-authored.
        if (state.hasBlockEntity()) return Classification.BLOCK_ENTITY;

        AuthoredOwnerType existing = authoredOwner(level, pos, ledger);
        if (existing != null) {
            return AuthoredOwnerType.allowsOverwrite(existing, requestedOwner)
                    ? Classification.AUTHORED_COMPATIBLE
                    : Classification.AUTHORED_FOREIGN_OWNER;
        }
        if (state.isAir() || state.canBeReplaced()) return Classification.AIR_OR_REPLACEABLE;
        if (state.is(BlockTags.LEAVES) || state.is(Blocks.VINE) || state.is(Blocks.SNOW) || state.is(Blocks.CACTUS)
                || state.is(Blocks.BAMBOO) || state.is(Blocks.BAMBOO_SAPLING)) {
            return Classification.NATURAL_LEAVES;
        }
        if (state.is(BlockTags.LOGS)) {
            return isNaturalTreeLog(level, pos) ? Classification.NATURAL_TREE_LOG : Classification.UNKNOWN_STRUCTURE;
        }
        if (isNaturalTerrain(state)) return Classification.NATURAL_TERRAIN;
        return Classification.UNKNOWN_STRUCTURE;
    }

    public static Decision evaluateReplace(
            ServerLevel level,
            BlockPos pos,
            BlockState current,
            AuthoredBlockLedger ledger,
            AuthoredOwnerType requestedOwner,
            boolean allowNaturalTerrain,
            boolean allowNaturalTreeLogs
    ) {
        Classification c = classify(level, pos, current, ledger, requestedOwner);
        return switch (c) {
            case AIR_OR_REPLACEABLE, NATURAL_LEAVES, AUTHORED_COMPATIBLE -> Decision.allow(c);
            case NATURAL_TERRAIN -> allowNaturalTerrain ? Decision.allow(c) : Decision.deny(c, "natural_terrain_not_allowed");
            case NATURAL_TREE_LOG -> allowNaturalTreeLogs ? Decision.allow(c) : Decision.deny(c, "preserve_tree");
            case BLOCK_ENTITY -> Decision.deny(c, "block_entity");
            case AUTHORED_FOREIGN_OWNER -> Decision.deny(c, "foreign_lr_owner");
            case UNKNOWN_STRUCTURE -> Decision.deny(c, "unknown_structure");
        };
    }

    public static Decision evaluateClear(
            ServerLevel level,
            BlockPos pos,
            BlockState current,
            AuthoredBlockLedger ledger,
            AuthoredOwnerType requestedOwner,
            boolean allowNaturalTreeLogs
    ) {
        return evaluateReplace(level, pos, current, ledger, requestedOwner, false, allowNaturalTreeLogs);
    }

    /**
     * Fail-closed write: never leave an untracked Living Realms mutation when provenance cannot be recorded.
     * Already-correct geometry does not claim ownership.
     */
    public static boolean trySetAuthored(
            ServerLevel level,
            BlockPos pos,
            BlockState target,
            AuthoredBlockLedger ledger,
            AuthoredOwnerType owner,
            boolean allowNaturalTerrain,
            boolean allowNaturalTreeLogs
    ) {
        if (level == null || pos == null || target == null || ledger == null || owner == null) return false;
        if (!level.hasChunkAt(pos)) return false;
        AuthoredOwnerType chunkOwner = ModWorldgenAttachments.ownerAt(level, pos);
        boolean chunkAlreadyOwns = chunkOwner != null
                && AuthoredOwnerType.allowsOverwrite(chunkOwner, owner);
        BlockState current = level.getBlockState(pos);
        if (current.equals(target)) {
            // Geometry satisfied by an existing block — do NOT adopt unknown provenance.
            return true;
        }
        Decision decision = evaluateReplace(level, pos, current, ledger, owner, allowNaturalTerrain, allowNaturalTreeLogs);
        if (!decision.mayMutate()) return false;
        // Worldgen provenance is already persisted on the chunk, so repair of that same owner does
        // not depend on capacity in the legacy/runtime SavedData ledger.
        if (!chunkAlreadyOwns && !ledger.canRecord(pos.getX(), pos.getY(), pos.getZ())) return false;
        BlockState previous = current;
        if (!level.setBlock(pos, target, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS)) return false;
        if (!chunkAlreadyOwns && !ledger.record(pos.getX(), pos.getY(), pos.getZ(), owner)) {
            level.setBlock(pos, previous, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            return false;
        }
        return true;
    }

    /** Runtime/legacy ledger wins; otherwise consult persistent chunk-local starter provenance. */
    public static AuthoredOwnerType authoredOwner(
            ServerLevel level, BlockPos pos, AuthoredBlockLedger ledger) {
        if (pos == null) return null;
        if (ledger != null) {
            AuthoredOwnerType owner = ledger.ownerType(pos.getX(), pos.getY(), pos.getZ());
            if (owner != null) return owner;
        }
        return ModWorldgenAttachments.ownerAt(level, pos);
    }

    public static boolean isNaturalTerrain(BlockState state) {
        if (state == null) return false;
        Block b = state.getBlock();
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE) || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE) || state.is(Blocks.TERRACOTTA) || state.is(Blocks.CLAY)
                || state.is(Blocks.MUD) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW)
                || state.is(Blocks.TUFF) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || b == Blocks.DIRT_PATH;
    }

    /**
     * Strong natural-tree test: tagged log with nearby leaves and no structural foundation under the trunk.
     * Player log houses/bridges fail this test and must be preserved.
     */
    public static boolean isNaturalTreeLog(ServerLevel level, BlockPos log) {
        if (level == null || log == null) return false;
        BlockState state = level.getBlockState(log);
        if (!state.is(BlockTags.LOGS)) return false;
        boolean leaves = false;
        for (BlockPos p : BlockPos.betweenClosed(log.offset(-3, -1, -3), log.offset(3, 6, 3))) {
            if (level.getBlockState(p).is(BlockTags.LEAVES)) { leaves = true; break; }
        }
        if (!leaves) return false;
        // Structural support under a log (planks/stone bricks/etc.) strongly implies a player build.
        for (int dy = 1; dy <= 3; dy++) {
            BlockState below = level.getBlockState(log.below(dy));
            if (below.isAir() || below.canBeReplaced() || below.is(BlockTags.LOGS) || below.is(BlockTags.LEAVES)) continue;
            if (isNaturalTerrain(below)) return true;
            return false;
        }
        return true;
    }
}
