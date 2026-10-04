package dev.livingrealms.minecraft.client.ui;

import dev.livingrealms.LivingRealms;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = LivingRealms.MOD_ID, value = Dist.CLIENT)
public final class LivingRealmsClientGameEvents {
    private LivingRealmsClientGameEvents() {}

    private static int presenceRefreshTicks;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (LivingRealmsKeyMappings.OPEN_DASHBOARD.get().consumeClick()) {
            if (minecraft.screen == null) DashboardClientState.requestOpen();
        }
        while (LivingRealmsKeyMappings.OPEN_WORLD_MAP.get().consumeClick()) {
            if (minecraft.screen == null) DashboardClientState.requestOpenMap();
        }
        while (LivingRealmsKeyMappings.OPEN_CREATIVE_CATALOG.get().consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                if (minecraft.player.getAbilities().instabuild) minecraft.setScreen(new CreativeItemCatalogScreen());
                else minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Living Realms creative catalog requires Creative mode."), false);
            }
        }
        // Soft-refresh presence HUD snapshot while in-world (does not force UI open).
        if (minecraft.player != null && minecraft.screen == null && minecraft.getConnection() != null) {
            if (++presenceRefreshTicks >= 200) {
                presenceRefreshTicks = 0;
                DashboardClientState.requestRefresh();
            }
        } else {
            presenceRefreshTicks = 0;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DashboardClientState.clear();
        NpcDialogueClientState.clear();
        ClientWaypointState.clear();
        presenceRefreshTicks = 0;
    }
}
