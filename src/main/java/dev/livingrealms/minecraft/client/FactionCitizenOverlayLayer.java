package dev.livingrealms.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.FactionCitizenEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Soft role/clothing tint from faction_citizen_*_overlay.png (humanoid UV). */
public final class FactionCitizenOverlayLayer extends RenderLayer<FactionCitizenEntity, FactionCitizenModel> {
    private static final ResourceLocation[] OVERLAYS = new ResourceLocation[48];
    static {
        for (int i = 0; i < OVERLAYS.length; i++) {
            OVERLAYS[i] = ResourceLocation.fromNamespaceAndPath(
                    LivingRealms.MOD_ID, "textures/entity/faction_citizen_" + i + "_overlay.png");
        }
    }

    public FactionCitizenOverlayLayer(RenderLayerParent<FactionCitizenEntity, FactionCitizenModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, FactionCitizenEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        int idx = Math.floorMod(entity.skinVariant(), OVERLAYS.length);
        ResourceLocation tex = OVERLAYS[idx];
        // Skip if the overlay asset is missing from the classpath (dedicated clients without assets).
        java.io.InputStream probe = FactionCitizenOverlayLayer.class.getResourceAsStream(
                "/assets/" + LivingRealms.MOD_ID + "/textures/entity/faction_citizen_" + idx + "_overlay.png");
        if (probe == null) return;
        try { probe.close(); } catch (java.io.IOException ignored) {}
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(tex));
        getParentModel().renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY);
    }
}
