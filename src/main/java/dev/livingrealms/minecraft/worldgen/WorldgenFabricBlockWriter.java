package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.minecraft.construction.FactionBlockPalette;
import dev.livingrealms.sim.construction.PaletteSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Worldgen-only block writer. It never accesses SavedData, never force-loads chunks and writes only
 * the chunk currently being decorated.
 */
public final class WorldgenFabricBlockWriter {
    private static final int WORLDGEN_FLAGS =
            Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private final WorldGenLevel level;
    private final ChunkGenerator generator;
    private final int chunkX;
    private final int chunkZ;

    public WorldgenFabricBlockWriter(WorldGenLevel level, ChunkGenerator generator, int chunkX, int chunkZ) {
        this.level = level;
        this.generator = generator;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public int terrainY(int x, int z) {
        return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level,
                level.getLevel().getChunkSource().randomState()) - 1;
    }

    public int oceanFloorY(int x, int z) {
        return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level,
                level.getLevel().getChunkSource().randomState()) - 1;
    }

    public int siteBaseY(int centerX, int centerZ, int width, int depth, int turns) {
        int w = (turns & 1) == 0 ? width : depth;
        int d = (turns & 1) == 0 ? depth : width;
        int hx = Math.max(1, w / 2), hz = Math.max(1, d / 2);
        int y = terrainY(centerX, centerZ);
        y = Math.max(y, terrainY(centerX - hx, centerZ - hz));
        y = Math.max(y, terrainY(centerX + hx, centerZ - hz));
        y = Math.max(y, terrainY(centerX - hx, centerZ + hz));
        y = Math.max(y, terrainY(centerX + hx, centerZ + hz));
        return y;
    }

    public boolean write(long factionId, PaletteSlot slot, BlockPos pos,
                         int rotationQuarterTurns, boolean doorUpper, BedPart bedPart) {
        BlockState target = FactionBlockPalette.state(factionId, slot);
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
        return writeState(pos, target, slot == PaletteSlot.AIR);
    }

    public boolean writeState(BlockPos pos, BlockState target, boolean clearing) {
        if (!insideCurrentChunk(pos.getX(), pos.getZ())) return false;
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() - 1) return false;
        if (!level.ensureCanWrite(pos)) return false;

        BlockState current = level.getBlockState(pos);
        if (current.equals(target)) return true;
        if (current.hasBlockEntity()) return false;
        if (!mayReplace(current, clearing)) return false;
        return level.setBlock(pos, target, WORLDGEN_FLAGS);
    }

    public boolean fillFoundation(long factionId, int x, int topY, int z) {
        if (!insideCurrentChunk(x, z)) return false;
        int ground = terrainY(x, z);
        boolean changed = false;
        int bottom = Math.max(ground, topY - 12);
        for (int y = bottom; y <= topY; y++) {
            changed |= write(factionId, PaletteSlot.FOUNDATION, new BlockPos(x, y, z), 0, false, null);
        }
        return changed;
    }

    public boolean insideCurrentChunk(int x, int z) {
        return Math.floorDiv(x, 16) == chunkX && Math.floorDiv(z, 16) == chunkZ;
    }

    public WorldGenLevel level() { return level; }

    private static boolean mayReplace(BlockState current, boolean clearing) {
        if (current.isAir()) return true;
        if (current.canBeReplaced() || current.is(BlockTags.LEAVES) || current.is(BlockTags.REPLACEABLE)) return true;
        if (naturalTerrain(current)) return true;
        // Never bulldoze foreign structure solids or existing block entities during generation.
        return false;
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
