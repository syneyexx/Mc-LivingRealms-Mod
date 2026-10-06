package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.minecraft.construction.FactionBlockPalette;
import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.Tags;

/**
 * Worldgen-only block writer. It never accesses SavedData, never force-loads chunks and writes only
 * the chunk currently being decorated.
 */
public final class WorldgenFabricBlockWriter {
    private static final int WORLDGEN_FLAGS =
            Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private final WorldGenLevel level;
    private final int chunkX;
    private final int chunkZ;
    private final StarterCivilizationWorldgenContext.Context worldgenContext;
    private final List<BoundingBox> foreignStructurePieces;
    private final int[] terrainSnapshot = new int[16 * 16];
    private final int[] worldSurfaceSnapshot = new int[16 * 16];
    private final int[] oceanFloorSnapshot = new int[16 * 16];
    private final int[] waterSurfaceSnapshot = new int[16 * 16];
    private final Map<Long, AuthoredOwnerType> authoredOwnerByPos = new HashMap<>();
    private final Map<Long, Boolean> naturalTreeLogSnapshot = new HashMap<>();
    private final List<StarterCivilizationWorldgenContext.AuthoredWrite> authoredWrites = new ArrayList<>();

    public WorldgenFabricBlockWriter(
            WorldGenLevel level, int chunkX, int chunkZ,
            StarterCivilizationWorldgenContext.Context worldgenContext) {
        this.level = level;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.worldgenContext = Objects.requireNonNull(worldgenContext, "worldgenContext");
        List<BoundingBox> foreignPieces = new ArrayList<>();
        /*
         * NEVER call StructureManager.startsForStructure(...) from a placed feature.
         *
         * That method follows the current chunk's structure-reference longs back to each
         * structure's start chunk. A referenced start may legitimately live outside the active
         * WorldGenRegion during FEATURES, in which case WorldGenRegion.getChunk throws
         * "Requested chunk unavailable during world generation" and fresh-world creation dies
         * before spawn preparation can advance.
         *
         * Read only starts stored directly on the chunk currently being decorated. This is
         * generation-region safe and still protects structures whose start is local. Structures
         * crossing in from a remote start remain protected fail-closed by canReplaceForWorldgen:
         * their already-placed non-natural solids/block entities are never bulldozed.
         */
        var currentChunk = level.getChunk(chunkX, chunkZ);
        for (var start : currentChunk.getAllStarts().values()) {
            if (start == null || !start.isValid()) continue;
            for (var piece : start.getPieces()) {
                foreignPieces.add(piece.getBoundingBox());
            }
        }
        this.foreignStructurePieces = List.copyOf(foreignPieces);
        // Snapshot the whole writable chunk before any Living Realms block is placed. Later roads,
        // walls and terrain-following structures must never treat earlier LR writes as terrain.
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int index = (lx << 4) | lz;
                int x = minX + lx, z = minZ + lz;
                worldSurfaceSnapshot[index] =
                        level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                oceanFloorSnapshot[index] =
                        level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
                terrainSnapshot[index] = sampleTerrainY(x, z);
                int waterSurface = terrainSnapshot[index];
                for (int y = worldSurfaceSnapshot[index]; y > terrainSnapshot[index]; y--) {
                    BlockPos waterPos = new BlockPos(x, y, z);
                    BlockState waterState = level.getBlockState(waterPos);
                    if (!level.getFluidState(waterPos).isEmpty() || isFrozenWater(waterState)) {
                        waterSurface = y;
                        break;
                    }
                }
                waterSurfaceSnapshot[index] = waterSurface;
            }
        }
    }

    /**
     * Natural terrain surface for starter fabric. Current-chunk columns always use the immutable
     * pre-write snapshot; outside columns are used only by the structure-base prepass.
     */
    public int terrainY(int x, int z) {
        if (insideCurrentChunk(x, z)) return terrainSnapshot[columnIndex(x, z)];
        return sampleTerrainY(x, z);
    }

    public int oceanFloorY(int x, int z) {
        if (insideCurrentChunk(x, z)) return oceanFloorSnapshot[columnIndex(x, z)];
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
    }

    /**
     * Highest fluid surface captured before LR writes. Returns terrainY when the column is dry.
     * Scanning down from WORLD_SURFACE_WG also handles vegetation/lily pads and frozen surfaces.
     */
    public int waterSurfaceY(int x, int z) {
        if (insideCurrentChunk(x, z)) return waterSurfaceSnapshot[columnIndex(x, z)];
        int ground = terrainY(x, z);
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        for (int y = top; y > ground; y--) {
            BlockPos waterPos = new BlockPos(x, y, z);
            BlockState waterState = level.getBlockState(waterPos);
            if (!level.getFluidState(waterPos).isEmpty() || isFrozenWater(waterState)) return y;
        }
        return ground;
    }

    /**
     * Applies the immutable regional-road grade profile without touching foreign structure pieces.
     * Negative deltas become short natural-terrain cuts/tunnels; positive deltas become filled
     * causeways or periodically supported bridge decks. All earthwork is bounded.
     */
    public boolean prepareDryRoadColumn(
            long factionId,
            int x,
            int z,
            int groundY,
            int deckY,
            AuthoredOwnerType ownerType) {
        if (!insideCurrentChunk(x, z) || ownerType == null) return false;
        int delta = deckY - groundY;
        // Engineered starter corridors may tunnel through a major ridge or bridge a deep ravine.
        // Keep hard bounds to avoid absurd vertical shafts while preventing ordinary mountain gaps.
        if (delta < -96 || delta > 64) return false;

        if (delta < 0) {
            int headTop = Math.min(groundY + 2, deckY + 3);
            for (int y = deckY + 1; y <= headTop; y++) {
                if (!canReplaceForWorldgen(new BlockPos(x, y, z), true, ownerType)) return false;
            }
            for (int y = deckY + 1; y <= headTop; y++) {
                BlockPos carve = new BlockPos(x, y, z);
                if (!writeState(carve, Blocks.AIR.defaultBlockState(), true, ownerType)
                        && !level.getBlockState(carve).isAir()) {
                    return false;
                }
            }
            return true;
        }

        if (!clearNaturalVegetationAbove(x, groundY, z, 8, ownerType)) return false;
        boolean fullCauseway = delta <= 4;
        boolean supportColumn = fullCauseway || Math.floorMod(x * 31 + z * 17, 7) == 0;
        if (!supportColumn) return true;

        for (int y = groundY + 1; y < deckY; y++) {
            if (!canReplaceForWorldgen(new BlockPos(x, y, z), false, ownerType)) return false;
        }
        for (int y = groundY + 1; y < deckY; y++) {
            if (!write(
                    factionId, PaletteSlot.FOUNDATION, new BlockPos(x, y, z),
                    0, false, null, ownerType)) return false;
        }
        return true;
    }

    public int buildSurfaceY(int x, int z) {
        return Math.max(terrainY(x, z), waterSurfaceY(x, z));
    }

    public int siteBaseY(int centerX, int centerZ, int width, int depth, int turns) {
        int w = (turns & 1) == 0 ? width : depth;
        int d = (turns & 1) == 0 ? depth : width;
        int hx = Math.max(1, w / 2), hz = Math.max(1, d / 2);
        // Fixed multi-chunk structures must derive one base from the chunk generator itself.
        // Reading WorldGenRegion columns here is unsafe: a corner can lie outside the active region,
        // and an already-generated neighboring LR slice would also make generation order observable.
        int y = generatorSurfaceY(centerX, centerZ);
        y = Math.max(y, generatorSurfaceY(centerX - hx, centerZ - hz));
        y = Math.max(y, generatorSurfaceY(centerX + hx, centerZ - hz));
        y = Math.max(y, generatorSurfaceY(centerX - hx, centerZ + hz));
        y = Math.max(y, generatorSurfaceY(centerX + hx, centerZ + hz));
        return y;
    }

    private int generatorSurfaceY(int x, int z) {
        var chunkSource = level.getLevel().getChunkSource();
        return chunkSource.getGenerator().getBaseHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, level, chunkSource.randomState()) - 1;
    }

    /** Natural generator ground, below fluids when applicable, without requesting a chunk. */
    public int generatorGroundY(int x, int z) {
        var chunkSource = level.getLevel().getChunkSource();
        return chunkSource.getGenerator().getBaseHeight(
                x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, chunkSource.randomState()) - 1;
    }

    public boolean write(long factionId, PaletteSlot slot, BlockPos pos,
                         int rotationQuarterTurns, boolean doorUpper, BedPart bedPart,
                         AuthoredOwnerType ownerType) {
        BlockState target = FactionBlockPalette.worldgenState(
                factionId, slot, worldgenContext.paletteStyle(factionId));
        if (slot == PaletteSlot.DOOR && target.getBlock() instanceof DoorBlock) {
            target = target
                    .setValue(DoorBlock.FACING, facing(rotationQuarterTurns))
                    .setValue(DoorBlock.HALF, doorUpper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)
                    .setValue(DoorBlock.OPEN, false)
                    .setValue(DoorBlock.POWERED, false);
        } else if (slot == PaletteSlot.BED && target.getBlock() instanceof BedBlock) {
            target = target
                    .setValue(BedBlock.FACING, facing(rotationQuarterTurns))
                    .setValue(BedBlock.PART, bedPart == null ? BedPart.FOOT : bedPart);
        }
        return writeState(pos, target, slot == PaletteSlot.AIR, ownerType);
    }

    public boolean writeState(BlockPos pos, BlockState target, boolean clearing,
                              AuthoredOwnerType ownerType) {
        if (!insideCurrentChunk(pos.getX(), pos.getZ())) return false;
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() - 1) return false;
        if (!level.ensureCanWrite(pos)) return false;

        BlockState current = level.getBlockState(pos);
        if (isForeignStructurePiece(pos)) return false;
        if (current.hasBlockEntity()) return false;
        AuthoredOwnerType existingOwner = authoredOwnerAt(pos);
        if (current.equals(target)) {
            // Exact material equality is not proof of LR authorship. Preserve unknown matching
            // foreign blocks rather than silently adopting them into starter provenance.
            if (existingOwner != null) {
                return AuthoredOwnerType.allowsOverwrite(existingOwner, ownerType);
            }
            return clearing && current.isAir();
        }
        if (existingOwner != null) {
            if (!AuthoredOwnerType.allowsOverwrite(existingOwner, ownerType)) return false;
        } else if (!mayReplaceForOwner(pos, current, clearing, ownerType)) {
            return false;
        }
        BlockState placed = needsConnectionPostprocessing(target)
                ? Block.updateFromNeighbourShapes(target, level, pos)
                : target;
        boolean changed = level.setBlock(pos, placed, WORLDGEN_FLAGS);
        if (changed && needsConnectionPostprocessing(placed)) {
            // Structure/worldgen placement normally defers final neighbour-shape resolution.
            // Mark only connectable LR blocks; full-volume postprocessing would be unnecessary.
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4).markPosForPostprocessing(pos);
        }
        if (changed && ownerType != null) {
            // Keep transient ownership even for cleared air so later operations in this same
            // feature invocation see the correct LR author. Persistent provenance, however, only
            // describes physical blocks; recording every excavated interior/vegetation AIR cell
            // would bloat chunk NBT without protecting any material.
            authoredOwnerByPos.put(pos.asLong(), ownerType);
            if (!placed.isAir()) {
                authoredWrites.add(new StarterCivilizationWorldgenContext.AuthoredWrite(
                        pos.getX(), pos.getY(), pos.getZ(), ownerType));
            }
        }
        return changed;
    }

    public boolean fillFoundation(long factionId, int x, int topY, int z,
                                  AuthoredOwnerType ownerType) {
        if (!insideCurrentChunk(x, z)) return false;
        int ground = terrainY(x, z);
        if (topY - ground > 64) return false;
        boolean changed = false;
        for (int y = ground; y <= topY; y++) {
            changed |= write(factionId, PaletteSlot.FOUNDATION, new BlockPos(x, y, z),
                    0, false, null, ownerType);
        }
        return changed;
    }

    /**
     * Clears only natural/replaceable vegetation above a road column. The scan is two-pass so an
     * unknown solid aborts before any partial clearing occurs.
     */
    public boolean clearNaturalVegetationAbove(
            int x, int groundY, int z, int height, AuthoredOwnerType ownerType) {
        if (!insideCurrentChunk(x, z) || height <= 0) return false;
        for (int dy = 1; dy <= height; dy++) {
            BlockPos pos = new BlockPos(x, groundY + dy, z);
            if (isForeignStructurePiece(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            boolean clearable = state.canBeReplaced()
                    || state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE)
                    || state.is(Blocks.SNOW) || state.is(Blocks.MOSS_CARPET)
                    || state.is(Blocks.VINE) || state.is(Blocks.CACTUS)
                    || state.is(Blocks.BAMBOO) || state.is(Blocks.BAMBOO_SAPLING)
                    || (state.is(BlockTags.LOGS) && isNaturalTreeLog(pos));
            if (!clearable) return false;
        }
        for (int dy = 1; dy <= height; dy++) {
            BlockPos pos = new BlockPos(x, groundY + dy, z);
            if (level.getBlockState(pos).isAir()) continue;
            if (!writeState(pos, Blocks.AIR.defaultBlockState(), true, ownerType)) return false;
        }
        return true;
    }

    /**
     * Roadside fabric may use dry natural ground, but water columns require a bridge/road deck
     * authored by Living Realms. This prevents floating waystations beside a one-block bridge.
     */
    public boolean canSupportRoadside(BlockPos support, boolean waterColumn) {
        if (support == null || !insideCurrentChunk(support.getX(), support.getZ())) return false;
        BlockState state = level.getBlockState(support);
        if (isForeignStructurePiece(support) || state.hasBlockEntity()) return false;
        AuthoredOwnerType owner = authoredOwnerAt(support);
        if (owner == AuthoredOwnerType.SETTLEMENT_ROAD
                || owner == AuthoredOwnerType.INTERCITY_ROUTE
                || owner == AuthoredOwnerType.ROADSIDE_SITE) {
            return true;
        }
        if (waterColumn) return false;
        return naturalTerrain(state) || state.is(Blocks.DIRT_PATH);
    }

    /**
     * Non-mutating preflight used to keep one structure slice atomic inside the current chunk.
     * Exact-target equality is intentionally not special-cased: an unknown foreign block that
     * happens to match the palette still remains foreign rather than being silently adopted.
     */
    public boolean canReplaceForWorldgen(
            BlockPos pos, boolean clearing, AuthoredOwnerType ownerType) {
        if (pos == null || ownerType == null
                || !insideCurrentChunk(pos.getX(), pos.getZ())) return false;
        if (pos.getY() <= level.getMinBuildHeight()
                || pos.getY() >= level.getMaxBuildHeight() - 1) return false;
        if (isForeignStructurePiece(pos)) return false;
        BlockState current = level.getBlockState(pos);
        if (current.hasBlockEntity()) return false;
        AuthoredOwnerType existingOwner = authoredOwnerAt(pos);
        if (existingOwner != null) {
            return AuthoredOwnerType.allowsOverwrite(existingOwner, ownerType);
        }
        return mayReplaceForOwner(pos, current, clearing, ownerType);
    }

    private boolean mayReplaceForOwner(
            BlockPos pos, BlockState current, boolean clearing, AuthoredOwnerType ownerType) {
        if (mayReplace(pos, current, clearing)) return true;
        return ownerType == AuthoredOwnerType.WIZARD_TREES && wizardNaturalUnderground(current);
    }

    private AuthoredOwnerType authoredOwnerAt(BlockPos pos) {
        AuthoredOwnerType owner = authoredOwnerByPos.get(pos.asLong());
        return owner != null ? owner : ModWorldgenAttachments.ownerAt(level, pos);
    }

    public boolean isForeignStructurePiece(BlockPos pos) {
        if (pos == null) return false;
        for (BoundingBox box : foreignStructurePieces) {
            if (box.isInside(pos)) return true;
        }
        return false;
    }

    public boolean insideCurrentChunk(int x, int z) {
        return Math.floorDiv(x, 16) == chunkX && Math.floorDiv(z, 16) == chunkZ;
    }

    public WorldGenLevel level() { return level; }

    public List<StarterCivilizationWorldgenContext.AuthoredWrite> authoredWrites() {
        return List.copyOf(authoredWrites);
    }

    private int sampleTerrainY(int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        int floor = level.getMinBuildHeight() + 1;
        while (y > floor) {
            BlockPos pos = new BlockPos(x, y, z);
            // A previously generated neighboring slice may already contain LR geometry at this
            // sample coordinate. Ignore those authored blocks when recovering the original surface;
            // otherwise fixed multi-chunk structures acquire different base Y by generation order.
            if (ModWorldgenAttachments.ownerAt(level, pos) != null) {
                y--;
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || isFrozenWater(state)
                    || state.is(BlockTags.LEAVES) || state.canBeReplaced()
                    || state.is(Blocks.SNOW) || state.is(Blocks.VINE)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.BAMBOO)
                    || state.is(Blocks.BAMBOO_SAPLING)
                    || (state.is(BlockTags.LOGS) && isNaturalTreeLog(pos))) {
                y--;
                continue;
            }
            break;
        }
        return y;
    }

    private static int columnIndex(int x, int z) {
        return ((x & 15) << 4) | (z & 15);
    }

    private boolean mayReplace(BlockPos pos, BlockState current, boolean clearing) {
        if (current.isAir()) return true;
        if (current.canBeReplaced() || current.is(BlockTags.LEAVES) || current.is(BlockTags.REPLACEABLE)) return true;
        if (current.is(BlockTags.LOGS) && isNaturalTreeLog(pos)) return true;
        if (naturalTerrain(current)) return true;
        // Never bulldoze foreign structure solids or existing block entities during generation.
        return false;
    }

    /**
     * Tagged logs are treated as natural only when nearby leaves exist and their trunk resolves
     * downward into natural terrain. This keeps player/foreign log construction fail-closed.
     */
    private boolean isNaturalTreeLog(BlockPos log) {
        BlockState state = level.getBlockState(log);
        if (!state.is(BlockTags.LOGS)) return false;
        return naturalTreeLogSnapshot.computeIfAbsent(log.asLong(), ignored -> classifyNaturalTreeLog(log));
    }

    /**
     * Classification is cached on first observation. The writer snapshots every current-chunk
     * terrain column before LR writes, so ordinary trunks encountered by surface sampling retain
     * their original natural/foreign identity even after nearby LR vegetation clears.
     */
    private boolean classifyNaturalTreeLog(BlockPos log) {
        boolean leaves = false;
        for (BlockPos p : BlockPos.betweenClosed(log.offset(-3, -1, -3), log.offset(3, 6, 3))) {
            if (level.getBlockState(p).is(BlockTags.LEAVES)) {
                leaves = true;
                break;
            }
        }
        if (!leaves) return false;
        for (int dy = 1; dy <= 3; dy++) {
            BlockState below = level.getBlockState(log.below(dy));
            if (below.isAir() || below.canBeReplaced()
                    || below.is(BlockTags.LOGS) || below.is(BlockTags.LEAVES)) continue;
            return naturalTerrain(below);
        }
        return true;
    }

    private static boolean isFrozenWater(BlockState state) {
        return state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE);
    }

    private static boolean wizardNaturalUnderground(BlockState state) {
        return state.is(Tags.Blocks.ORES)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.CALCITE)
                || state.is(Blocks.DRIPSTONE_BLOCK);
    }

    private static boolean naturalTerrain(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.CLAY) || state.is(Blocks.MUD)
                || isFrozenWater(state)
                || !state.getFluidState().isEmpty();
    }

    private static boolean needsConnectionPostprocessing(BlockState state) {
        return state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock;
    }

    private static Direction facing(int quarterTurns) {
        Direction[] order = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
        return order[Math.floorMod(quarterTurns, 4)];
    }
}
