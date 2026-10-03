package dev.livingrealms.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.LivingRealmsShipEntity;
import dev.livingrealms.sim.naval.ShipClass;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

public final class ShipRenderer extends MobRenderer<LivingRealmsShipEntity,ShipModel>{
    private static final ResourceLocation TEX=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/ship.png");
    public ShipRenderer(EntityRendererProvider.Context context){super(context,new ShipModel(context.bakeLayer(ShipModel.LAYER)),1.1F);}
    @Override protected void scale(LivingRealmsShipEntity entity,PoseStack pose,float partialTick){float s=switch(entity.shipClass()){case PATROL_BOAT->.65F;case CORVETTE->.8F;case FRIGATE->1.0F;case DESTROYER->1.15F;case CRUISER->1.35F;case CARGO_SHIP->1.3F;case LANDING_SHIP->1.2F;};pose.scale(s,s,s);}
    @Override public ResourceLocation getTextureLocation(LivingRealmsShipEntity entity){return TEX;}
}
