package dev.livingrealms.minecraft.construction;

import dev.livingrealms.sim.construction.PlayerStructureRegistration;
import dev.livingrealms.sim.construction.PlayerStructureValidator;
import dev.livingrealms.sim.construction.RegisteredPlayerStructure;
import dev.livingrealms.sim.player.PlayerActorIdentity;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Bounded server-side survey of a player-built structure. Never force-loads chunks.
 * Recognition only — does not modify player blocks.
 *
 * <p>Doors are boundary portals, not walkable flood-fill cells. Interior BFS never escapes
 * through the exterior entrance into open world air.
 */
public final class PlayerStructureSurvey {
    private PlayerStructureSurvey() {}

    public record SurveyOutcome(boolean success, String message, long structureId, int capacity) {}

    public enum FailReason {
        UNKNOWN_ROLE,
        ENTRANCE_UNLOADED,
        NO_ENTRANCE,
        OPEN_TO_EXTERIOR,
        REACHES_UNLOADED_BOUNDARY,
        TOO_LARGE,
        VALIDATION
    }

    /** Preferred API: canonical actor key from {@link PlayerActorIdentity} / CrimeRuntime. */
    public static SurveyOutcome surveyAndRegister(ServerLevel level, ServerPlayer player, String canonicalActorKey,
                                                  SimulationState state, long settlementId, String roleArgument) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(state, "state");
        String actor = canonicalActorKey == null || canonicalActorKey.isBlank()
                ? PlayerActorIdentity.of(player.getUUID())
                : canonicalActorKey;
        if (!PlayerActorIdentity.isCanonical(actor)) {
            // Never fall back to display name — force UUID identity.
            actor = PlayerActorIdentity.of(player.getUUID());
        }
        RegisteredPlayerStructure.Role role;
        try {
            role = RegisteredPlayerStructure.Role.valueOf(
                    roleArgument == null || roleArgument.isBlank() ? "HOUSE"
                            : roleArgument.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return new SurveyOutcome(false, "Unknown building role. Try HOUSE, TOWN_HALL, WORKSHOP, …", 0, 0);
        }
        BlockPos origin = player.blockPosition();
        BlockPos door = findNearbyDoor(level, origin);
        if (door == null) {
            return new SurveyOutcome(false, "Cannot register: stand at a door entrance to survey the building.", 0, 0);
        }
        if (!chunkLoaded(level, door)) {
            return new SurveyOutcome(false, "Cannot verify this building until all of it is loaded.", 0, 0);
        }

        ScanResult scan = scanInterior(level, door);
        if (!scan.ok()) {
            return new SurveyOutcome(false, scan.message(), 0, 0);
        }
        MetricsAccum accum = scan.metrics();
        boolean overlapsProtected = overlapsProtectedFootprint(state, settlementId,
                accum.minX, accum.minY, accum.minZ, accum.maxX, accum.maxY, accum.maxZ);
        PlayerStructureValidator.SurveyMetrics metrics = new PlayerStructureValidator.SurveyMetrics(
                accum.width, accum.depth, accum.height, accum.interiorCells, accum.inspected,
                accum.roofCoverage, accum.floorCoverage, accum.beds, accum.enclosed, accum.validEntrance,
                overlapsProtected, false);

        long fingerprint = fingerprint(accum, role, door);
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

    /** @deprecated use {@link #surveyAndRegister(ServerLevel, ServerPlayer, String, SimulationState, long, String)} */
    @Deprecated
    public static SurveyOutcome surveyAndRegister(ServerLevel level, ServerPlayer player, SimulationState state,
                                                  long settlementId, String roleArgument) {
        return surveyAndRegister(level, player, PlayerActorIdentity.of(player.getUUID()), state, settlementId, roleArgument);
    }

    /** Pure scan used by GameTests — no registration. */
    public static ScanResult scanInterior(ServerLevel level, BlockPos doorBlock) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(doorBlock, "doorBlock");
        BlockPos door = normalizeDoorLower(level, doorBlock);
        if (!chunkLoaded(level, door)) {
            return ScanResult.fail("Cannot verify this building until all of it is loaded.",
                    FailReason.ENTRANCE_UNLOADED);
        }
        BlockState doorState = level.getBlockState(door);
        Direction facing = doorState.getBlock() instanceof DoorBlock
                ? doorState.getValue(DoorBlock.FACING)
                : Direction.NORTH;

        // Candidate interior seeds on both horizontal sides of the doorway.
        BlockPos candidateA = door.relative(facing.getOpposite());
        BlockPos candidateB = door.relative(facing);
        ScanAttempt attemptA = floodInterior(level, door, candidateA, facing);
        ScanAttempt attemptB = floodInterior(level, door, candidateB, facing.getOpposite());

        ScanAttempt chosen = chooseEnclosed(attemptA, attemptB);
        if (chosen == null) {
            if (attemptA != null && attemptA.reason == FailReason.REACHES_UNLOADED_BOUNDARY) {
                return ScanResult.fail("Cannot verify this building until all of it is loaded.",
                        FailReason.REACHES_UNLOADED_BOUNDARY);
            }
            if (attemptB != null && attemptB.reason == FailReason.REACHES_UNLOADED_BOUNDARY) {
                return ScanResult.fail("Cannot verify this building until all of it is loaded.",
                        FailReason.REACHES_UNLOADED_BOUNDARY);
            }
            if ((attemptA != null && attemptA.reason == FailReason.TOO_LARGE)
                    || (attemptB != null && attemptB.reason == FailReason.TOO_LARGE)) {
                return ScanResult.fail("Cannot register: structure is too large for the bounded survey.",
                        FailReason.TOO_LARGE);
            }
            return ScanResult.fail("Cannot register: open shell — walls do not enclose an interior.",
                    FailReason.OPEN_TO_EXTERIOR);
        }
        return ScanResult.ok(chosen.metrics);
    }

