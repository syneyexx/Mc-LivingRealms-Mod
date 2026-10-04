package dev.livingrealms.minecraft.client;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.MilitaryUnitEntity;
import dev.livingrealms.sim.military.MilitaryUnitClass;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class MilitaryUnitRenderer extends MobRenderer<MilitaryUnitEntity,MilitaryUnitModel>{
    private static final ResourceLocation INFANTRY=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/military_unit.png");
    private static final ResourceLocation OFFICER=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/military_officer.png");
    private static final ResourceLocation GUARD=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/military_guard.png");
    private static final ResourceLocation ELITE=ResourceLocation.fromNamespaceAndPath(LivingRealms.MOD_ID,"textures/entity/military_elite.png");

    public MilitaryUnitRenderer(EntityRendererProvider.Context c){super(c,new MilitaryUnitModel(c.bakeLayer(MilitaryUnitModel.LAYER)),.55F);}

    @Override public ResourceLocation getTextureLocation(MilitaryUnitEntity e){
        MilitaryUnitClass cls=e.unitClass();
        if(cls==MilitaryUnitClass.CAVALRY) return ELITE;
        if(cls==MilitaryUnitClass.ARTILLERY||cls==MilitaryUnitClass.ARMOR) return OFFICER;
        // Slot parity distinguishes line infantry from local guards without a parallel authority.
        return (e.projectionSlot()&1)==0?INFANTRY:GUARD;
    }
}
