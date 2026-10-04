package dev.livingrealms.sim.construction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded provenance ledger for Living Realms-authored physical blocks.
 *
 * <p>Stored outside the binary SimulationStateCodec payload (Minecraft SavedData NBT), so schema 16
 * bytes remain untouched. Chunk count and per-chunk block counts are hard-capped.
 */
public final class AuthoredBlockLedger {
    public static final int MAX_CHUNKS = 4096;
    public static final int MAX_BLOCKS_PER_CHUNK = 8192;
    public static final String NBT_KEY = "AuthoredBlocksV1";

    private final Map<Long, Set<Long>> blocksByChunk = new LinkedHashMap<>();

    public boolean isAuthored(int x, int y, int z) {
        long chunk = chunkKey(x, z);
        Set<Long> set = blocksByChunk.get(chunk);
        return set != null && set.contains(blockKey(x, y, z));
    }

    public boolean record(int x, int y, int z) {
        long chunk = chunkKey(x, z);
        Set<Long> set = blocksByChunk.get(chunk);
        if (set == null) {
            if (blocksByChunk.size() >= MAX_CHUNKS) return false;
            set = new LinkedHashSet<>();
            blocksByChunk.put(chunk, set);
        }
        if (set.size() >= MAX_BLOCKS_PER_CHUNK && !set.contains(blockKey(x, y, z))) return false;
        return set.add(blockKey(x, y, z));
    }

    public boolean forget(int x, int y, int z) {
        long chunk = chunkKey(x, z);
        Set<Long> set = blocksByChunk.get(chunk);
        if (set == null) return false;
        boolean removed = set.remove(blockKey(x, y, z));
        if (set.isEmpty()) blocksByChunk.remove(chunk);
        return removed;
    }

    public int chunkCount() { return blocksByChunk.size(); }
    public int blockCount() {
        int n = 0;
        for (Set<Long> set : blocksByChunk.values()) n += set.size();
        return n;
    }

    public Map<Long, Set<Long>> snapshot() {
        Map<Long, Set<Long>> out = new LinkedHashMap<>();
        for (Map.Entry<Long, Set<Long>> e : blocksByChunk.entrySet()) {
            out.put(e.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(e.getValue())));
        }
        return Collections.unmodifiableMap(out);
    }

    public void clear() { blocksByChunk.clear(); }

    public void load(Map<Long, long[]> packedByChunk) {
        Objects.requireNonNull(packedByChunk, "packedByChunk");
        blocksByChunk.clear();
        for (Map.Entry<Long, long[]> e : packedByChunk.entrySet()) {
            if (blocksByChunk.size() >= MAX_CHUNKS) break;
            Set<Long> set = new LinkedHashSet<>();
            for (long packed : e.getValue()) {
                if (set.size() >= MAX_BLOCKS_PER_CHUNK) break;
                set.add(packed);
            }
            if (!set.isEmpty()) blocksByChunk.put(e.getKey(), set);
        }
    }

    public static long chunkKey(int x, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    /** Packs block coords into a long; Y is offset by +2048 so negative Overworld Y fits. */
    public static long blockKey(int x, int y, int z) {
        int lx = x & 15;
        int lz = z & 15;
        int yy = y + 2048;
        if (yy < 0 || yy > 4095) yy = Math.max(0, Math.min(4095, yy));
        return (((long) yy) << 8) | (lx << 4) | lz;
    }
}
