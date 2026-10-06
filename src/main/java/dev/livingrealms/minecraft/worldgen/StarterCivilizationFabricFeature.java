package dev.livingrealms.minecraft.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Additive NeoForge/Minecraft feature invoked during the surface-structures decoration step.
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

        int chunkX = Math.floorDiv(context.origin().getX(), 16);
        int chunkZ = Math.floorDiv(context.origin().getZ(), 16);
        var slice = published.get().fabricIndex().query(chunkX, chunkZ);
        if (slice.isEmpty()) return false;

        WorldgenFabricBlockWriter writer =
                new WorldgenFabricBlockWriter(context.level(), context.chunkGenerator(), chunkX, chunkZ);
        return StarterCivilizationChunkGenerator.generate(writer, slice, chunkX, chunkZ) > 0;
    }
}
