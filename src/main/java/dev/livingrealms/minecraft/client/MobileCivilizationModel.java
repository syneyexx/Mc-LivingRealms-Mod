package dev.livingrealms.minecraft.client;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.MobileCivilizationEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

/** Humanoid LOD representative for traveling families/refugees and pirates. */
public final class MobileCivilizationModel extends HumanoidModel<MobileCivilizationEntity> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"mobile_civilization"),"main");
    public MobileCivilizationModel(ModelPart root){super(root);}
    public static LayerDefinition createBodyLayer(){return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0.0F),64,64);}
}
