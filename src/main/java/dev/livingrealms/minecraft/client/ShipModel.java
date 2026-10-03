package dev.livingrealms.minecraft.client;

import com.mojang.blaze3d.vertex.*;
import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.LivingRealmsShipEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

/** Low-poly strategic ship silhouette; ship class changes scale/stats rather than requiring a renderer per class. */
public final class ShipModel extends EntityModel<LivingRealmsShipEntity>{
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"ship"),"main");
    private final ModelPart hull,deck,superstructure;
    public ShipModel(ModelPart root){hull=root.getChild("hull");deck=root.getChild("deck");superstructure=root.getChild("superstructure");}
    public static LayerDefinition createBodyLayer(){MeshDefinition mesh=new MeshDefinition();PartDefinition root=mesh.getRoot();root.addOrReplaceChild("hull",CubeListBuilder.create().texOffs(0,0).addBox(-6,-3,-14,12,5,28),PartPose.offset(0,20,0));root.addOrReplaceChild("deck",CubeListBuilder.create().texOffs(0,34).addBox(-5,-2,-11,10,2,22),PartPose.offset(0,17,0));root.addOrReplaceChild("superstructure",CubeListBuilder.create().texOffs(0,58).addBox(-3,-6,-2,6,6,8).texOffs(30,58).addBox(-1,-11,2,2,5,2),PartPose.offset(0,17,0));return LayerDefinition.create(mesh,128,128);}
    @Override public void setupAnim(LivingRealmsShipEntity entity,float limbSwing,float limbSwingAmount,float ageInTicks,float netHeadYaw,float headPitch){}
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer consumer,int packedLight,int packedOverlay,int color){hull.render(pose,consumer,packedLight,packedOverlay,color);deck.render(pose,consumer,packedLight,packedOverlay,color);superstructure.render(pose,consumer,packedLight,packedOverlay,color);}
}
