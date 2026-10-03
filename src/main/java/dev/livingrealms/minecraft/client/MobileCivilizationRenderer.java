package dev.livingrealms.minecraft.client;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.MobileCivilizationEntity;
import dev.livingrealms.minecraft.entity.MobileCivilizationKind;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/** Reuses LivingRealms citizen skins while keeping pirate equipment visibly rendered. */
public final class MobileCivilizationRenderer extends MobRenderer<MobileCivilizationEntity,MobileCivilizationModel> {
    private static final ResourceLocation[] TEXTURES=new ResourceLocation[12];
    static{for(int i=0;i<TEXTURES.length;i++)TEXTURES[i]=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/faction_citizen_"+i+".png");}
    public MobileCivilizationRenderer(EntityRendererProvider.Context context){
        super(context,new MobileCivilizationModel(context.bakeLayer(MobileCivilizationModel.LAYER)),.45F);
        addLayer(new ItemInHandLayer<>(this,context.getItemInHandRenderer()));
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<MobileCivilizationEntity>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<MobileCivilizationEntity>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }
    @Override public ResourceLocation getTextureLocation(MobileCivilizationEntity entity){int salt=entity.kind()==MobileCivilizationKind.PIRATE?7:0;int index=Math.floorMod(Long.hashCode(entity.canonicalId())+entity.projectionSlot()*3+salt,TEXTURES.length);return TEXTURES[index];}
}
