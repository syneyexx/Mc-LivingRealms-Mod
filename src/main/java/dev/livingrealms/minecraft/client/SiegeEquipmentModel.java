package dev.livingrealms.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.SiegeEquipmentEntity;
import dev.livingrealms.sim.military.SiegeEquipmentKind;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

/** Differentiated siege machinery: ram body, ladder frame, or artillery frame from one mesh family. */
public final class SiegeEquipmentModel extends EntityModel<SiegeEquipmentEntity> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"siege_equipment"),"main");
    private final ModelPart base,ram,ladder,artillery;

    public SiegeEquipmentModel(ModelPart root){
        base=root.getChild("base");ram=root.getChild("ram");ladder=root.getChild("ladder");artillery=root.getChild("artillery");
    }

    public static LayerDefinition createBodyLayer(){
        MeshDefinition mesh=new MeshDefinition();PartDefinition root=mesh.getRoot();
        root.addOrReplaceChild("base",CubeListBuilder.create().texOffs(0,0).addBox(-6,0,-8,12,2,16),PartPose.offset(0,22,0));
        root.addOrReplaceChild("ram",CubeListBuilder.create()
                .texOffs(0,20).addBox(-2,2,-14,4,4,20)
                .texOffs(48,20).addBox(-3,1,-16,6,6,4)
                .texOffs(0,48).addBox(-8,0,-6,2,10,2)
                .texOffs(0,48).addBox(6,0,-6,2,10,2),PartPose.offset(0,12,0));
        root.addOrReplaceChild("ladder",CubeListBuilder.create()
                .texOffs(56,0).addBox(-5,0,-1,2,24,2)
                .texOffs(56,0).addBox(3,0,-1,2,24,2)
                .texOffs(64,0).addBox(-5,4,-1,10,1,2)
                .texOffs(64,0).addBox(-5,10,-1,10,1,2)
                .texOffs(64,0).addBox(-5,16,-1,10,1,2)
                .texOffs(64,0).addBox(-5,22,-1,10,1,2),PartPose.offset(0,0,0));
        root.addOrReplaceChild("artillery",CubeListBuilder.create()
                .texOffs(0,64).addBox(-5,0,-6,10,4,12)
                .texOffs(48,64).addBox(-2,4,-2,4,8,10)
                .texOffs(80,64).addBox(-8,1,-4,3,3,3)
                .texOffs(80,64).addBox(5,1,-4,3,3,3),PartPose.offset(0,18,0));
        return LayerDefinition.create(mesh,128,128);
    }

    @Override public void setupAnim(SiegeEquipmentEntity entity,float limbSwing,float limbSwingAmount,float ageInTicks,float netHeadYaw,float headPitch){
        SiegeEquipmentKind kind=entity.kind();
        ram.visible=kind==SiegeEquipmentKind.RAM;
        ladder.visible=kind==SiegeEquipmentKind.LADDER;
        artillery.visible=kind==SiegeEquipmentKind.ARTILLERY;
        base.visible=kind!=SiegeEquipmentKind.LADDER;
        if(kind==SiegeEquipmentKind.ARTILLERY)artillery.xRot=(float)Math.sin(ageInTicks*.03)*.08f;
        if(kind==SiegeEquipmentKind.RAM)ram.z=(float)Math.sin(ageInTicks*.08)*1.2f;
    }

    @Override public void renderToBuffer(PoseStack pose,VertexConsumer consumer,int packedLight,int packedOverlay,int color){
        if(base.visible)base.render(pose,consumer,packedLight,packedOverlay,color);
        if(ram.visible)ram.render(pose,consumer,packedLight,packedOverlay,color);
        if(ladder.visible)ladder.render(pose,consumer,packedLight,packedOverlay,color);
        if(artillery.visible)artillery.render(pose,consumer,packedLight,packedOverlay,color);
    }
}
