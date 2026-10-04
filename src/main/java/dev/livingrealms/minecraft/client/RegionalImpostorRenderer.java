package dev.livingrealms.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.livingrealms.minecraft.entity.RegionalImpostorEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Flat colored billboard / pole for regional impostors — intentionally cheap. */
public final class RegionalImpostorRenderer extends EntityRenderer<RegionalImpostorEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public RegionalImpostorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(RegionalImpostorEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        float h = switch (entity.kind()) {
            case MILITARY_BANNER -> 2.4f;
            case SAIL -> 2.8f;
            case SETTLEMENT_BUSTLE -> 1.6f;
            case MIGRATION -> 1.8f;
            case CARAVAN_DUST -> 1.2f;
            case HERD -> 1.0f;
        };
        float w = switch (entity.kind()) {
            case SAIL -> 1.4f;
            case MILITARY_BANNER -> 0.55f;
            default -> 0.9f;
        };
        int rgb = entity.colorRgb();
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        Matrix4f m = pose.last().pose();
        // Simple vertical quad facing the camera approximately (axis-aligned for cost).
        float y0 = 0.1f, y1 = h;
        float x0 = -w * .5f, x1 = w * .5f;
        quad(vc, m, x0, y0, 0, x1, y0, 0, x1, y1, 0, x0, y1, 0, r, g, b, 0.85f, light);
        pose.popPose();
        super.render(entity, yaw, partial, pose, buffers, light);
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float r, float g, float b, float a, int light) {
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
    }

    @Override
    public ResourceLocation getTextureLocation(RegionalImpostorEntity entity) {
        return WHITE;
    }
}
