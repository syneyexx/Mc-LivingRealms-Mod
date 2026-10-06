package dev.livingrealms.minecraft.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Additive NeoForge/Minecraft feature invoked during the final top-layer decoration step.
 * It writes only the current chunk's deterministic Living Realms starter-fabric slice.
 */
public final class StarterCivilizationFabricFeature extends Feature<NoneFeatureConfiguration> {
    public StarterCivilizationFabricFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var published = StarterCivilizationWorldgenContext.context(context.level());
        if (published.isEmpty()) return false;

        var worldgen = published.get();
        int chunkX = Math.floorDiv(context.origin().getX(), 16);
        int chunkZ = Math.floorDiv(context.origin().getZ(), 16);
        var slice = worldgen.fabricIndex().query(chunkX, chunkZ);
        var routeSlice = worldgen.routeGeometryIndex().query(chunkX, chunkZ);
        var wizardSlice = worldgen.wizardTreesIndex().query(chunkX, chunkZ);
        if (slice.isEmpty() && routeSlice.isEmpty() && wizardSlice.isEmpty()) return false;

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
        return writes > 0;
    }
}
