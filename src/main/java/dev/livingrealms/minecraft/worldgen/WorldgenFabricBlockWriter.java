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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.server.level.WorldGenRegion;

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
    private final List<StarterCivilizationWorldgenContext.AuthoredWrite> authoredWrites = new ArrayList<>();

    public WorldgenFabricBlockWriter(
            WorldGenLevel level, int chunkX, int chunkZ,
            StarterCivilizationWorldgenContext.Context worldgenContext) {
        this.level = level;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.worldgenContext = Objects.requireNonNull(worldgenContext, "worldgenContext");
        var structureManager = level instanceof WorldGenRegion region
                ? level.getLevel().structureManager().forWorldGenRegion(region)
                : level.getLevel().structureManager();
        List<BoundingBox> foreignPieces = new ArrayList<>();
        for (var start : structureManager.startsForStructure(
                new ChunkPos(chunkX, chunkZ), structure -> true)) {
            if (start == null || !start.isValid()) continue;
            for (var piece : start.getPieces()) foreignPieces.add(piece.getBoundingBox());
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
                    if (!level.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
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
            if (!level.getFluidState(new BlockPos(x, y, z)).isEmpty()) return y;
        }
        return ground;
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
        if (current.equals(target)) return true;
        if (current.hasBlockEntity()) return false;
        if (!mayReplace(pos, current, clearing)) return false;
        boolean changed = level.setBlock(pos, target, WORLDGEN_FLAGS);
        if (changed && ownerType != null) {
            authoredOwnerByPos.put(pos.asLong(), ownerType);
            authoredWrites.add(new StarterCivilizationWorldgenContext.AuthoredWrite(
                    pos.getX(), pos.getY(), pos.getZ(), ownerType));
        }
        return changed;
    }

    public boolean fillFoundation(long factionId, int x, int topY, int z,
                                  AuthoredOwnerType ownerType) {
        if (!insideCurrentChunk(x, z)) return false;
        int ground = terrainY(x, z);
        boolean changed = false;
        int bottom = Math.max(ground, topY - 12);
        for (int y = bottom; y <= topY; y++) {
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
        AuthoredOwnerType owner = authoredOwnerByPos.get(support.asLong());
        if (owner == null) owner = ModWorldgenAttachments.ownerAt(level, support);
        if (owner == AuthoredOwnerType.SETTLEMENT_ROAD
                || owner == AuthoredOwnerType.INTERCITY_ROUTE
                || owner == AuthoredOwnerType.ROADSIDE_SITE) {
            return true;
        }
        if (waterColumn) return false;
        return naturalTerrain(state) || state.is(Blocks.DIRT_PATH);
    }

    public boolean isForeignStructurePiece(BlockPos pos) {
        if (pos == null || !insideCurrentChunk(pos.getX(), pos.getZ())) return false;
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
            if (!state.getFluidState().isEmpty()
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
        if (clearing && current.is(BlockTags.LOGS) && isNaturalTreeLog(pos)) return true;
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

    private static boolean naturalTerrain(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.CLAY) || state.is(Blocks.MUD)
                || !state.getFluidState().isEmpty();
    }

    private static Direction facing(int quarterTurns) {
        Direction[] order = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
        return order[Math.floorMod(quarterTurns, 4)];
    }
}
