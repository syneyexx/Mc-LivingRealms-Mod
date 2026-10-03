package dev.livingrealms.minecraft;

import dev.livingrealms.sim.biome.BiomeObservation;
import dev.livingrealms.sim.biome.BiomeSignalNormalizer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

/** Converts the loaded Minecraft/NeoForge biome at a position into loader-neutral ecology facts. */
public final class MinecraftBiomeBridge {
    private MinecraftBiomeBridge() {}

    public static BiomeObservation observe(ServerLevel level, BlockPos pos) {
        Holder<Biome> holder=level.getBiome(pos);
        Biome biome=holder.value();
        String sourceId=holder.unwrapKey().map(k->k.location().toString()).orElse("minecraft:unregistered");
        List<String> tagPaths=new ArrayList<>();
        holder.tags().forEach(tag->tagPaths.add(tag.location().toString()));
        Biome.ClimateSettings climate=biome.getModifiedClimateSettings();
        return BiomeSignalNormalizer.observation(sourceId,tagPaths,climate.temperature(),climate.downfall());
    }
}