    private static ScanAttempt chooseEnclosed(ScanAttempt a, ScanAttempt b) {
        boolean aOk = a != null && a.metrics != null && a.metrics.enclosed && a.metrics.validEntrance;
        boolean bOk = b != null && b.metrics != null && b.metrics.enclosed && b.metrics.validEntrance;
        if (aOk && bOk) {
            // Prefer denser roof/floor, then more beds, then smaller volume.
            int scoreA = score(a.metrics);
            int scoreB = score(b.metrics);
            return scoreA >= scoreB ? a : b;
        }
        if (aOk) return a;
        if (bOk) return b;
        return null;
    }

    private static int score(MetricsAccum m) {
        return (int) (m.roofCoverage * 100) + (int) (m.floorCoverage * 100) + m.beds * 10 - m.interiorCells / 8;
    }

    private static ScanAttempt floodInterior(ServerLevel level, BlockPos door, BlockPos seed, Direction inward) {
        if (!isTraversableInterior(level, seed)) {
            return null;
        }
        MetricsAccum a = new MetricsAccum();
        a.validEntrance = true;
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        Set<Long> bedHeads = new HashSet<>();
        q.add(seed.immutable());
        seen.add(seed.asLong());
        int solidWallHits = 0;
        int roofed = 0, floored = 0, columns = 0;
        boolean hitBoundary = false;
        boolean hitUnloaded = false;
        boolean leaked = false;

        // Exterior portal cell — never enqueue the door as a normal walkable cell toward outside.
        long doorKey = door.asLong();

        while (!q.isEmpty()) {
            if (a.inspected >= PlayerStructureValidator.MAX_INSPECTED_BLOCKS
                    || a.interiorCells >= PlayerStructureValidator.MAX_INTERIOR_CELLS) {
                return new ScanAttempt(null, FailReason.TOO_LARGE);
            }
            BlockPos p = q.removeFirst();
            if (!chunkLoaded(level, p)) {
                hitUnloaded = true;
                continue;
            }
            a.inspected++;
            BlockState state = level.getBlockState(p);
            if (isSolidBoundary(state)) {
                solidWallHits++;
                continue;
            }
            // Internal doors are traversable; the exterior entrance door cell is a portal boundary.
            if (p.asLong() == doorKey) {
                solidWallHits++;
                continue;
            }
            if (!isTraversableInterior(level, p)) {
                solidWallHits++;
                continue;
            }

            if (Math.abs(p.getX() - door.getX()) >= PlayerStructureValidator.MAX_HORIZONTAL
                    || Math.abs(p.getZ() - door.getZ()) >= PlayerStructureValidator.MAX_HORIZONTAL
                    || Math.abs(p.getY() - door.getY()) >= PlayerStructureValidator.MAX_HEIGHT) {
                hitBoundary = true;
                leaked = true;
                break;
            }

            a.interiorCells++;
            a.minX = Math.min(a.minX, p.getX()); a.maxX = Math.max(a.maxX, p.getX());
            a.minY = Math.min(a.minY, p.getY()); a.maxY = Math.max(a.maxY, p.getY());
            a.minZ = Math.min(a.minZ, p.getZ()); a.maxZ = Math.max(a.maxZ, p.getZ());

            boolean hasFloor = false, hasRoof = false;
            for (int dy = 1; dy <= 4; dy++) {
                BlockPos below = p.below(dy);
                if (!chunkLoaded(level, below)) { hitUnloaded = true; break; }
                a.inspected++;
                BlockState down = level.getBlockState(below);
                if (!down.isAir() && !(down.getBlock() instanceof DoorBlock)) { hasFloor = true; break; }
            }
            for (int dy = 1; dy <= 8; dy++) {
                BlockPos above = p.above(dy);
                if (!chunkLoaded(level, above)) { hitUnloaded = true; break; }
                a.inspected++;
                BlockState up = level.getBlockState(above);
                if (!up.isAir() && !(up.getBlock() instanceof DoorBlock)) { hasRoof = true; break; }
            }
            columns++;
            if (hasFloor) floored++;
            if (hasRoof) roofed++;

            // Deterministic unique bed counting (one logical bed per Minecraft bed).
            for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                if (!chunkLoaded(level, n)) continue;
                a.inspected++;
                BlockState bs = level.getBlockState(n);
                if (bs.getBlock() instanceof BedBlock || bs.is(BlockTags.BEDS)) {
                    BlockPos head = bedIdentity(n, bs);
                    bedHeads.add(head.asLong());
                }
            }

            for (Direction dir : Direction.values()) {
                BlockPos n = p.relative(dir);
                if (n.asLong() == doorKey) continue; // never cross exterior portal
                // Crossing the entrance plane outward is leakage.
                if (isAcrossExteriorPortal(door, inward, n)) {
                    if (isTraversableInterior(level, n) || level.getBlockState(n).isAir()) {
                        // Only count as leak if neighbor is open exterior air without being inside walls.
                        // Internal rooms on the far side of non-entrance doors are allowed via other paths.
                        continue;
                    }
                    continue;
                }
                if (seen.add(n.asLong())) q.addLast(n.immutable());
            }
        }

