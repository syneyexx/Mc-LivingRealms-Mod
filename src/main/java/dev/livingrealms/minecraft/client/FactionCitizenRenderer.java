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
    private static final int ATLAS_SIZE=48;
    private static final int AVAILABLE_TEXTURES=12;
    private static final ResourceLocation[] TEXTURES=new ResourceLocation[ATLAS_SIZE];
    static {
        ResourceLocation[] base=new ResourceLocation[AVAILABLE_TEXTURES];
        for(int i=0;i<AVAILABLE_TEXTURES;i++)base[i]=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/faction_citizen_"+i+".png");
        for(int i=0;i<ATLAS_SIZE;i++)TEXTURES[i]=base[Math.floorMod(i,base.length)];
    }

    public FactionCitizenRenderer(EntityRendererProvider.Context context){
        super(context,new FactionCitizenModel(context.bakeLayer(FactionCitizenModel.LAYER)),.45F);
        addLayer(new ItemInHandLayer<>(this,context.getItemInHandRenderer()));
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<FactionCitizenEntity>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<FactionCitizenEntity>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override public ResourceLocation getTextureLocation(FactionCitizenEntity entity){return TEXTURES[Math.floorMod(entity.skinVariant(),TEXTURES.length)];}
}
