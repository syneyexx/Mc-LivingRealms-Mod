package dev.livingrealms.minecraft.client;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.TradeCaravanEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class TradeCaravanRenderer extends MobRenderer<TradeCaravanEntity,TradeCaravanModel>{
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/trade_caravan.png");
    public TradeCaravanRenderer(EntityRendererProvider.Context context){super(context,new TradeCaravanModel(context.bakeLayer(TradeCaravanModel.LAYER)),.65F);}
    @Override public ResourceLocation getTextureLocation(TradeCaravanEntity entity){return TEXTURE;}
}
