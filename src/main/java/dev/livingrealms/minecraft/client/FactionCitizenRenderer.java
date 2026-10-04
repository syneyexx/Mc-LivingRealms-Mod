package dev.livingrealms.minecraft.client;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.FactionCitizenEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/** Stable citizen appearances plus visible held weapons/tools and vanilla/modded humanoid armor. */
public final class FactionCitizenRenderer extends MobRenderer<FactionCitizenEntity,FactionCitizenModel> {
    private static final ResourceLocation[] TEXTURES=new ResourceLocation[48];
    static {
        for(int i=0;i<TEXTURES.length;i++){
            TEXTURES[i]=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/faction_citizen_"+i+".png");
        }
    }

    public FactionCitizenRenderer(EntityRendererProvider.Context context){
        super(context,new FactionCitizenModel(context.bakeLayer(FactionCitizenModel.LAYER)),.45F);
        addLayer(new ItemInHandLayer<>(this,context.getItemInHandRenderer()));
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<FactionCitizenEntity>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<FactionCitizenEntity>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override public ResourceLocation getTextureLocation(FactionCitizenEntity entity){
        int idx=Math.floorMod(entity.skinVariant(),TEXTURES.length);
        ResourceLocation tex=TEXTURES[idx];
        return tex!=null?tex:TEXTURES[Math.floorMod(idx,12)];
    }
}
