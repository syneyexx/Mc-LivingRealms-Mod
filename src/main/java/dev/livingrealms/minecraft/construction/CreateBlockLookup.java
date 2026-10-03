package dev.livingrealms.minecraft.construction;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/** Late registry lookup keeps the strategic core independent of Create implementation classes. */
public final class CreateBlockLookup {
    private CreateBlockLookup() {}

    public static Block orElse(String path, Block fallback) {
        ResourceLocation id=ResourceLocation.fromNamespaceAndPath("create",path);
        return BuiltInRegistries.BLOCK.containsKey(id)?BuiltInRegistries.BLOCK.get(id):fallback;
    }
}
