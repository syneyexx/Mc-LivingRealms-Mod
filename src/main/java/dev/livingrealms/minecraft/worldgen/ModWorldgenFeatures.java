package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.LivingRealms;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Static feature-type registration; configured/placed instances live in the built-in datapack. */
public final class ModWorldgenFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, LivingRealms.MOD_ID);

    public static final Supplier<Feature<NoneFeatureConfiguration>> STARTER_CIVILIZATION_FABRIC =
            FEATURES.register("starter_civilization_fabric", StarterCivilizationFabricFeature::new);

    private ModWorldgenFeatures() {}

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}
