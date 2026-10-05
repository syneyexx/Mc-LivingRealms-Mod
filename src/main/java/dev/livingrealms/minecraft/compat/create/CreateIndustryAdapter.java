package dev.livingrealms.minecraft.compat.create;

import dev.livingrealms.api.IndustryAdapter;
import dev.livingrealms.minecraft.construction.CreateBlockLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * Wave 22 — Create industry projection adapter boundary.
 * Hard Create dependency remains via {@link CreateBlockLookup}; canonical sim never imports Create classes.
 */
public final class CreateIndustryAdapter implements IndustryAdapter {
    public static final CreateIndustryAdapter INSTANCE = new CreateIndustryAdapter();

    private CreateIndustryAdapter() {}

    @Override
    public String resolveBlockPath(String preferredCreatePath, String vanillaFallbackPath) {
        if (preferredCreatePath != null && !preferredCreatePath.isBlank()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("create", preferredCreatePath);
            if (BuiltInRegistries.BLOCK.containsKey(id)) return "create:" + preferredCreatePath;
        }
        return vanillaFallbackPath == null ? "minecraft:iron_block" : vanillaFallbackPath;
    }

    @Override
    public boolean createAvailable() {
        return BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("create", "andesite_casing"));
    }

    /** Convenience for materializers that still need Block instances. */
    public Block resolveBlock(String preferredCreatePath, Block fallback) {
        return CreateBlockLookup.orElse(preferredCreatePath, fallback);
    }
}
