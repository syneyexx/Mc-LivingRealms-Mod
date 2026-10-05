package dev.livingrealms.sim.construction;

import java.util.Collection;
import java.util.Objects;

/**
 * Pure chunk-window selection for deterministic settlement fabric.
 *
 * <p>This class deliberately has no Minecraft dependency so chunk generation/load policy can be
 * tested headlessly. It answers whether an intent's complete horizontal footprint (plus a small
 * access margin) touches a chunk that has become available.</p>
 */
public final class ConstructionIntentChunkSelector {
    public record ChunkWindow(int chunkX, int chunkZ) {
        public int minX() { return chunkX << 4; }
        public int minZ() { return chunkZ << 4; }
        public int maxX() { return minX() + 15; }
        public int maxZ() { return minZ() + 15; }
    }

    private static final int ACCESS_MARGIN = 2;

    private ConstructionIntentChunkSelector() {}

    public static boolean intersects(ConstructionIntent intent, int chunkX, int chunkZ) {
        Objects.requireNonNull(intent, "intent");
        return intersects(intent, new ChunkWindow(chunkX, chunkZ));
    }

    public static boolean intersects(ConstructionIntent intent, ChunkWindow chunk) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(chunk, "chunk");
        int turns = Math.floorMod(intent.rotationQuarterTurns(), 4);
        int width = (turns & 1) == 0 ? intent.width() : intent.depth();
        int depth = (turns & 1) == 0 ? intent.depth() : intent.width();
        int cx = (int) Math.round(intent.center().x());
        int cz = (int) Math.round(intent.center().z());
        int minX = cx - width / 2 - ACCESS_MARGIN;
        int maxX = cx + (width - 1) / 2 + ACCESS_MARGIN;
        int minZ = cz - depth / 2 - ACCESS_MARGIN;
        int maxZ = cz + (depth - 1) / 2 + ACCESS_MARGIN;
        return maxX >= chunk.minX() && minX <= chunk.maxX()
                && maxZ >= chunk.minZ() && minZ <= chunk.maxZ();
    }

    public static boolean intersectsAny(ConstructionIntent intent, Collection<ChunkWindow> chunks) {
        Objects.requireNonNull(chunks, "chunks");
        for (ChunkWindow chunk : chunks) if (intersects(intent, chunk)) return true;
        return false;
    }
}
