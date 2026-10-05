package dev.livingrealms.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.LivingRealmsAnimalEntity;
import dev.livingrealms.sim.animal.AnimalIntent;
import dev.livingrealms.sim.ecology.SpeciesDefinition;
import dev.livingrealms.sim.ecology.SpeciesVisualFamily;
import dev.livingrealms.sim.ecology.SpeciesVisualFamilyResolver;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Multi-morphology wildlife model with intent-driven walk/run/idle/eat/flee/swim/fly/rest profiles. */
public final class LivingRealmsAnimalModel extends EntityModel<LivingRealmsAnimalEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID, "wildlife"), "main");
    private final ModelPart body, head, leg0, leg1, leg2, leg3, fishBody, fishTail, finLeft, finRight, birdBody, birdHead, wingLeft, wingRight, reptileBody, reptileTail;

    public LivingRealmsAnimalModel(ModelPart root) {
        body = root.getChild("body");
        head = root.getChild("head");
        leg0 = root.getChild("leg0");
        leg1 = root.getChild("leg1");
        leg2 = root.getChild("leg2");
        leg3 = root.getChild("leg3");
        fishBody = root.getChild("fish_body");
        fishTail = root.getChild("fish_tail");
        finLeft = root.getChild("fin_left");
        finRight = root.getChild("fin_right");
        birdBody = root.getChild("bird_body");
        birdHead = root.getChild("bird_head");
        wingLeft = root.getChild("wing_left");
        wingRight = root.getChild("wing_right");
        reptileBody = root.getChild("reptile_body");
        reptileTail = root.getChild("reptile_tail");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -5, -7, 8, 10, 14), PartPose.offset(0, 15, 0));
        r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 24).addBox(-3, -3, -5, 6, 6, 6), PartPose.offset(0, 13, -7));
        for (int i = 0; i < 4; i++) {
            float x = (i % 2 == 0 ? -2.5F : 2.5F), z = (i < 2 ? 4.5F : -4.5F);
            r.addOrReplaceChild("leg" + i, CubeListBuilder.create().texOffs(44, 0).addBox(-1.5F, 0, -1.5F, 3, 9, 3), PartPose.offset(x, 15, z));
        }
        r.addOrReplaceChild("fish_body", CubeListBuilder.create().texOffs(0, 38).addBox(-3, -4, -9, 6, 8, 18), PartPose.offset(0, 15, 0));
        r.addOrReplaceChild("fish_tail", CubeListBuilder.create().texOffs(48, 34).addBox(-1, -5, 0, 2, 10, 8), PartPose.offset(0, 15, 8));
        r.addOrReplaceChild("fin_left", CubeListBuilder.create().texOffs(38, 52).addBox(-8, 0, -2, 8, 1, 5), PartPose.offset(-2, 16, -1));
        r.addOrReplaceChild("fin_right", CubeListBuilder.create().texOffs(38, 52).mirror().addBox(0, 0, -2, 8, 1, 5), PartPose.offset(2, 16, -1));
        // Compact bird: body + head + wings share a tight silhouette so flying ducks don't look like loose cubes.
        r.addOrReplaceChild("bird_body", CubeListBuilder.create().texOffs(0, 38).addBox(-2.5F, -3.0F, -4.0F, 5, 6, 8), PartPose.offset(0, 17, 0));
        r.addOrReplaceChild("bird_head", CubeListBuilder.create().texOffs(32, 38).addBox(-1.5F, -1.5F, -3.5F, 3, 3, 4)
                .texOffs(46, 38).addBox(-0.5F, 0.0F, -5.0F, 1, 1, 2), PartPose.offset(0, 14.5F, -4.0F));
        r.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(0, 58).addBox(-8, -0.5F, -2.5F, 8, 1, 5), PartPose.offset(-2.5F, 16, 0));
        r.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(0, 58).mirror().addBox(0, -0.5F, -2.5F, 8, 1, 5), PartPose.offset(2.5F, 16, 0));
        r.addOrReplaceChild("reptile_body", CubeListBuilder.create().texOffs(0, 0).addBox(-5, -3, -9, 10, 6, 18), PartPose.offset(0, 19, 0));
        r.addOrReplaceChild("reptile_tail", CubeListBuilder.create().texOffs(38, 0).addBox(-3, -2, 0, 6, 4, 15), PartPose.offset(0, 19, 8));
        return LayerDefinition.create(mesh, 64, 72);
    }

    @Override
    public void setupAnim(LivingRealmsAnimalEntity e, float swing, float amount, float age, float yaw, float pitch) {
        SpeciesDefinition sp = e.species();
        SpeciesVisualFamily family = sp == null ? SpeciesVisualFamily.GENERIC_QUADRUPED
                : SpeciesVisualFamilyResolver.resolve(sp);
        var plan = SpeciesVisualFamilyResolver.modelPlan(family);
        boolean fish = plan == SpeciesVisualFamilyResolver.ModelPlan.FISH
                || plan == SpeciesVisualFamilyResolver.ModelPlan.MARINE_MAMMAL;
        boolean bird = plan == SpeciesVisualFamilyResolver.ModelPlan.BIRD;
        boolean reptile = plan == SpeciesVisualFamilyResolver.ModelPlan.REPTILE;
        boolean quad = !fish && !bird && !reptile;
        resetPartScales();
        body.visible = quad;
        head.visible = quad;
        leg0.visible = quad;
        leg1.visible = quad;
        leg2.visible = quad;
        leg3.visible = quad;
        fishBody.visible = fish;
        fishTail.visible = fish;
        finLeft.visible = fish;
        finRight.visible = fish;
        birdBody.visible = bird;
        birdHead.visible = bird;
        wingLeft.visible = bird;
        wingRight.visible = bird;
        reptileBody.visible = reptile;
        reptileTail.visible = reptile;

        // Family-aware base proportions (silhouette differences without unique Java models per species).
        if (quad) {
            float bodyY = switch (plan) {
                case BULKY_QUADRUPED -> 13.5F;
                case LIGHT_QUADRUPED -> 16.0F;
                case UNGULATE -> 14.5F;
                default -> 15.0F;
            };
            body.y = bodyY;
            head.y = bodyY - 2.0F;
            float legLen = switch (plan) {
                case BULKY_QUADRUPED -> 0.85F;
                case LIGHT_QUADRUPED -> 1.15F;
                case UNGULATE -> 1.25F;
                default -> 1.0F;
            };
            leg0.yScale = legLen; leg1.yScale = legLen; leg2.yScale = legLen; leg3.yScale = legLen;
            float bodyScaleX = plan == SpeciesVisualFamilyResolver.ModelPlan.BULKY_QUADRUPED ? 1.25F
                    : plan == SpeciesVisualFamilyResolver.ModelPlan.LIGHT_QUADRUPED ? 0.85F : 1.0F;
            float bodyScaleZ = family == SpeciesVisualFamily.FELID ? 0.9F
                    : family == SpeciesVisualFamily.URSID ? 1.15F
                    : family == SpeciesVisualFamily.ELEPHANT ? 1.35F : 1.0F;
            body.xScale = bodyScaleX; body.zScale = bodyScaleZ;
            head.xScale = family == SpeciesVisualFamily.CANID ? 0.9F
                    : family == SpeciesVisualFamily.ELEPHANT ? 1.2F : 1.0F;
        }
        if (fish) {
            boolean whale = family == SpeciesVisualFamily.CETACEAN;
            boolean seal = family == SpeciesVisualFamily.PINNIPED;
            boolean shark = family == SpeciesVisualFamily.SHARK;
            fishBody.xScale = whale ? 1.4F : seal ? 1.15F : shark ? 1.1F : 1.0F;
            fishBody.yScale = whale ? 1.3F : seal ? 0.85F : 1.0F;
            fishBody.zScale = whale ? 1.6F : seal ? 1.2F : shark ? 1.35F : 1.0F;
            fishTail.yScale = whale ? 1.4F : 1.0F;
        }
        if (bird) {
            boolean raptor = family == SpeciesVisualFamily.RAPTOR;
            boolean waterfowl = family == SpeciesVisualFamily.WATERFOWL;
            birdBody.xScale = raptor ? 1.15F : waterfowl ? 1.2F : 0.9F;
            birdBody.zScale = waterfowl ? 1.15F : 1.0F;
            wingLeft.xScale = raptor ? 1.25F : 1.0F;
            wingRight.xScale = raptor ? 1.25F : 1.0F;
        }
        if (reptile) {
            boolean snake = family == SpeciesVisualFamily.SNAKE;
            reptileBody.yScale = snake ? 0.55F : 1.0F;
            reptileBody.zScale = snake ? 1.6F : family == SpeciesVisualFamily.TURTLE ? 0.85F : 1.0F;
            reptileTail.zScale = snake ? 1.8F : 1.0F;
        }

        AnimalIntent intent = parseIntent(e.currentIntentName());
        float strideMul = switch (intent) {
            case FLEE -> 2.1F;
            case HUNT, DEFEND -> 1.7F;
            case REST, GRAZE, FORAGE, DRINK, MATE -> 0.35F;
            default -> 1.0F;
        };

        if (quad) {
            head.yRot = yaw * ((float) Math.PI / 180);
            float headPitch = pitch * ((float) Math.PI / 180);
            if (intent == AnimalIntent.GRAZE || intent == AnimalIntent.FORAGE || intent == AnimalIntent.DRINK) {
                head.xRot = 0.55F + Mth.sin(age * 0.12F) * 0.08F;
            } else if (intent == AnimalIntent.REST) {
                head.xRot = 0.18F;
                body.y = 16.2F;
            } else {
                head.xRot = headPitch;
                body.y = 15F;
            }
            float stride = 1.4F * amount * strideMul;
            float gait = intent == AnimalIntent.FLEE ? 0.9F : 0.6662F;
            leg0.xRot = Mth.cos(swing * gait) * stride;
            leg1.xRot = Mth.cos(swing * gait + (float) Math.PI) * stride;
            leg2.xRot = leg1.xRot;
            leg3.xRot = leg0.xRot;
            if (intent == AnimalIntent.REST && amount < 0.05F) {
                leg0.xRot = leg1.xRot = leg2.xRot = leg3.xRot = 0.08F;
            }
        }
        if (fish) {
            float swim = intent == AnimalIntent.FLEE ? 0.32F : intent == AnimalIntent.REST ? 0.06F : 0.18F;
            fishTail.yRot = Mth.sin(age * swim) * (intent == AnimalIntent.FLEE ? 0.85F : 0.55F);
            finLeft.zRot = -0.35F;
            finRight.zRot = 0.35F;
        }
        if (bird) {
            float flapSpeed = intent == AnimalIntent.FLEE || intent == AnimalIntent.HUNT ? 1.15F : intent == AnimalIntent.REST ? 0.12F : 0.65F;
            float flapAmp = intent == AnimalIntent.REST ? 0.08F : 0.65F;
            float flap = Mth.sin(age * flapSpeed) * flapAmp;
            wingLeft.zRot = -0.35F - flap;
            wingRight.zRot = 0.35F + flap;
            birdHead.yRot = yaw * ((float) Math.PI / 180);
            if (intent == AnimalIntent.FORAGE || intent == AnimalIntent.GRAZE) birdHead.xRot = 0.4F;
            else birdHead.xRot = 0F;
        }
        if (reptile) {
            float wag = intent == AnimalIntent.FLEE ? 0.28F : intent == AnimalIntent.REST ? 0.05F : 0.16F;
            reptileTail.yRot = Mth.sin(age * wag) * (intent == AnimalIntent.FLEE ? 0.45F : 0.28F);
        }
    }

    private void resetPartScales() {
        for (ModelPart part : new ModelPart[]{
                body, head, leg0, leg1, leg2, leg3, fishBody, fishTail, finLeft, finRight,
                birdBody, birdHead, wingLeft, wingRight, reptileBody, reptileTail}) {
            part.xScale = 1.0F;
            part.yScale = 1.0F;
            part.zScale = 1.0F;
        }
        body.y = 15.0F;
        head.y = 13.0F;
    }

    private static AnimalIntent parseIntent(String name) {
        if (name == null || name.isBlank()) return AnimalIntent.ROAM;
        try {
            return AnimalIntent.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return AnimalIntent.ROAM;
        }
    }

    @Override
    public void renderToBuffer(PoseStack p, VertexConsumer b, int light, int overlay, int color) {
        body.render(p, b, light, overlay, color);
        head.render(p, b, light, overlay, color);
        leg0.render(p, b, light, overlay, color);
        leg1.render(p, b, light, overlay, color);
        leg2.render(p, b, light, overlay, color);
        leg3.render(p, b, light, overlay, color);
        fishBody.render(p, b, light, overlay, color);
        fishTail.render(p, b, light, overlay, color);
        finLeft.render(p, b, light, overlay, color);
        finRight.render(p, b, light, overlay, color);
        birdBody.render(p, b, light, overlay, color);
        birdHead.render(p, b, light, overlay, color);
        wingLeft.render(p, b, light, overlay, color);
        wingRight.render(p, b, light, overlay, color);
        reptileBody.render(p, b, light, overlay, color);
        reptileTail.render(p, b, light, overlay, color);
    }
}
