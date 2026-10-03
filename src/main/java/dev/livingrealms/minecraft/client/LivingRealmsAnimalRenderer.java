package dev.livingrealms.minecraft.client;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.LivingRealmsAnimalEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class LivingRealmsAnimalRenderer extends MobRenderer<LivingRealmsAnimalEntity, LivingRealmsAnimalModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            LivingRealms.MOD_ID, "textures/entity/wildlife.png");

    public LivingRealmsAnimalRenderer(EntityRendererProvider.Context context) {
        super(context, new LivingRealmsAnimalModel(context.bakeLayer(LivingRealmsAnimalModel.LAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(LivingRealmsAnimalEntity entity) {
        return TEXTURE;
    }
}