        if (hitUnloaded && a.interiorCells < 8) {
            return new ScanAttempt(null, FailReason.REACHES_UNLOADED_BOUNDARY);
        }
        if (leaked || hitBoundary) {
            return new ScanAttempt(null, FailReason.OPEN_TO_EXTERIOR);
        }
        if (a.interiorCells <= 0 || a.minX == Integer.MAX_VALUE) {
            return new ScanAttempt(null, FailReason.OPEN_TO_EXTERIOR);
        }
        a.width = a.maxX - a.minX + 1;
        a.depth = a.maxZ - a.minZ + 1;
        a.height = a.maxY - a.minY + 1;
        a.floorCoverage = columns == 0 ? 0 : (double) floored / columns;
        a.roofCoverage = columns == 0 ? 0 : (double) roofed / columns;
        a.beds = bedHeads.size();
        // Hitting MAX_INTERIOR_CELLS is never a valid enclosed room.
        boolean hitCap = a.interiorCells >= PlayerStructureValidator.MAX_INTERIOR_CELLS
                || a.inspected >= PlayerStructureValidator.MAX_INSPECTED_BLOCKS;
        a.enclosed = !hitCap && solidWallHits >= 8
                && a.width <= PlayerStructureValidator.MAX_HORIZONTAL
                && a.depth <= PlayerStructureValidator.MAX_HORIZONTAL
                && a.roofCoverage >= PlayerStructureValidator.MIN_ROOF_COVERAGE * 0.5
                && a.floorCoverage >= PlayerStructureValidator.MIN_FLOOR_COVERAGE * 0.5;
        if (hitCap) return new ScanAttempt(null, FailReason.TOO_LARGE);
        if (!a.enclosed) return new ScanAttempt(null, FailReason.OPEN_TO_EXTERIOR);
        return new ScanAttempt(a, null);
    }

    /**
     * True when {@code n} lies on the exterior side of the entrance portal plane.
     * Used to keep BFS from walking through the door into the open world.
     */
    private static boolean isAcrossExteriorPortal(BlockPos door, Direction inward, BlockPos n) {
        // Exterior is opposite of inward.
        Direction outward = inward.getOpposite();
        BlockPos exterior = door.relative(outward);
        return n.getX() == exterior.getX() && n.getY() == exterior.getY() && n.getZ() == exterior.getZ();
    }

    private static BlockPos bedIdentity(BlockPos pos, BlockState state) {
        if (state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) == BedPart.FOOT
                && state.hasProperty(BedBlock.FACING)) {
            return pos.relative(state.getValue(BedBlock.FACING)).immutable();
        }
        return pos.immutable();
    }

    private static boolean isSolidBoundary(BlockState state) {
        if (state.isAir()) return false;
        if (state.getBlock() instanceof DoorBlock) return false;
        if (state.getBlock() instanceof StairBlock) return false;
        if (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE) return false;
        if (state.is(BlockTags.BEDS) || state.getBlock() instanceof BedBlock) return false;
        if (state.is(BlockTags.WOOL_CARPETS)) return false;
        return state.isSolidRender(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                || state.blocksMotion();
    }

    private static boolean isTraversableInterior(ServerLevel level, BlockPos pos) {
        if (!chunkLoaded(level, pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (state.getBlock() instanceof DoorBlock) return true; // internal doors
        if (state.getBlock() instanceof StairBlock) return true;
        if (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) return true;
        if (state.is(BlockTags.WOOL_CARPETS)) return true;
        if (state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock
                && state.hasProperty(net.minecraft.world.level.block.TrapDoorBlock.OPEN)
                && state.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN)) {
            return true;
        }
        return false;
    }

    private static BlockPos findNearbyDoor(ServerLevel level, BlockPos origin) {
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(2, 2, 2))) {
            if (!chunkLoaded(level, p)) continue;
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof DoorBlock)) continue;
            BlockPos lower = normalizeDoorLower(level, p);
            int dist = lower.distManhattan(origin);
            if (dist < bestDist || (dist == bestDist && best != null && comparePos(lower, best) < 0)
                    || (dist == bestDist && best == null)) {
                bestDist = dist;
                best = lower;
            }
        }
        return best;
    }

    private static BlockPos normalizeDoorLower(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.HALF)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return pos.below().immutable();
        }
        return pos.immutable();
    }

    private static int comparePos(BlockPos a, BlockPos b) {
        if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
        if (a.getX() != b.getX()) return Integer.compare(a.getX(), b.getX());
        return Integer.compare(a.getZ(), b.getZ());
    }

    private static boolean chunkLoaded(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL);
    }

    private static boolean overlapsProtectedFootprint(SimulationState state, long settlementId,
                                                      int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (RegisteredPlayerStructure existing : state.registeredPlayerStructures()) {
            if (!existing.valid()) continue;
            if (existing.minX() <= maxX && existing.maxX() >= minX
                    && existing.minY() <= maxY && existing.maxY() >= minY
                    && existing.minZ() <= maxZ && existing.maxZ() >= minZ) {
                return true;
            }
        }
        // Foreign outlying footprints that claim housing credit.
        for (var site : state.outlyingSites()) {
            if (!site.active()) continue;
            // Outlying sites are point+radius; treat a tight box as protected.
            int cx = (int) Math.round(site.position().x());
            int cz = (int) Math.round(site.position().z());
            int r = 12;
            if (cx - r <= maxX && cx + r >= minX && cz - r <= maxZ && cz + r >= minZ) {
                if (site.settlementId() != settlementId || site.housingCredit() > 0) return true;
            }
        }
        return false;
    }

    private static long fingerprint(MetricsAccum accum, RegisteredPlayerStructure.Role role, BlockPos door) {
        return (((long) accum.minX) << 48) ^ (((long) accum.minZ) << 32)
                ^ (((long) accum.interiorCells) << 16) ^ accum.beds ^ role.ordinal()
                ^ (((long) door.getX()) << 8) ^ door.getZ();
    }

    public record ScanResult(boolean ok, String message, FailReason reason, MetricsAccum metrics) {
        public static ScanResult ok(MetricsAccum metrics) {
            return new ScanResult(true, "ok", null, metrics);
        }
        public static ScanResult fail(String message, FailReason reason) {
            return new ScanResult(false, message, reason, null);
        }
    }

    private static final class ScanAttempt {
        final MetricsAccum metrics;
        final FailReason reason;
        ScanAttempt(MetricsAccum metrics, FailReason reason) {
            this.metrics = metrics;
            this.reason = reason;
        }
    }

    /** Exposed for GameTests / revalidation. */
    public static final class MetricsAccum {
        public int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        public int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        public int width, depth, height, interiorCells, inspected, beds;
        public double roofCoverage, floorCoverage;
        public boolean enclosed, validEntrance;
    }
}
