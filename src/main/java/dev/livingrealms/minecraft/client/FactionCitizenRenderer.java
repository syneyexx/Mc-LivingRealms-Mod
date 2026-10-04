package dev.livingrealms.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.FactionCitizenEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Stable citizen appearances plus clothing overlay, held items and armor. */
public final class FactionCitizenRenderer extends MobRenderer<FactionCitizenEntity,FactionCitizenModel> {
    private static final ResourceLocation[] TEXTURES=new ResourceLocation[48];
    private static final ResourceLocation[] OVERLAYS=new ResourceLocation[48];
    static {
        for(int i=0;i<TEXTURES.length;i++){
            TEXTURES[i]=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/faction_citizen_"+i+".png");
            OVERLAYS[i]=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/faction_citizen_"+i+"_overlay.png");
        }
    }

    public FactionCitizenRenderer(EntityRendererProvider.Context context){
        super(context,new FactionCitizenModel(context.bakeLayer(FactionCitizenModel.LAYER)),.45F);
        addLayer(new ItemInHandLayer<>(this,context.getItemInHandRenderer()));
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<FactionCitizenEntity>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<FactionCitizenEntity>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new ClothingOverlayLayer(this));
    }

    @Override public ResourceLocation getTextureLocation(FactionCitizenEntity entity){
        int idx=Math.floorMod(entity.skinVariant(),TEXTURES.length);
        ResourceLocation tex=TEXTURES[idx];
        return tex!=null?tex:TEXTURES[Math.floorMod(idx,12)];
    }

    private static final class ClothingOverlayLayer extends RenderLayer<FactionCitizenEntity,FactionCitizenModel> {
        ClothingOverlayLayer(FactionCitizenRenderer parent){super(parent);}

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, FactionCitizenEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            int idx=Math.floorMod(entity.skinVariant(),OVERLAYS.length);
            ResourceLocation overlay=OVERLAYS[idx];
            if(overlay==null)return;
            VertexConsumer consumer=buffers.getBuffer(RenderType.entityCutoutNoCull(overlay));
            getParentModel().renderToBuffer(pose,consumer,light,LivingEntityRenderer.getOverlayCoords(entity,0.0F));
        }
    }
}
