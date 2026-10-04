package dev.livingrealms.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.LivingRealmsAircraftEntity;
import dev.livingrealms.sim.aviation.AircraftRole;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class AircraftRenderer extends MobRenderer<LivingRealmsAircraftEntity, AircraftModel> {
    private static final ResourceLocation FIGHTER = ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID, "textures/entity/aircraft.png");
    private static final ResourceLocation PATROL = ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID, "textures/entity/aircraft_patrol.png");
    private static final ResourceLocation TRANSPORT = ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID, "textures/entity/aircraft_transport.png");
    private static final ResourceLocation BOMBER = ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID, "textures/entity/aircraft_bomber.png");

    public AircraftRenderer(EntityRendererProvider.Context c) {
        super(c, new AircraftModel(c.bakeLayer(AircraftModel.LAYER)), 0.8F);
    }

    @Override
    protected void scale(LivingRealmsAircraftEntity entity, PoseStack pose, float partialTick) {
        AircraftRole role = entity.aircraftModel().role();
        float s = switch (role) {
            case FIGHTER -> 0.85F;
            case INTERCEPTOR -> 0.95F;
            case RECON -> 0.9F;
            case ATTACK -> 1.05F;
            case BOMBER -> 1.25F;
            case TRANSPORT -> 1.35F;
        };
        pose.scale(s, s, s);
        this.shadowRadius = 0.55F * s;
    }

    @Override
    public ResourceLocation getTextureLocation(LivingRealmsAircraftEntity e) {
        return switch (e.aircraftModel().role()) {
            case TRANSPORT -> TRANSPORT;
            case RECON -> PATROL;
            case BOMBER, ATTACK -> BOMBER;
            case FIGHTER, INTERCEPTOR -> FIGHTER;
        };
    }
}
