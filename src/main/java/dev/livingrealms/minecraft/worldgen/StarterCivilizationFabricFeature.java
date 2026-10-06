package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.LivingRealms;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Additive NeoForge/Minecraft feature invoked during the final top-layer decoration step.
 * It writes only the current chunk's deterministic Living Realms starter-fabric slice.
 */
public final class StarterCivilizationFabricFeature extends Feature<NoneFeatureConfiguration> {
    private static final AtomicBoolean FIRST_CHUNK_TRACE = new AtomicBoolean();
    private static final AtomicInteger SLOW_LOG_BUDGET = new AtomicInteger(12);

    public StarterCivilizationFabricFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var published = StarterCivilizationWorldgenContext.context(context.level());
        if (published.isEmpty()) return false;

        var worldgen = published.get();
        int chunkX;
        int chunkZ;
        if (context.level() instanceof WorldGenRegion region) {
            var center = region.getCenter();
            chunkX = center.x;
            chunkZ = center.z;
        } else {
            chunkX = Math.floorDiv(context.origin().getX(), 16);
            chunkZ = Math.floorDiv(context.origin().getZ(), 16);
        }
        boolean trace = FIRST_CHUNK_TRACE.compareAndSet(false, true);
        long started = System.nanoTime();
        if (trace) {
            LivingRealms.LOGGER.info(
                    "Starter fabric first chunk begin chunk=({}, {})", chunkX, chunkZ);
        }

        // Resolve route geometry first: lazy route completion also aligns and indexes its
        // roadside sites, so the fabric query below sees them in this same worldgen invocation.
        var routeSlice = worldgen.routeGeometryIndex().query(chunkX, chunkZ);
        long afterRoutes = System.nanoTime();
        if (trace) {
            LivingRealms.LOGGER.info(
                    "Starter fabric first chunk routes ready in {} ms; routeSlices={}",
                    millis(started, afterRoutes), routeSlice.routes().size());
        }

        var slice = worldgen.fabricIndex().query(chunkX, chunkZ);
        long afterFabric = System.nanoTime();
        if (trace) {
            LivingRealms.LOGGER.info(
                    "Starter fabric first chunk settlements ready in {} ms; settlementFabric={}",
                    millis(afterRoutes, afterFabric), slice.settlementFabric().size());
        }

        var wizardSlice = worldgen.wizardTreesIndex().query(chunkX, chunkZ);
        long afterWizard = System.nanoTime();
        if (slice.isEmpty() && routeSlice.isEmpty() && wizardSlice.isEmpty()) {
            if (trace) {
                LivingRealms.LOGGER.info(
                        "Starter fabric first chunk complete: no Living Realms fabric; total={} ms",
                        millis(started, afterWizard));
            }
            slowLog(chunkX, chunkZ, started, afterRoutes, afterFabric, afterWizard, afterWizard);
            return false;
        }

        WorldgenFabricBlockWriter writer =
                new WorldgenFabricBlockWriter(context.level(), chunkX, chunkZ, worldgen);
        int writes = StarterCivilizationChunkGenerator.generate(
                writer, slice, routeSlice, chunkX, chunkZ);
        if (!wizardSlice.isEmpty()) {
            writes += WizardTreesChunkGenerator.generate(writer, wizardSlice);
        }
        List<StarterCivilizationWorldgenContext.AuthoredWrite> authored = writer.authoredWrites();
        if (!authored.isEmpty()) {
            List<Long> packed = new ArrayList<>(authored.size());
            for (StarterCivilizationWorldgenContext.AuthoredWrite write : authored) {
                packed.add(ModWorldgenAttachments.ChunkProvenance.pack(
                        write.x(), write.y(), write.z(), write.ownerType()));
            }
            var chunk = context.level().getChunk(chunkX, chunkZ);
            var type = ModWorldgenAttachments.STARTER_FABRIC_PROVENANCE.get();
            var existing = chunk.hasData(type)
                    ? chunk.getData(type)
                    : ModWorldgenAttachments.ChunkProvenance.empty();
            chunk.setData(type, existing.merge(worldgen.worldgenVersion(), packed));
        }

        long finished = System.nanoTime();
        if (trace) {
            LivingRealms.LOGGER.info(
                    "Starter fabric first chunk complete in {} ms; writes={}",
                    millis(started, finished), writes);
        }
        slowLog(chunkX, chunkZ, started, afterRoutes, afterFabric, afterWizard, finished);
        return writes > 0;
    }

    private static void slowLog(
            int chunkX,
            int chunkZ,
            long started,
            long afterRoutes,
            long afterFabric,
            long afterWizard,
            long finished) {
        long totalMs = millis(started, finished);
        if (totalMs < 500 || SLOW_LOG_BUDGET.getAndDecrement() <= 0) return;
        LivingRealms.LOGGER.warn(
                "Slow starter worldgen chunk=({}, {}) total={} ms "
                        + "[routes={} ms, settlements={} ms, wizard={} ms, generate={} ms]",
                chunkX, chunkZ, totalMs,
                millis(started, afterRoutes),
                millis(afterRoutes, afterFabric),
                millis(afterFabric, afterWizard),
                millis(afterWizard, finished));
    }

    private static long millis(long from, long to) {
        return Math.max(0L, (to - from) / 1_000_000L);
    }
}
