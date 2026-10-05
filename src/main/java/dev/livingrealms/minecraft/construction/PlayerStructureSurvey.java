package dev.livingrealms.minecraft.construction;

import dev.livingrealms.sim.construction.PlayerStructureRegistration;
import dev.livingrealms.sim.construction.PlayerStructureValidator;
import dev.livingrealms.sim.construction.RegisteredPlayerStructure;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Bounded server-side survey of a player-built structure. Never force-loads chunks.
 * Recognition only — does not modify player blocks.
 */
public final class PlayerStructureSurvey {
    private PlayerStructureSurvey() {}

    public record SurveyOutcome(boolean success, String message, long structureId, int capacity) {}

    public static SurveyOutcome surveyAndRegister(ServerLevel level, ServerPlayer player, SimulationState state,
                                                  long settlementId, String roleArgument) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(state, "state");
        RegisteredPlayerStructure.Role role;
        try {
            role = RegisteredPlayerStructure.Role.valueOf(
                    roleArgument == null || roleArgument.isBlank() ? "HOUSE"
                            : roleArgument.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return new SurveyOutcome(false, "Unknown building role. Try HOUSE, TOWN_HALL, WORKSHOP, …", 0, 0);
        }
        BlockPos door = player.blockPosition();
        // Prefer an adjacent door block if the player stands in the entrance.
        BlockPos doorBlock = findNearbyDoor(level, door);
        if (doorBlock != null) door = doorBlock;

        if (!chunkLoaded(level, door)) {
            return new SurveyOutcome(false, "Cannot register: entrance chunk is not loaded.", 0, 0);
        }

        MetricsAccum accum = scan(level, door);
        PlayerStructureValidator.SurveyMetrics metrics = new PlayerStructureValidator.SurveyMetrics(
                accum.width, accum.depth, accum.height, accum.interiorCells, accum.inspected,
                accum.roofCoverage, accum.floorCoverage, accum.beds, accum.enclosed, accum.validEntrance,
                false, false);

        long fingerprint = (((long) accum.minX) << 48) ^ (((long) accum.minZ) << 32)
                ^ (((long) accum.interiorCells) << 16) ^ accum.beds ^ role.ordinal();
        String actor = "player:" + player.getGameProfile().getName();
        var result = PlayerStructureRegistration.register(
                state, actor, settlementId, role,
                accum.minX, accum.minY, accum.minZ, accum.maxX, accum.maxY, accum.maxZ,
                door.getX(), door.getY(), door.getZ(), metrics, fingerprint);
        if (!result.success()) {
            return new SurveyOutcome(false, result.reason(), 0, 0);
        }
        return new SurveyOutcome(true,
                "Registered " + role.name().toLowerCase(Locale.ROOT)
                        + " (+" + result.capacity() + " capacity). Living Realms will not rebuild your blocks.",
                result.structureId(), result.capacity());
    }

    private static BlockPos findNearbyDoor(ServerLevel level, BlockPos origin) {
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(2, 2, 2))) {
            if (!chunkLoaded(level, p)) continue;
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof DoorBlock) return p.immutable();
        }
        return null;
    }

    private static boolean chunkLoaded(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL);
    }

    private static final class MetricsAccum {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int width, depth, height, interiorCells, inspected, beds;
        double roofCoverage, floorCoverage;
        boolean enclosed, validEntrance;
    }

    private static MetricsAccum scan(ServerLevel level, BlockPos door) {
        MetricsAccum a = new MetricsAccum();
        a.validEntrance = true;
        // Flood-fill walkable interior from just inside the door (prefer +Z then neighbors).
        BlockPos start = door.relative(net.minecraft.core.Direction.NORTH);
        if (!isWalkableAir(level, start)) start = door.relative(net.minecraft.core.Direction.SOUTH);
        if (!isWalkableAir(level, start)) start = door.above();
        if (!isWalkableAir(level, start)) {
            a.validEntrance = false;
            a.enclosed = false;
            return a;
        }

        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        q.add(start.immutable());
        seen.add(start.asLong());
        int solidWallHits = 0;
        int roofed = 0, floored = 0, columns = 0;

        while (!q.isEmpty() && a.inspected < PlayerStructureValidator.MAX_INSPECTED_BLOCKS
                && a.interiorCells < PlayerStructureValidator.MAX_INTERIOR_CELLS) {
            BlockPos p = q.removeFirst();
            if (!chunkLoaded(level, p)) continue;
            a.inspected++;
            BlockState state = level.getBlockState(p);
            if (!state.isAir() && !(state.getBlock() instanceof DoorBlock)) {
                solidWallHits++;
                continue;
            }
            a.interiorCells++;
            a.minX = Math.min(a.minX, p.getX()); a.maxX = Math.max(a.maxX, p.getX());
            a.minY = Math.min(a.minY, p.getY()); a.maxY = Math.max(a.maxY, p.getY());
            a.minZ = Math.min(a.minZ, p.getZ()); a.maxZ = Math.max(a.maxZ, p.getZ());

            // Floor / roof probes
            boolean hasFloor = false, hasRoof = false;
            for (int dy = 1; dy <= 4; dy++) {
                BlockPos below = p.below(dy);
                if (!chunkLoaded(level, below)) break;
                a.inspected++;
                if (!level.getBlockState(below).isAir()) { hasFloor = true; break; }
            }
            for (int dy = 1; dy <= 8; dy++) {
                BlockPos above = p.above(dy);
                if (!chunkLoaded(level, above)) break;
                a.inspected++;
                BlockState up = level.getBlockState(above);
                if (!up.isAir()) { hasRoof = true; break; }
            }
            columns++;
            if (hasFloor) floored++;
            if (hasRoof) roofed++;

            // Beds in or adjacent
            for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                if (!chunkLoaded(level, n)) continue;
                a.inspected++;
                if (level.getBlockState(n).getBlock() instanceof BedBlock
                        || level.getBlockState(n).is(BlockTags.BEDS)) {
                    a.beds++;
                }
            }

            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos n = p.relative(dir);
                if (Math.abs(n.getX() - door.getX()) > PlayerStructureValidator.MAX_HORIZONTAL) continue;
                if (Math.abs(n.getZ() - door.getZ()) > PlayerStructureValidator.MAX_HORIZONTAL) continue;
                if (Math.abs(n.getY() - door.getY()) > PlayerStructureValidator.MAX_HEIGHT) continue;
                if (seen.add(n.asLong())) q.addLast(n.immutable());
            }
        }

        if (a.interiorCells <= 0 || a.minX == Integer.MAX_VALUE) {
            a.enclosed = false;
            return a;
        }
        a.width = a.maxX - a.minX + 1;
        a.depth = a.maxZ - a.minZ + 1;
        a.height = a.maxY - a.minY + 1;
        a.floorCoverage = columns == 0 ? 0 : (double) floored / columns;
        a.roofCoverage = columns == 0 ? 0 : (double) roofed / columns;
        // Enclosed if we hit solid walls during expansion and didn't leak into an unbounded void.
        a.enclosed = solidWallHits >= 8 && a.interiorCells < PlayerStructureValidator.MAX_INTERIOR_CELLS
                && a.width <= PlayerStructureValidator.MAX_HORIZONTAL
                && a.depth <= PlayerStructureValidator.MAX_HORIZONTAL;
        // Bed count over-counts adjacency; normalize roughly.
        a.beds = Math.max(0, (a.beds + 3) / 4);
        return a;
    }

    private static boolean isWalkableAir(ServerLevel level, BlockPos pos) {
        if (!chunkLoaded(level, pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.getBlock() instanceof DoorBlock;
    }
}
