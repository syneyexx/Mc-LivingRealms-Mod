package dev.livingrealms.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.LivingRealmsAircraftEntity;
import dev.livingrealms.sim.aviation.AircraftRole;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Class-differentiated aircraft: fighters, recon, transport and bomber silhouettes share one mesh
 * with mission-driven part visibility and wing animation.
 */
public final class AircraftModel extends EntityModel<LivingRealmsAircraftEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID, "aircraft"), "main");
    private final ModelPart fuselage, wings, tail, prop, cargoPod, twinBoom, canopy, bombBay;

    public AircraftModel(ModelPart root) {
        fuselage = root.getChild("fuselage");
        wings = root.getChild("wings");
        tail = root.getChild("tail");
        prop = root.getChild("prop");
        cargoPod = root.getChild("cargo_pod");
        twinBoom = root.getChild("twin_boom");
        canopy = root.getChild("canopy");
        bombBay = root.getChild("bomb_bay");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("fuselage", CubeListBuilder.create().texOffs(0, 0).addBox(-2, -2, -10, 4, 4, 20), PartPose.offset(0, 16, 0));
        root.addOrReplaceChild("wings", CubeListBuilder.create().texOffs(0, 24).addBox(-14, -1, -3, 28, 2, 7), PartPose.offset(0, 16, 0));
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 34).addBox(-6, -1, 0, 12, 2, 5).texOffs(36, 34).addBox(-1, -7, 2, 2, 7, 4), PartPose.offset(0, 15, 8));
        root.addOrReplaceChild("prop", CubeListBuilder.create().texOffs(48, 0).addBox(-6, -.5F, -11.5F, 12, 1, 1), PartPose.offset(0, 16, 0));
        root.addOrReplaceChild("cargo_pod", CubeListBuilder.create().texOffs(0, 42).addBox(-3, 1, -4, 6, 4, 10), PartPose.offset(0, 16, 0));
        root.addOrReplaceChild("twin_boom", CubeListBuilder.create().texOffs(32, 42).addBox(-8, -1, 4, 2, 2, 10).texOffs(32, 42).addBox(6, -1, 4, 2, 2, 10), PartPose.offset(0, 16, 0));
        root.addOrReplaceChild("canopy", CubeListBuilder.create().texOffs(48, 8).addBox(-1.5F, -3.5F, -3, 3, 2, 5), PartPose.offset(0, 16, 0));
        root.addOrReplaceChild("bomb_bay", CubeListBuilder.create().texOffs(48, 16).addBox(-2.5F, 1.5F, -2, 5, 3, 8), PartPose.offset(0, 16, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(LivingRealmsAircraftEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        AircraftRole role = entity.aircraftModel().role();
        boolean transport = role == AircraftRole.TRANSPORT;
        boolean fighter = role == AircraftRole.FIGHTER || role == AircraftRole.INTERCEPTOR;
        boolean recon = role == AircraftRole.RECON;
        boolean strike = role == AircraftRole.BOMBER || role == AircraftRole.ATTACK;
        cargoPod.visible = transport;
        twinBoom.visible = recon;
        bombBay.visible = strike;
        canopy.visible = fighter || recon || !transport;
        prop.visible = true;
        float spin = fighter ? 1.25F : transport ? 0.5F : strike ? 0.7F : 0.9F;
        prop.zRot = ageInTicks * spin;
        float bank = Mth.sin(ageInTicks * (fighter ? 0.12F : 0.07F)) * (fighter ? 0.09F : recon ? 0.05F : 0.03F);
        wings.zRot = bank;
        fuselage.xRot = strike ? 0.04F : fighter ? -0.02F : 0F;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        fuselage.render(pose, buffer, packedLight, packedOverlay, color);
        wings.render(pose, buffer, packedLight, packedOverlay, color);
        tail.render(pose, buffer, packedLight, packedOverlay, color);
        if (prop.visible) prop.render(pose, buffer, packedLight, packedOverlay, color);
        if (cargoPod.visible) cargoPod.render(pose, buffer, packedLight, packedOverlay, color);
        if (twinBoom.visible) twinBoom.render(pose, buffer, packedLight, packedOverlay, color);
        if (canopy.visible) canopy.render(pose, buffer, packedLight, packedOverlay, color);
        if (bombBay.visible) bombBay.render(pose, buffer, packedLight, packedOverlay, color);
    }
}
