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
 * Multi-morphology wildlife model with reusable anatomical attachments.
 * One LayerDefinition — family selects which parts are visible (no per-species Java models).
 */
public final class LivingRealmsAnimalModel extends EntityModel<LivingRealmsAnimalEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID, "wildlife"), "main");

    private final ModelPart body, head, leg0, leg1, leg2, leg3;
    private final ModelPart fishBody, fishTail, finLeft, finRight, dorsalFin;
    private final ModelPart birdBody, birdHead, wingLeft, wingRight;
    private final ModelPart reptileBody, reptileTail, snout, shell;
    private final ModelPart trunk, earLeft, earRight, tuskLeft, tuskRight;
    private final ModelPart horn, horn2, antlerLeft, antlerRight, hump, longNeck;

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
        dorsalFin = root.getChild("dorsal_fin");
        birdBody = root.getChild("bird_body");
        birdHead = root.getChild("bird_head");
        wingLeft = root.getChild("wing_left");
        wingRight = root.getChild("wing_right");
        reptileBody = root.getChild("reptile_body");
        reptileTail = root.getChild("reptile_tail");
        snout = root.getChild("snout");
        shell = root.getChild("shell");
        trunk = root.getChild("trunk");
        earLeft = root.getChild("ear_left");
        earRight = root.getChild("ear_right");
        tuskLeft = root.getChild("tusk_left");
        tuskRight = root.getChild("tusk_right");
        horn = root.getChild("horn");
        horn2 = root.getChild("horn_2");
        antlerLeft = root.getChild("antler_left");
        antlerRight = root.getChild("antler_right");
        hump = root.getChild("hump");
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
        r.addOrReplaceChild("dorsal_fin", CubeListBuilder.create().texOffs(56, 24).addBox(-0.5F, -6, -2, 1, 6, 4), PartPose.offset(0, 12, -1));
        r.addOrReplaceChild("bird_body", CubeListBuilder.create().texOffs(0, 38).addBox(-2.5F, -3.0F, -4.0F, 5, 6, 8), PartPose.offset(0, 17, 0));
        r.addOrReplaceChild("bird_head", CubeListBuilder.create().texOffs(32, 38).addBox(-1.5F, -1.5F, -3.5F, 3, 3, 4)
                .texOffs(46, 38).addBox(-0.5F, 0.0F, -5.0F, 1, 1, 2), PartPose.offset(0, 14.5F, -4.0F));
        r.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(0, 58).addBox(-8, -0.5F, -2.5F, 8, 1, 5), PartPose.offset(-2.5F, 16, 0));
        r.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(0, 58).mirror().addBox(0, -0.5F, -2.5F, 8, 1, 5), PartPose.offset(2.5F, 16, 0));
        r.addOrReplaceChild("reptile_body", CubeListBuilder.create().texOffs(0, 0).addBox(-5, -3, -9, 10, 6, 18), PartPose.offset(0, 19, 0));
        r.addOrReplaceChild("reptile_tail", CubeListBuilder.create().texOffs(38, 0).addBox(-3, -2, 0, 6, 4, 15), PartPose.offset(0, 19, 8));
        r.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(48, 48).addBox(-2, -1.5F, -8, 4, 3, 8), PartPose.offset(0, 19, -8));
        r.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 48).addBox(-5, -4, -6, 10, 4, 12), PartPose.offset(0, 17, 0));
        // Mammalian attachments (parented in world space near head/body).
        r.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(56, 0).addBox(-1, 0, -1.5F, 2, 10, 3), PartPose.offset(0, 14, -11));
        r.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(32, 48).addBox(-6, -4, 0, 6, 6, 1), PartPose.offset(-3, 11, -6));
        r.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(32, 48).mirror().addBox(0, -4, 0, 6, 6, 1), PartPose.offset(3, 11, -6));
        r.addOrReplaceChild("tusk_left", CubeListBuilder.create().texOffs(60, 16).addBox(-1, 0, -1, 1, 5, 1), PartPose.offset(-2, 15, -10));
        r.addOrReplaceChild("tusk_right", CubeListBuilder.create().texOffs(60, 16).addBox(0, 0, -1, 1, 5, 1), PartPose.offset(2, 15, -10));
        r.addOrReplaceChild("horn", CubeListBuilder.create().texOffs(52, 16).addBox(-1, -5, -1, 2, 5, 2), PartPose.offset(0, 11, -9));
        r.addOrReplaceChild("horn_2", CubeListBuilder.create().texOffs(52, 16).addBox(-0.5F, -3, -0.5F, 1, 3, 1), PartPose.offset(0, 12, -7));
        r.addOrReplaceChild("antler_left", CubeListBuilder.create().texOffs(0, 64).addBox(-5, -6, -0.5F, 5, 6, 1), PartPose.offset(-1, 10, -6));
        r.addOrReplaceChild("antler_right", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(0, -6, -0.5F, 5, 6, 1), PartPose.offset(1, 10, -6));
        r.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(32, 56).addBox(-3, -4, -3, 6, 4, 6), PartPose.offset(0, 10, 0));
        r.addOrReplaceChild("long_neck", CubeListBuilder.create().texOffs(48, 56).addBox(-2, -8, -2, 4, 8, 4), PartPose.offset(0, 12, -6));
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
        hideAllAttachments();

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
                    : family == SpeciesVisualFamily.ELEPHANT ? 1.35F
                    : family == SpeciesVisualFamily.CAMELID ? 1.1F : 1.0F;
            body.xScale = bodyScaleX; body.zScale = bodyScaleZ;
            head.xScale = family == SpeciesVisualFamily.CANID ? 0.9F
                    : family == SpeciesVisualFamily.ELEPHANT ? 1.2F
                    : family == SpeciesVisualFamily.SUID ? 1.15F : 1.0F;

            if (family == SpeciesVisualFamily.ELEPHANT) {
                trunk.visible = true; earLeft.visible = true; earRight.visible = true;
                tuskLeft.visible = true; tuskRight.visible = true;
                trunk.y = bodyY + 1; earLeft.y = bodyY - 2; earRight.y = bodyY - 2;
                tuskLeft.y = bodyY + 2; tuskRight.y = bodyY + 2;
            } else if (family == SpeciesVisualFamily.RHINO) {
                horn.visible = true; horn.y = bodyY - 2;
            } else if (family == SpeciesVisualFamily.BOVID || family == SpeciesVisualFamily.HIPPO) {
                horn.visible = true; horn2.visible = true;
                horn.y = bodyY - 2; horn2.y = bodyY - 1;
                if (family == SpeciesVisualFamily.BOVID) {
                    horn.x = -2; horn2.x = 2;
                }
            } else if (family == SpeciesVisualFamily.CERVID) {
                antlerLeft.visible = true; antlerRight.visible = true;
                antlerLeft.y = bodyY - 3; antlerRight.y = bodyY - 3;
            } else if (family == SpeciesVisualFamily.CAMELID) {
                hump.visible = true; longNeck.visible = true;
                hump.y = bodyY - 5; longNeck.y = bodyY - 1;
                head.y = bodyY - 8;
            } else if (family == SpeciesVisualFamily.LAGOMORPH) {
                earLeft.visible = true; earRight.visible = true;
                earLeft.yScale = 1.4F; earRight.yScale = 1.4F;
                earLeft.y = bodyY - 4; earRight.y = bodyY - 4;
            }
        }
        if (fish) {
            boolean whale = family == SpeciesVisualFamily.CETACEAN;
            boolean seal = family == SpeciesVisualFamily.PINNIPED;
            boolean shark = family == SpeciesVisualFamily.SHARK;
            boolean ray = family == SpeciesVisualFamily.RAY;
            fishBody.xScale = whale ? 1.4F : seal ? 1.15F : shark ? 1.1F : ray ? 1.8F : 1.0F;
            fishBody.yScale = whale ? 1.3F : seal ? 0.85F : ray ? 0.45F : 1.0F;
            fishBody.zScale = whale ? 1.6F : seal ? 1.2F : shark ? 1.35F : ray ? 1.1F : 1.0F;
            fishTail.yScale = whale ? 1.4F : 1.0F;
            // Cetacean flukes are horizontal; sharks keep a dorsal fin.
            if (whale) {
                fishTail.xRot = 1.2F;
            }
            if (shark) {
                dorsalFin.visible = true;
            }
            if (ray) {
                finLeft.yScale = 0.4F; finRight.yScale = 0.4F;
                finLeft.xScale = 1.6F; finRight.xScale = 1.6F;
            }
        }
        if (bird) {
            boolean raptor = family == SpeciesVisualFamily.RAPTOR;
            boolean waterfowl = family == SpeciesVisualFamily.WATERFOWL;
            boolean ground = family == SpeciesVisualFamily.GROUND_BIRD;
            birdBody.xScale = raptor ? 1.15F : waterfowl ? 1.2F : ground ? 1.25F : 0.9F;
            birdBody.zScale = waterfowl ? 1.15F : ground ? 1.1F : 1.0F;
            wingLeft.xScale = raptor ? 1.25F : ground ? 0.7F : 1.0F;
            wingRight.xScale = raptor ? 1.25F : ground ? 0.7F : 1.0F;
        }
        if (reptile) {
            boolean snake = family == SpeciesVisualFamily.SNAKE;
            boolean turtle = family == SpeciesVisualFamily.TURTLE;
            boolean croc = family == SpeciesVisualFamily.CROCODILIAN;
            reptileBody.yScale = snake ? 0.55F : turtle ? 0.75F : 1.0F;
            reptileBody.zScale = snake ? 1.6F : turtle ? 0.85F : croc ? 1.25F : 1.0F;
            reptileTail.zScale = snake ? 1.8F : croc ? 1.35F : 1.0F;
            if (turtle) {
                shell.visible = true;
            }
            if (croc) {
                snout.visible = true;
            }
            if (snake) {
                // Collapse legs if they were wrongly visible; keep low segmented look via stretch.
                reptileBody.xScale = 0.55F;
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
            }
            float gait = family == SpeciesVisualFamily.FELID ? 0.85F
                    : family == SpeciesVisualFamily.EQUID ? 0.55F
                    : intent == AnimalIntent.FLEE ? 0.9F : 0.6662F;
            float stride = 1.4F * amount * strideMul;
            leg0.xRot = Mth.cos(swing * gait) * stride;
            leg1.xRot = Mth.cos(swing * gait + (float) Math.PI) * stride;
            leg2.xRot = leg1.xRot;
            leg3.xRot = leg0.xRot;
            if (intent == AnimalIntent.REST && amount < 0.05F) {
                leg0.xRot = leg1.xRot = leg2.xRot = leg3.xRot = 0.08F;
            }
            if (trunk.visible) {
                trunk.xRot = 0.35F + Mth.sin(age * 0.08F) * 0.12F;
            }
        }
        if (fish) {
            float swim = intent == AnimalIntent.FLEE ? 0.32F : intent == AnimalIntent.REST ? 0.06F : 0.18F;
            if (family == SpeciesVisualFamily.CETACEAN) {
                fishTail.xRot = 1.2F + Mth.sin(age * swim) * 0.25F;
            } else {
                fishTail.yRot = Mth.sin(age * swim) * (intent == AnimalIntent.FLEE ? 0.85F : 0.55F);
            }
            finLeft.zRot = -0.35F;
            finRight.zRot = 0.35F;
        }
        if (bird) {
            float flapSpeed = intent == AnimalIntent.FLEE || intent == AnimalIntent.HUNT ? 1.15F
                    : intent == AnimalIntent.REST ? 0.12F : 0.65F;
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

    private void hideAllAttachments() {
        dorsalFin.visible = false;
        snout.visible = false;
        shell.visible = false;
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
        longNeck.visible = false;
        horn.x = 0; horn2.x = 0;
    }

    private void resetPartScales() {
        for (ModelPart part : new ModelPart[]{
                body, head, leg0, leg1, leg2, leg3, fishBody, fishTail, finLeft, finRight, dorsalFin,
                birdBody, birdHead, wingLeft, wingRight, reptileBody, reptileTail, snout, shell,
                trunk, earLeft, earRight, tuskLeft, tuskRight, horn, horn2, antlerLeft, antlerRight, hump, longNeck}) {
            part.xScale = 1.0F;
            part.yScale = 1.0F;
            part.zScale = 1.0F;
            part.xRot = 0; part.yRot = 0; part.zRot = 0;
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
        dorsalFin.render(p, b, light, overlay, color);
        birdBody.render(p, b, light, overlay, color);
        birdHead.render(p, b, light, overlay, color);
        wingLeft.render(p, b, light, overlay, color);
        wingRight.render(p, b, light, overlay, color);
        reptileBody.render(p, b, light, overlay, color);
        reptileTail.render(p, b, light, overlay, color);
        snout.render(p, b, light, overlay, color);
        shell.render(p, b, light, overlay, color);
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
        longNeck.render(p, b, light, overlay, color);
    }
}
