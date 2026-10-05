package dev.livingrealms.minecraft.construction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded hand-off from NeoForge chunk lifecycle events to main-thread civilization materializers.
 *
 * <p>Chunk load events only record coordinates. They never inspect or mutate the level, because
 * NeoForge may fire the load event before the chunk has reached FULL status. Materializers drain
 * these hints later from the normal server tick.</p>
 */
public final class CivilizationFabricChunkQueue {
    public record ChunkRef(int x, int z) {
        public int minX() { return x << 4; }
        public int minZ() { return z << 4; }
        public int maxX() { return minX() + 15; }
        public int maxZ() { return minZ() + 15; }
    }

    private static final int MAX_PENDING_PER_DOMAIN = 8192;
    private static final Set<Long> SETTLEMENT = new LinkedHashSet<>();
    private static final Set<Long> TRANSPORT = new LinkedHashSet<>();
    private static Object boundLevelIdentity;

    private CivilizationFabricChunkQueue() {}

    public static synchronized void onChunkAvailable(Object levelIdentity, int chunkX, int chunkZ) {
        if (levelIdentity == null) return;
        if (boundLevelIdentity == null) boundLevelIdentity = levelIdentity;
        if (boundLevelIdentity != levelIdentity) return;
        long key = pack(chunkX, chunkZ);
        addBounded(SETTLEMENT, key);
        addBounded(TRANSPORT, key);
    }

    public static synchronized List<ChunkRef> pollSettlement(Object levelIdentity, int max) {
        return poll(SETTLEMENT, levelIdentity, max);
    }

    public static synchronized List<ChunkRef> pollTransport(Object levelIdentity, int max) {
        return poll(TRANSPORT, levelIdentity, max);
    }

    public static synchronized int pendingSettlementChunks() { return SETTLEMENT.size(); }
    public static synchronized int pendingTransportChunks() { return TRANSPORT.size(); }

    public static synchronized void clear() {
        SETTLEMENT.clear();
        TRANSPORT.clear();
        boundLevelIdentity = null;
    }

    private static void addBounded(Set<Long> queue, long key) {
        if (queue.contains(key)) return;
        if (queue.size() >= MAX_PENDING_PER_DOMAIN) {
            // The periodic loaded-chunk fallback in both materializers repairs an extreme burst.
            // Prefer recent chunk availability instead of allowing this static hand-off to grow.
            var it = queue.iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        queue.add(key);
    }

    private static List<ChunkRef> poll(Set<Long> queue, Object levelIdentity, int max) {
        if (max < 0) throw new IllegalArgumentException("max");
        if (max == 0 || levelIdentity == null || boundLevelIdentity != levelIdentity || queue.isEmpty()) {
            return List.of();
        }
        List<ChunkRef> out = new ArrayList<>(Math.min(max, queue.size()));
        var it = queue.iterator();
        while (it.hasNext() && out.size() < max) {
            long key = it.next();
            it.remove();
            out.add(new ChunkRef((int) (key >> 32), (int) key));
        }
        return List.copyOf(out);
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
