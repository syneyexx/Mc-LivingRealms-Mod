package dev.livingrealms.minecraft.client;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.FactionCitizenEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

/** Standard humanoid-compatible citizen model so vanilla/modded armor and held-item layers render correctly. */
public final class FactionCitizenModel extends HumanoidModel<FactionCitizenEntity> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"faction_citizen"),"main");
    public FactionCitizenModel(ModelPart root){super(root);}
    public static LayerDefinition createBodyLayer(){return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0.0F),64,64);}
}
