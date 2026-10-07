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
    // Blueprints may intentionally overhang the nominal lot (culture minimum house sizes,
    // porches/wings/roof eaves). Keep fixed structures visible to every intersecting chunk so a
    // house cannot be clipped merely because its authored blueprint is wider than the intent lot.
    private static final int FIXED_STRUCTURE_MARGIN = 16;

    private ConstructionIntentChunkSelector() {}

    public static boolean intersects(ConstructionIntent intent, int chunkX, int chunkZ) {
        Objects.requireNonNull(intent, "intent");
        return intersects(intent, new ChunkWindow(chunkX, chunkZ));
    }

    public static boolean intersects(ConstructionIntent intent, ChunkWindow chunk) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(chunk, "chunk");
        int minX, maxX, minZ, maxZ;
        if (intent.hasPath()) {
            double pathMinX = intent.path().stream().mapToDouble(p -> p.x()).min().orElse(intent.center().x());
            double pathMaxX = intent.path().stream().mapToDouble(p -> p.x()).max().orElse(intent.center().x());
            double pathMinZ = intent.path().stream().mapToDouble(p -> p.z()).min().orElse(intent.center().z());
            double pathMaxZ = intent.path().stream().mapToDouble(p -> p.z()).max().orElse(intent.center().z());
            int margin = intent.width() / 2 + ACCESS_MARGIN;
            minX = (int) Math.floor(pathMinX) - margin;
            maxX = (int) Math.ceil(pathMaxX) + margin;
            minZ = (int) Math.floor(pathMinZ) - margin;
            maxZ = (int) Math.ceil(pathMaxZ) + margin;
        } else {
            int turns = Math.floorMod(intent.rotationQuarterTurns(), 4);
            int width = (turns & 1) == 0 ? intent.width() : intent.depth();
            int depth = (turns & 1) == 0 ? intent.depth() : intent.width();
            int cx = (int) Math.round(intent.center().x());
            int cz = (int) Math.round(intent.center().z());
            minX = cx - width / 2 - FIXED_STRUCTURE_MARGIN;
            maxX = cx + (width - 1) / 2 + FIXED_STRUCTURE_MARGIN;
            minZ = cz - depth / 2 - FIXED_STRUCTURE_MARGIN;
            maxZ = cz + (depth - 1) / 2 + FIXED_STRUCTURE_MARGIN;
        }
        return maxX >= chunk.minX() && minX <= chunk.maxX()
                && maxZ >= chunk.minZ() && minZ <= chunk.maxZ();
    }

    public static boolean intersectsAny(ConstructionIntent intent, Collection<ChunkWindow> chunks) {
        Objects.requireNonNull(chunks, "chunks");
        for (ChunkWindow chunk : chunks) if (intersects(intent, chunk)) return true;
        return false;
    }
}
