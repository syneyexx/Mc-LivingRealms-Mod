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

/**
 * Multi-morphology wildlife model with intent-driven walk/run/idle/eat/flee/swim/fly/rest profiles.
 * Distinctive anatomy (trunk, horns, antlers, shell, …) is toggled per {@link SpeciesVisualFamily}
 * on one shared {@link LayerDefinition} — not one Java model per species.
 */
public final class LivingRealmsAnimalModel extends EntityModel<LivingRealmsAnimalEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID, "wildlife"), "main");
    private final ModelPart body, head, leg0, leg1, leg2, leg3, fishBody, fishTail, finLeft, finRight, birdBody, birdHead, wingLeft, wingRight, reptileBody, reptileTail;
    // Reusable attachment parts (show/hide + scale by visual family).
    private final ModelPart trunk, earLeft, earRight, tuskLeft, tuskRight, horn, horn2, antlerLeft, antlerRight, hump, shell, dorsalFin, snout, longNeck;

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
        trunk = root.getChild("trunk");
        earLeft = root.getChild("ear_left");
        earRight = root.getChild("ear_right");
        tuskLeft = root.getChild("tusk_left");
        tuskRight = root.getChild("tusk_right");
        horn = root.getChild("horn");
        horn2 = root.getChild("horn2");
        antlerLeft = root.getChild("antler_left");
        antlerRight = root.getChild("antler_right");
        hump = root.getChild("hump");
        shell = root.getChild("shell");
        dorsalFin = root.getChild("dorsal_fin");
        snout = root.getChild("snout");
        longNeck = root.getChild("long_neck");
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

        // Distinctive attachments — one shared mesh; visibility is family-driven in setupAnim.
        r.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(52, 24).addBox(-1.5F, 0, -1.5F, 3, 10, 3),
                PartPose.offsetAndRotation(0, 14, -11, 0.55F, 0, 0));
        r.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(0, 52).addBox(-8, -6, 0, 8, 8, 1),
                PartPose.offset(-3, 11, -6));
        r.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(0, 52).mirror().addBox(0, -6, 0, 8, 8, 1),
                PartPose.offset(3, 11, -6));
        r.addOrReplaceChild("tusk_left", CubeListBuilder.create().texOffs(56, 48).addBox(-1, 0, -6, 1, 1, 6),
                PartPose.offsetAndRotation(-2.5F, 15, -10, 0.35F, 0.15F, 0));
        r.addOrReplaceChild("tusk_right", CubeListBuilder.create().texOffs(56, 48).mirror().addBox(0, 0, -6, 1, 1, 6),
                PartPose.offsetAndRotation(2.5F, 15, -10, 0.35F, -0.15F, 0));
        r.addOrReplaceChild("horn", CubeListBuilder.create().texOffs(48, 48).addBox(-1, -6, -1, 2, 6, 2),
                PartPose.offset(0, 10, -9));
        r.addOrReplaceChild("horn2", CubeListBuilder.create().texOffs(48, 56).addBox(-0.75F, -4, -0.75F, 1.5F, 4, 1.5F),
                PartPose.offset(0, 11, -7.5F));
        r.addOrReplaceChild("antler_left", CubeListBuilder.create().texOffs(32, 48).addBox(-6, -8, -1, 6, 8, 1)
                .texOffs(32, 57).addBox(-8, -10, -1, 3, 3, 1),
                PartPose.offset(-2, 10, -6));
        r.addOrReplaceChild("antler_right", CubeListBuilder.create().texOffs(32, 48).mirror().addBox(0, -8, -1, 6, 8, 1)
                .texOffs(32, 57).mirror().addBox(5, -10, -1, 3, 3, 1),
                PartPose.offset(2, 10, -6));
        r.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(16, 52).addBox(-3, -5, -3, 6, 5, 6),
                PartPose.offset(0, 10, 0));
        r.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 0).addBox(-6, -4, -7, 12, 5, 14),
                PartPose.offset(0, 17, 0));
        r.addOrReplaceChild("dorsal_fin", CubeListBuilder.create().texOffs(48, 24).addBox(-0.5F, -8, -2, 1, 8, 5),
                PartPose.offset(0, 11, -1));
        r.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(40, 38).addBox(-2.5F, -2, -12, 5, 3, 12),
                PartPose.offset(0, 19, -8));
        r.addOrReplaceChild("long_neck", CubeListBuilder.create().texOffs(24, 24).addBox(-2, -10, -2, 4, 12, 4),
                PartPose.offset(0, 12, -6));
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

        applyAttachments(family, plan, quad, fish, reptile);

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
            if (family == SpeciesVisualFamily.CAMELID) {
                longNeck.y = bodyY - 3.0F;
                hump.y = bodyY - 5.0F;
                head.y = bodyY - 10.0F;
            }
            if (family == SpeciesVisualFamily.ELEPHANT) {
                trunk.y = head.y + 1.0F;
                earLeft.y = head.y - 2.0F;
                earRight.y = head.y - 2.0F;
                tuskLeft.y = head.y + 2.0F;
                tuskRight.y = head.y + 2.0F;
            }
            if (family == SpeciesVisualFamily.CERVID || family == SpeciesVisualFamily.BOVID
                    || family == SpeciesVisualFamily.RHINO) {
                horn.y = head.y - 3.0F;
                horn2.y = head.y - 2.0F;
                antlerLeft.y = head.y - 3.0F;
                antlerRight.y = head.y - 3.0F;
            }
        }
        if (fish) {
            boolean whale = family == SpeciesVisualFamily.CETACEAN;
            boolean seal = family == SpeciesVisualFamily.PINNIPED;
            boolean shark = family == SpeciesVisualFamily.SHARK;
            fishBody.xScale = whale ? 1.4F : seal ? 1.15F : shark ? 1.1F : 1.0F;
            fishBody.yScale = whale ? 1.3F : seal ? 0.85F : 1.0F;
            fishBody.zScale = whale ? 1.6F : seal ? 1.2F : shark ? 1.35F : 1.0F;
            fishTail.yScale = whale ? 1.4F : 1.0F;
            if (shark) {
                dorsalFin.yScale = 1.15F;
                dorsalFin.zScale = 1.1F;
            }
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
            if (family == SpeciesVisualFamily.CROCODILIAN) {
                snout.zScale = 1.25F;
                snout.yScale = 0.85F;
            }
            if (family == SpeciesVisualFamily.TURTLE) {
                shell.xScale = 1.15F;
                shell.yScale = 1.05F;
                shell.zScale = 1.1F;
            }
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
            // Keep head-anchored attachments tracking the head pose.
            if (trunk.visible) {
                trunk.xRot = 0.55F + head.xRot * 0.35F;
                trunk.yRot = head.yRot * 0.5F;
            }
            if (earLeft.visible) {
                earLeft.yRot = head.yRot;
                earRight.yRot = head.yRot;
            }
            if (antlerLeft.visible) {
                antlerLeft.yRot = head.yRot;
                antlerRight.yRot = head.yRot;
            }
            if (horn.visible) horn.yRot = head.yRot;
            if (horn2.visible) horn2.yRot = head.yRot;
            if (longNeck.visible) {
                longNeck.yRot = head.yRot * 0.6F;
                longNeck.xRot = head.xRot * 0.4F;
            }
        }
        if (fish) {
            float swim = intent == AnimalIntent.FLEE ? 0.32F : intent == AnimalIntent.REST ? 0.06F : 0.18F;
            fishTail.yRot = Mth.sin(age * swim) * (intent == AnimalIntent.FLEE ? 0.85F : 0.55F);
            finLeft.zRot = -0.35F;
            finRight.zRot = 0.35F;
            if (dorsalFin.visible) {
                dorsalFin.zRot = Mth.sin(age * swim * 0.5F) * 0.04F;
            }
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
            if (snout.visible) {
                snout.yRot = yaw * ((float) Math.PI / 180) * 0.25F;
            }
        }
    }

    private void applyAttachments(SpeciesVisualFamily family, SpeciesVisualFamilyResolver.ModelPlan plan,
                                  boolean quad, boolean fish, boolean reptile) {
        hideAttachments();
        switch (family) {
            case ELEPHANT -> {
                if (!quad) break;
                trunk.visible = true;
                earLeft.visible = true;
                earRight.visible = true;
                tuskLeft.visible = true;
                tuskRight.visible = true;
                earLeft.xScale = earLeft.yScale = 1.35F;
                earRight.xScale = earRight.yScale = 1.35F;
                trunk.yScale = 1.15F;
            }
            case RHINO -> {
                if (!quad) break;
                horn.visible = true;
                horn2.visible = true;
                horn.yScale = 1.25F;
                horn2.yScale = 0.85F;
            }
            case BOVID -> {
                if (!quad) break;
                horn.visible = true;
                horn2.visible = true;
                // Lateralized short horns via offset scales rather than unique meshes.
                horn.xScale = 0.85F;
                horn2.xScale = 0.85F;
                horn.x = -1.5F;
                horn2.x = 1.5F;
            }
            case CERVID -> {
                if (!quad) break;
                antlerLeft.visible = true;
                antlerRight.visible = true;
                antlerLeft.yScale = 1.1F;
                antlerRight.yScale = 1.1F;
            }
            case CAMELID -> {
                if (!quad) break;
                hump.visible = true;
                longNeck.visible = true;
                hump.yScale = 1.2F;
                longNeck.yScale = 1.35F;
                // Hide default head cube offset — long_neck carries the silhouette upward.
                head.yScale = 0.95F;
            }
            case TURTLE -> {
                if (!reptile) break;
                shell.visible = true;
            }
            case SHARK -> {
                if (!fish) break;
                dorsalFin.visible = true;
            }
            case CROCODILIAN -> {
                if (!reptile) break;
                snout.visible = true;
            }
            default -> {
                // GENERIC / other families: no distinctive attachments.
            }
        }
        // plan unused beyond family routing — kept for call-site clarity / future ungulate tweaks
        if (plan == SpeciesVisualFamilyResolver.ModelPlan.UNGULATE && family == SpeciesVisualFamily.EQUID) {
            // equids intentionally have no horn/antler attachments
        }
    }

    private void hideAttachments() {
        trunk.visible = false;
        earLeft.visible = false;
        earRight.visible = false;
        tuskLeft.visible = false;
        tuskRight.visible = false;
        horn.visible = false;
        horn2.visible = false;
        antlerLeft.visible = false;
        antlerRight.visible = false;
        hump.visible = false;
        shell.visible = false;
        dorsalFin.visible = false;
        snout.visible = false;
        longNeck.visible = false;
        horn.x = 0;
        horn2.x = 0;
    }

    private void resetPartScales() {
        for (ModelPart part : new ModelPart[]{
                body, head, leg0, leg1, leg2, leg3, fishBody, fishTail, finLeft, finRight,
                birdBody, birdHead, wingLeft, wingRight, reptileBody, reptileTail,
                trunk, earLeft, earRight, tuskLeft, tuskRight, horn, horn2, antlerLeft, antlerRight,
                hump, shell, dorsalFin, snout, longNeck}) {
            part.xScale = 1.0F;
            part.yScale = 1.0F;
            part.zScale = 1.0F;
            part.xRot = 0;
            part.yRot = 0;
            part.zRot = 0;
        }
        body.y = 15.0F;
        head.y = 13.0F;
        trunk.y = 14.0F;
        earLeft.y = 11.0F;
        earRight.y = 11.0F;
        tuskLeft.y = 15.0F;
        tuskRight.y = 15.0F;
        horn.y = 10.0F;
        horn2.y = 11.0F;
        antlerLeft.y = 10.0F;
        antlerRight.y = 10.0F;
        hump.y = 10.0F;
        longNeck.y = 12.0F;
        dorsalFin.y = 11.0F;
        snout.y = 19.0F;
        shell.y = 17.0F;
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
        trunk.render(p, b, light, overlay, color);
        earLeft.render(p, b, light, overlay, color);
        earRight.render(p, b, light, overlay, color);
        tuskLeft.render(p, b, light, overlay, color);
        tuskRight.render(p, b, light, overlay, color);
        horn.render(p, b, light, overlay, color);
        horn2.render(p, b, light, overlay, color);
        antlerLeft.render(p, b, light, overlay, color);
        antlerRight.render(p, b, light, overlay, color);
        hump.render(p, b, light, overlay, color);
        shell.render(p, b, light, overlay, color);
        dorsalFin.render(p, b, light, overlay, color);
        snout.render(p, b, light, overlay, color);
        longNeck.render(p, b, light, overlay, color);
    }
}
