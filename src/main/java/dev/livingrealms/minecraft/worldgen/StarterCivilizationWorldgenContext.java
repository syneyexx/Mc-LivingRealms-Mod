package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import dev.livingrealms.sim.worldgen.StarterCivilizationFabricIndex;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;

/**
 * Server-lifetime publication of immutable starter worldgen planning plus a thread-safe,
 * metadata-only worker -> main-thread authored-block receipt handoff.
 *
 * <p>Activation happens on the main thread when the Overworld loads. Generation workers only read
 * the immutable fabric index and append immutable receipts; they never touch SavedData or canonical
 * mutable simulation collections.</p>
 */
public final class StarterCivilizationWorldgenContext {
    public record AuthoredWrite(int x, int y, int z, AuthoredOwnerType ownerType) {
        public AuthoredWrite {
            ownerType = Objects.requireNonNull(ownerType, "ownerType");
        }
    }

    public record GeneratedChunkReceipt(int chunkX, int chunkZ, List<AuthoredWrite> writes) {
        public GeneratedChunkReceipt {
            writes = List.copyOf(Objects.requireNonNull(writes, "writes"));
        }
    }

    public static final class Context {
        private final long worldSeed;
        private final int worldgenVersion;
        private final StarterCivilizationFabricIndex fabricIndex;
        private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<GeneratedChunkReceipt>> receipts =
                new ConcurrentHashMap<>();

        private Context(long worldSeed, int worldgenVersion, StarterCivilizationFabricIndex fabricIndex) {
            this.worldSeed = worldSeed;
            this.worldgenVersion = worldgenVersion;
            this.fabricIndex = Objects.requireNonNull(fabricIndex, "fabricIndex");
        }

        public long worldSeed() { return worldSeed; }
        public int worldgenVersion() { return worldgenVersion; }
        public StarterCivilizationFabricIndex fabricIndex() { return fabricIndex; }
    }

    private static final ConcurrentHashMap<ServerLevel, Context> BY_LEVEL = new ConcurrentHashMap<>();

    private StarterCivilizationWorldgenContext() {}

    public static void activate(ServerLevel level, LivingRealmsSavedData data) {
        if (level == null || data == null || level.dimension() != Level.OVERWORLD) return;
        if (!data.starterWorldgenEnabled()) {
            BY_LEVEL.remove(level);
            return;
        }
        long seed = level.getSeed();
        Context existing = BY_LEVEL.get(level);
        if (existing != null && existing.worldSeed() == seed
                && existing.worldgenVersion() == data.civilizationWorldgenVersion()) {
            return;
        }
        StarterCivilizationFabricIndex index = StarterCivilizationFabricIndex.build(
                StarterCivilizationLayoutPlanner.plan(seed));
        BY_LEVEL.put(level, new Context(seed, data.civilizationWorldgenVersion(), index));
    }

    public static Optional<Context> context(WorldGenLevel worldGenLevel) {
        if (worldGenLevel == null) return Optional.empty();
        ServerLevel level = worldGenLevel.getLevel();
        if (level.dimension() != Level.OVERWORLD) return Optional.empty();
        Context context = BY_LEVEL.get(level);
        if (context == null || context.worldSeed() != worldGenLevel.getSeed()) return Optional.empty();
        return Optional.of(context);
    }

    public static Optional<Context> context(ServerLevel level) {
        if (level == null || level.dimension() != Level.OVERWORLD) return Optional.empty();
        Context context = BY_LEVEL.get(level);
        if (context == null || context.worldSeed() != level.getSeed()) return Optional.empty();
        return Optional.of(context);
    }

    /** Worker-safe. Publishes only immutable provenance metadata after physical worldgen writes. */
    public static void publishReceipt(
            Context context, int chunkX, int chunkZ, List<AuthoredWrite> writes) {
        if (context == null || writes == null || writes.isEmpty()) return;
        long key = pack(chunkX, chunkZ);
        context.receipts.computeIfAbsent(key, ignored -> new ConcurrentLinkedQueue<>())
                .add(new GeneratedChunkReceipt(chunkX, chunkZ, writes));
    }

    /**
     * Main-thread only. Reconciles bounded worldgen receipts into SavedData without touching blocks.
     * Returns coordinates whose owner metadata changed.
     */
    public static int reconcileAvailable(
            ServerLevel level, LivingRealmsSavedData data, int maxChunks) {
        if (level == null || data == null || maxChunks <= 0) return 0;
        Context context = BY_LEVEL.get(level);
        if (context == null) return 0;

        int visitedChunks = 0;
        int metadataChanges = 0;
        for (Long key : List.copyOf(context.receipts.keySet())) {
            if (visitedChunks >= maxChunks) break;
            ConcurrentLinkedQueue<GeneratedChunkReceipt> queue = context.receipts.remove(key);
            if (queue == null) continue;
            visitedChunks++;
            GeneratedChunkReceipt receipt;
            while ((receipt = queue.poll()) != null) {
                for (AuthoredWrite write : receipt.writes()) {
                    AuthoredOwnerType before = data.authoredBlocks()
                            .ownerType(write.x(), write.y(), write.z());
                    if (data.authoredBlocks().record(
                            write.x(), write.y(), write.z(), write.ownerType())
                            && before != write.ownerType()) {
                        metadataChanges++;
                    }
                }
            }
        }
        if (metadataChanges > 0) data.setDirty();
        return metadataChanges;
    }

    public static boolean active(ServerLevel level) {
        return level != null && BY_LEVEL.containsKey(level);
    }

    public static int pendingReceiptChunks(ServerLevel level) {
        Context context = level == null ? null : BY_LEVEL.get(level);
        return context == null ? 0 : context.receipts.size();
    }

    public static void clear() {
        BY_LEVEL.clear();
    }

    private static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }
}
