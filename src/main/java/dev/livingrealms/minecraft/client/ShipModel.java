package dev.livingrealms.minecraft.client;

import com.mojang.blaze3d.vertex.*;
import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.LivingRealmsShipEntity;
import dev.livingrealms.sim.naval.ShipClass;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

/**
 * Scalable vessel family: cargo hulks, landing barges and warships share one mesh with
 * class-driven part visibility rather than one generic placeholder silhouette.
 */
public final class ShipModel extends EntityModel<LivingRealmsShipEntity>{
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"ship"),"main");
    private final ModelPart hull,deck,superstructure,mast,cargoStack,bowsprit,gunwale;
    public ShipModel(ModelPart root){
        hull=root.getChild("hull");deck=root.getChild("deck");superstructure=root.getChild("superstructure");
        mast=root.getChild("mast");cargoStack=root.getChild("cargo_stack");bowsprit=root.getChild("bowsprit");gunwale=root.getChild("gunwale");
    }
    public static LayerDefinition createBodyLayer(){
        MeshDefinition mesh=new MeshDefinition();PartDefinition root=mesh.getRoot();
        root.addOrReplaceChild("hull",CubeListBuilder.create().texOffs(0,0).addBox(-6,-3,-14,12,5,28),PartPose.offset(0,20,0));
        root.addOrReplaceChild("deck",CubeListBuilder.create().texOffs(0,34).addBox(-5,-2,-11,10,2,22),PartPose.offset(0,17,0));
        root.addOrReplaceChild("superstructure",CubeListBuilder.create().texOffs(0,58).addBox(-3,-6,-2,6,6,8).texOffs(30,58).addBox(-1,-11,2,2,5,2),PartPose.offset(0,17,0));
        root.addOrReplaceChild("mast",CubeListBuilder.create().texOffs(48,58).addBox(-1,-16,-1,2,16,2).texOffs(56,34).addBox(-8,-14,0,16,1,1),PartPose.offset(0,17,0));
        root.addOrReplaceChild("cargo_stack",CubeListBuilder.create().texOffs(80,0).addBox(-4,-5,-6,8,4,10),PartPose.offset(0,17,0));
        root.addOrReplaceChild("bowsprit",CubeListBuilder.create().texOffs(80,20).addBox(-1,-1,-20,2,2,8),PartPose.offset(0,18,0));
        root.addOrReplaceChild("gunwale",CubeListBuilder.create().texOffs(80,34).addBox(-6.5F,-1,-12,1,2,24).texOffs(80,34).addBox(5.5F,-1,-12,1,2,24),PartPose.offset(0,17,0));
        return LayerDefinition.create(mesh,128,128);
    }
    @Override public void setupAnim(LivingRealmsShipEntity entity,float limbSwing,float limbSwingAmount,float ageInTicks,float netHeadYaw,float headPitch){
        ShipClass cls=entity.shipClass();
        boolean cargo=cls==ShipClass.CARGO_SHIP;
        boolean landing=cls==ShipClass.LANDING_SHIP;
        boolean war=!cargo&&!landing;
        mast.visible=cargo||cls==ShipClass.PATROL_BOAT||cls==ShipClass.CORVETTE;
        cargoStack.visible=cargo;
        bowsprit.visible=cargo||landing||cls==ShipClass.PATROL_BOAT;
        gunwale.visible=war||landing;
        superstructure.visible=war||landing;
    }
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer consumer,int packedLight,int packedOverlay,int color){
        hull.render(pose,consumer,packedLight,packedOverlay,color);
        deck.render(pose,consumer,packedLight,packedOverlay,color);
        if(superstructure.visible)superstructure.render(pose,consumer,packedLight,packedOverlay,color);
        if(mast.visible)mast.render(pose,consumer,packedLight,packedOverlay,color);
        if(cargoStack.visible)cargoStack.render(pose,consumer,packedLight,packedOverlay,color);
        if(bowsprit.visible)bowsprit.render(pose,consumer,packedLight,packedOverlay,color);
        if(gunwale.visible)gunwale.render(pose,consumer,packedLight,packedOverlay,color);
    }
}
