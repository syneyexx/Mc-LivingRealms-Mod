package dev.livingrealms;

import dev.livingrealms.api.LivingRealmsApi;
import dev.livingrealms.minecraft.LivingRealmsEvents;
import dev.livingrealms.minecraft.compat.ModCompatibilityRuntime;
import dev.livingrealms.minecraft.compat.create.CreateIndustryAdapter;
import dev.livingrealms.minecraft.compat.npc.CivilianNpcAdapter;
import dev.livingrealms.minecraft.entity.ModEntities;
import dev.livingrealms.minecraft.network.LivingRealmsNetwork;
import dev.livingrealms.sim.compat.adapter.DefaultApiProviders;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(LivingRealms.MOD_ID)
public final class LivingRealms {
    public static final String MOD_ID="livingrealms";
    public static final Logger LOGGER= LoggerFactory.getLogger(MOD_ID);
    public LivingRealms(IEventBus modBus){
        LivingRealmsApi.installDefaults(
                DefaultApiProviders.species(),
                DefaultApiProviders.cultures(),
                DefaultApiProviders.structures(),
                DefaultApiProviders.tradeGoods());
        LivingRealmsApi.installNpcAdapter(CivilianNpcAdapter.INSTANCE);
        LivingRealmsApi.installIndustryAdapter(CreateIndustryAdapter.INSTANCE);
        LivingRealmsApi.installMagicAdapter(id -> id != null && (
                id.contains("spell") || id.contains("staff") || id.contains("wand") || id.contains("tome")));
        ModEntities.register(modBus);
        LivingRealmsNetwork.register(modBus);
        NeoForge.EVENT_BUS.register(new LivingRealmsEvents());
        ModCompatibilityRuntime.logDetectedPack();
        LOGGER.info("Living Realms initialized");
    }
}
