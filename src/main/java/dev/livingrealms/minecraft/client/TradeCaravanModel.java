package dev.livingrealms.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.TradeCaravanEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Pack-animal silhouette for light loads; wagon bed + cargo crates for heavy escorted shipments.
 */
public final class TradeCaravanModel extends EntityModel<TradeCaravanEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID, "trade_caravan"), "main");
    private final ModelPart body, head, packLeft, packRight, leg0, leg1, leg2, leg3, wagon, cargo, yoke;

    public TradeCaravanModel(ModelPart root) {
        body = root.getChild("body");
        head = root.getChild("head");
        packLeft = root.getChild("pack_left");
        packRight = root.getChild("pack_right");
        leg0 = root.getChild("leg0");
        leg1 = root.getChild("leg1");
        leg2 = root.getChild("leg2");
        leg3 = root.getChild("leg3");
        wagon = root.getChild("wagon");
        cargo = root.getChild("cargo");
        yoke = root.getChild("yoke");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5, -5, -8, 10, 10, 16), PartPose.offset(0, 14, 0));
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 26).addBox(-3, -3, -5, 6, 6, 6), PartPose.offset(0, 12, -9));
        root.addOrReplaceChild("pack_left", CubeListBuilder.create().texOffs(36, 0).addBox(-4, -4, -5, 4, 8, 10), PartPose.offset(-5, 13, 0));
        root.addOrReplaceChild("pack_right", CubeListBuilder.create().texOffs(36, 0).addBox(0, -4, -5, 4, 8, 10), PartPose.offset(5, 13, 0));
        for (int i = 0; i < 4; i++) {
            float x = (i % 2 == 0 ? -3 : 3), z = (i < 2 ? 5 : -5);
            root.addOrReplaceChild("leg" + i, CubeListBuilder.create().texOffs(48, 22).addBox(-1.5F, 0, -1.5F, 3, 10, 3), PartPose.offset(x, 15, z));
        }
        root.addOrReplaceChild("wagon", CubeListBuilder.create().texOffs(0, 42).addBox(-7, -3, -2, 14, 6, 16), PartPose.offset(0, 16, 10));
        root.addOrReplaceChild("cargo", CubeListBuilder.create().texOffs(36, 42).addBox(-5, -6, 0, 10, 6, 10), PartPose.offset(0, 13, 12));
        root.addOrReplaceChild("yoke", CubeListBuilder.create().texOffs(48, 0).addBox(-6, -1, -1, 12, 2, 2), PartPose.offset(0, 14, 6));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(TradeCaravanEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        boolean wagonMode = entity.wagonMode();
        packLeft.visible = !wagonMode;
        packRight.visible = !wagonMode;
        wagon.visible = wagonMode;
        cargo.visible = wagonMode;
        yoke.visible = wagonMode;
        head.yRot = netHeadYaw * ((float) Math.PI / 180F);
        head.xRot = headPitch * ((float) Math.PI / 180F);
        float stride = (wagonMode ? 1.05F : 1.25F) * limbSwingAmount;
        leg0.xRot = Mth.cos(limbSwing * .6662F) * stride;
        leg1.xRot = Mth.cos(limbSwing * .6662F + (float) Math.PI) * stride;
        leg2.xRot = leg1.xRot;
        leg3.xRot = leg0.xRot;
        if (wagonMode) {
            float bob = Mth.sin(limbSwing * .5F) * limbSwingAmount * .04F;
            wagon.y = 16 + bob;
            cargo.y = 13 + bob;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        body.render(poseStack, buffer, packedLight, packedOverlay, color);
        head.render(poseStack, buffer, packedLight, packedOverlay, color);
        packLeft.render(poseStack, buffer, packedLight, packedOverlay, color);
        packRight.render(poseStack, buffer, packedLight, packedOverlay, color);
        leg0.render(poseStack, buffer, packedLight, packedOverlay, color);
        leg1.render(poseStack, buffer, packedLight, packedOverlay, color);
        leg2.render(poseStack, buffer, packedLight, packedOverlay, color);
        leg3.render(poseStack, buffer, packedLight, packedOverlay, color);
        wagon.render(poseStack, buffer, packedLight, packedOverlay, color);
        cargo.render(poseStack, buffer, packedLight, packedOverlay, color);
        yoke.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
