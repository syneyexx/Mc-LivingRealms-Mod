package dev.livingrealms.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.LivingRealmsAircraftEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class AircraftRenderer extends MobRenderer<LivingRealmsAircraftEntity,AircraftModel>{
    private static final ResourceLocation BASE=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/aircraft.png");
    private static final ResourceLocation PATROL=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/aircraft_patrol.png");
    private static final ResourceLocation TRANSPORT=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/aircraft_transport.png");
    public AircraftRenderer(EntityRendererProvider.Context c){super(c,new AircraftModel(c.bakeLayer(AircraftModel.LAYER)),.8F);}
    @Override protected void scale(LivingRealmsAircraftEntity entity,PoseStack pose,float partialTick){
        float s=0.85F+Math.floorMod((int)entity.wingId(),5)*0.05F;pose.scale(s,s,s);
    }
    @Override public ResourceLocation getTextureLocation(LivingRealmsAircraftEntity e){
        int salt=Math.floorMod((int)e.wingId(),3);
        return salt==0?PATROL:salt==1?TRANSPORT:BASE;
    }
}
