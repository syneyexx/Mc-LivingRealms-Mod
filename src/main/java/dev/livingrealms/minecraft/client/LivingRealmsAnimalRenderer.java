package dev.livingrealms.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.LivingRealmsAnimalEntity;
import dev.livingrealms.sim.ecology.SpeciesDefinition;
import dev.livingrealms.sim.ecology.SpeciesMorphology;
import dev.livingrealms.sim.ecology.SpeciesMorphologyResolver;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Morphology-family wildlife renderer with mass-driven scale and family textures. */
public final class LivingRealmsAnimalRenderer extends MobRenderer<LivingRealmsAnimalEntity, LivingRealmsAnimalModel> {
    private static final ResourceLocation FALLBACK = ResourceLocation.fromNamespaceAndPath(
            LivingRealms.MOD_ID, "textures/entity/wildlife.png");
    private static final java.util.EnumMap<SpeciesMorphology, ResourceLocation> FAMILY_TEXTURES =
            new java.util.EnumMap<>(SpeciesMorphology.class);
    static {
        for (SpeciesMorphology morph : SpeciesMorphology.values()) {
            FAMILY_TEXTURES.put(morph, ResourceLocation.fromNamespaceAndPath(
                    LivingRealms.MOD_ID, "textures/entity/wildlife_" + morph.name().toLowerCase(java.util.Locale.ROOT) + ".png"));
        }
    }

    public LivingRealmsAnimalRenderer(EntityRendererProvider.Context context) {
        super(context, new LivingRealmsAnimalModel(context.bakeLayer(LivingRealmsAnimalModel.LAYER)), 0.5F);
    }

    @Override
    protected void scale(LivingRealmsAnimalEntity entity, PoseStack pose, float partialTick) {
        SpeciesDefinition sp = entity.species();
        float scale = 1.0F;
        if (sp != null) {
            SpeciesMorphology morph = SpeciesMorphologyResolver.resolve(sp);
            double mass = Math.max(0.2, sp.adultMassKg());
            scale = switch (morph) {
                case SMALL_QUADRUPED -> Mth.clamp((float) Math.pow(mass / 8.0, 0.28), 0.35F, 0.75F);
                case UNGULATE -> Mth.clamp((float) Math.pow(mass / 120.0, 0.30), 0.55F, 1.55F);
                case PREDATOR_QUADRUPED -> Mth.clamp((float) Math.pow(mass / 60.0, 0.30), 0.45F, 1.35F);
                case BEAR -> Mth.clamp((float) Math.pow(mass / 180.0, 0.28), 0.85F, 1.7F);
                case LARGE_MAMMAL -> Mth.clamp((float) Math.pow(mass / 800.0, 0.25), 1.2F, 2.4F);
                case CROCODILIAN -> Mth.clamp((float) Math.pow(mass / 200.0, 0.28), 0.7F, 1.8F);
                case FISH -> Mth.clamp((float) Math.pow(mass / 20.0, 0.32), 0.3F, 1.6F);
                case CETACEAN -> Mth.clamp((float) Math.pow(mass / 5000.0, 0.22), 1.4F, 3.2F);
                case PINNIPED -> Mth.clamp((float) Math.pow(mass / 200.0, 0.28), 0.7F, 1.6F);
                case BIRD -> Mth.clamp((float) Math.pow(mass / 3.0, 0.30), 0.35F, 1.2F);
            };
            // Deterministic stretch by species id so similar masses still differ slightly.
            long h = sp.id().hashCode() & 0xffffffffL;
            scale *= 0.94F + ((h % 13) * 0.01F);
        }
        pose.scale(scale, scale, scale);
        this.shadowRadius = 0.25F * scale;
    }

    @Override
    public ResourceLocation getTextureLocation(LivingRealmsAnimalEntity entity) {
        SpeciesDefinition sp = entity.species();
        if (sp == null) return FALLBACK;
        ResourceLocation family = FAMILY_TEXTURES.get(SpeciesMorphologyResolver.resolve(sp));
        return family != null ? family : FALLBACK;
    }
}
