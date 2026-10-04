package dev.livingrealms.minecraft.client;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.SiegeEquipmentEntity;
import dev.livingrealms.sim.military.SiegeEquipmentKind;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class SiegeEquipmentRenderer extends MobRenderer<SiegeEquipmentEntity,SiegeEquipmentModel> {
    private static final ResourceLocation RAM=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/siege_ram.png");
    private static final ResourceLocation LADDER=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/siege_ladder.png");
    private static final ResourceLocation ARTILLERY=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/siege_artillery.png");

    public SiegeEquipmentRenderer(EntityRendererProvider.Context c){
        super(c,new SiegeEquipmentModel(c.bakeLayer(SiegeEquipmentModel.LAYER)),.8F);
    }

    @Override public ResourceLocation getTextureLocation(SiegeEquipmentEntity e){
        return switch(e.kind()){
            case RAM -> RAM;
            case LADDER -> LADDER;
            case ARTILLERY -> ARTILLERY;
        };
    }
}
