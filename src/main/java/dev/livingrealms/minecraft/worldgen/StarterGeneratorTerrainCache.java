package dev.livingrealms.minecraft.worldgen;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Thread-safe generator-only terrain cache shared by starter worldgen planning phases.
 *
 * <p>It never requests or loads chunks. Values come exclusively from ChunkGenerator base-height
 * queries, so settlement refinement and regional route planning can safely reuse the same samples
 * without making generation order observable.</p>
 */
final class StarterGeneratorTerrainCache {
    private final ServerLevel level;
    private final net.minecraft.world.level.chunk.ChunkGenerator generator;
    private final net.minecraft.world.level.levelgen.RandomState randomState;
    private final ConcurrentHashMap<Long, Integer> surfaceByColumn = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> groundByColumn = new ConcurrentHashMap<>();
    private final AtomicInteger surfaceMisses = new AtomicInteger();
    private final AtomicInteger groundMisses = new AtomicInteger();

    StarterGeneratorTerrainCache(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
        var chunkSource = level.getChunkSource();
        this.generator = chunkSource.getGenerator();
        this.randomState = chunkSource.randomState();
    }

    int surfaceY(int x, int z) {
        long key = pack(x, z);
        return surfaceByColumn.computeIfAbsent(key, ignored -> {
            surfaceMisses.incrementAndGet();
            return generator.getBaseHeight(
                    x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState) - 1;
        });
    }

    int groundY(int x, int z) {
        long key = pack(x, z);
        return groundByColumn.computeIfAbsent(key, ignored -> {
            groundMisses.incrementAndGet();
            return generator.getBaseHeight(
                    x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState) - 1;
        });
    }

    boolean water(int x, int z) {
        return surfaceY(x, z) > groundY(x, z);
    }

    int cachedSurfaceColumns() {
        return surfaceByColumn.size();
    }

    int cachedGroundColumns() {
        return groundByColumn.size();
    }

    int surfaceMisses() {
        return surfaceMisses.get();
    }

    int groundMisses() {
        return groundMisses.get();
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
