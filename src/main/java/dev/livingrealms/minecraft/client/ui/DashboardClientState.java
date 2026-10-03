package dev.livingrealms.minecraft.client.ui;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.network.DashboardActionPayload;
import dev.livingrealms.minecraft.network.DashboardRequestPayload;
import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.RealmDashboardCodec;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Physical-client cache for the latest immutable strategic snapshot. */
public final class DashboardClientState {
    private static RealmDashboardSnapshot latest;
    private enum OpenTarget { NONE, DASHBOARD, WORLD_MAP }
    private static OpenTarget awaitingOpen = OpenTarget.NONE;

    private DashboardClientState() {}

    public static void requestOpen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) return;
        awaitingOpen = OpenTarget.DASHBOARD;
        PacketDistributor.sendToServer(DashboardRequestPayload.INSTANCE);
    }

    public static void requestOpenMap() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) return;
        awaitingOpen = OpenTarget.WORLD_MAP;
        PacketDistributor.sendToServer(DashboardRequestPayload.INSTANCE);
    }

    public static void requestRefresh() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) return;
        PacketDistributor.sendToServer(DashboardRequestPayload.INSTANCE);
    }

    public static void receive(String json) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            RealmDashboardSnapshot decoded = RealmDashboardCodec.decode(json);
            latest = decoded;
            if (minecraft.screen instanceof RealmDashboardScreen dashboard) {
                dashboard.replaceSnapshot(decoded);
            } else if (minecraft.screen instanceof RealmWorldMapScreen worldMap) {
                worldMap.replaceSnapshot(decoded);
            } else if (awaitingOpen == OpenTarget.DASHBOARD) {
                minecraft.setScreen(new RealmDashboardScreen(decoded));
            } else if (awaitingOpen == OpenTarget.WORLD_MAP) {
                minecraft.setScreen(new RealmWorldMapScreen(decoded));
            }
        } catch (RuntimeException ex) {
            LivingRealms.LOGGER.error("Could not decode Living Realms dashboard snapshot", ex);
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("message.livingrealms.dashboard_error"), false);
            }
        } finally {
            awaitingOpen = OpenTarget.NONE;
        }
    }


    public static void sendAction(DashboardActionCommand command) {
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.player==null||minecraft.getConnection()==null||command==null)return;
        PacketDistributor.sendToServer(new DashboardActionPayload(command.encode()));
    }

    public static RealmDashboardSnapshot latest() {
        return latest;
    }

    public static void clear() {
        latest = null;
        awaitingOpen = OpenTarget.NONE;
    }
}
