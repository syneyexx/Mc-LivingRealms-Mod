package dev.livingrealms.minecraft.client;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.entity.ModEntities;
import dev.livingrealms.minecraft.client.ui.DashboardClientState;
import dev.livingrealms.minecraft.client.ui.LivingRealmsKeyMappings;
import dev.livingrealms.minecraft.client.ui.NpcDialogueClientState;
import dev.livingrealms.minecraft.network.DialogueClientBridge;
import dev.livingrealms.minecraft.network.DashboardClientBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = LivingRealms.MOD_ID, value = Dist.CLIENT)
public final class LivingRealmsClientEvents {
    private LivingRealmsClientEvents() {}


    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        DashboardClientBridge.install(DashboardClientState::receive);
        DialogueClientBridge.install(NpcDialogueClientState::receive);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(LivingRealmsKeyMappings.OPEN_DASHBOARD.get());
        event.register(LivingRealmsKeyMappings.OPEN_WORLD_MAP.get());
        event.register(LivingRealmsKeyMappings.OPEN_CREATIVE_CATALOG.get());
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LivingRealmsAnimalModel.LAYER, LivingRealmsAnimalModel::createBodyLayer);
        event.registerLayerDefinition(TradeCaravanModel.LAYER, TradeCaravanModel::createBodyLayer);
        event.registerLayerDefinition(FactionCitizenModel.LAYER, FactionCitizenModel::createBodyLayer);
        event.registerLayerDefinition(MilitaryUnitModel.LAYER, MilitaryUnitModel::createBodyLayer);
        event.registerLayerDefinition(MobileCivilizationModel.LAYER, MobileCivilizationModel::createBodyLayer);
        event.registerLayerDefinition(AircraftModel.LAYER, AircraftModel::createBodyLayer);
        event.registerLayerDefinition(ShipModel.LAYER, ShipModel::createBodyLayer);
        event.registerLayerDefinition(BountyHunterModel.LAYER, BountyHunterModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WILDLIFE.get(), LivingRealmsAnimalRenderer::new);
        event.registerEntityRenderer(ModEntities.CARAVAN.get(), TradeCaravanRenderer::new);
        event.registerEntityRenderer(ModEntities.CITIZEN.get(), FactionCitizenRenderer::new);
        event.registerEntityRenderer(ModEntities.MILITARY_UNIT.get(), MilitaryUnitRenderer::new);
        event.registerEntityRenderer(ModEntities.MOBILE_CIVILIZATION.get(), MobileCivilizationRenderer::new);
        event.registerEntityRenderer(ModEntities.AIRCRAFT.get(), AircraftRenderer::new);
        event.registerEntityRenderer(ModEntities.SHIP.get(), ShipRenderer::new);
        event.registerEntityRenderer(ModEntities.BOUNTY_HUNTER.get(), BountyHunterRenderer::new);
    }
}
