package dev.livingrealms.minecraft.client.ui;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Kingdom-style live strip: realm status stays on-screen while you walk the simulation.
 * Data comes from the cached dashboard snapshot; the world itself remains the live view.
 */
@EventBusSubscriber(modid = LivingRealms.MOD_ID, value = Dist.CLIENT)
public final class LivingRealmsLiveHud {
    private static int refreshTicks;

    private LivingRealmsLiveHud() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || mc.screen != null) return;
        if (++refreshTicks >= 80) {
            refreshTicks = 0;
            DashboardClientState.requestRefresh();
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        RealmDashboardSnapshot snap = DashboardClientState.latest();
        if (snap == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int x = 8;
        int y = 8;
        var player = snap.player();
        var realm = snap.realm();
        var jurisdiction = snap.jurisdiction();

        String title;
        String line2;
        String line3;
        if (player.memberFactionId() > 0 && realm.factionId() > 0) {
            title = realm.name();
            line2 = "Day " + snap.day() + "  ·  " + whole(realm.treasury()) + " coin  ·  "
                    + realm.population() + " people  ·  " + realm.armyPersonnel() + " troops";
            line3 = player.rank() + "  ·  stab " + pct(realm.stability()) + "  ·  wars " + realm.activeWars()
                    + "  ·  F12 dashboard · M map";
        } else if (jurisdiction.claimed()) {
            title = jurisdiction.contested()
                    ? "Contested: " + jurisdiction.primaryName()
                    : jurisdiction.primaryName();
            line2 = "Day " + snap.day() + "  ·  walk the living world — citizens, roads and markets update live";
            line3 = "Join here or Found settlement (F12 Overview) · need "
                    + (int) Math.round(dev.livingrealms.sim.player.PlayerSettlementFounder.MIN_SETTLEMENT_SPACING)
                    + "m clearance · M map";
        } else {
            title = "Wilderness";
            line2 = "Day " + snap.day() + "  ·  open land — found a realm when "
                    + (int) Math.round(dev.livingrealms.sim.player.PlayerSettlementFounder.MIN_SETTLEMENT_SPACING)
                    + "m from every town";
            line3 = "F12 Overview → Found settlement here  ·  /livingrealms found <name>  ·  M map";
        }

        int w = Math.min(mc.getWindow().getGuiScaledWidth() - 16, Math.max(220, mc.font.width(line2) + 16));
        g.fill(x - 2, y - 2, x + w, y + 34, 0xAA101418);
        g.fill(x - 2, y - 2, x + w, y - 1, 0xFFB59A5A);
        g.drawString(mc.font, title, x, y, 0xFFF2E8C9, false);
        g.drawString(mc.font, trim(line2, w - 8), x, y + 11, 0xFFD0D6DE, false);
        g.drawString(mc.font, trim(line3, w - 8), x, y + 22, 0xFF9AA3AD, false);
    }

    private static String whole(double v) { return Long.toString(Math.round(v)); }
    private static String pct(double v) { return Math.round(v * 100.0) + "%"; }
    private static String trim(String s, int maxPx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font.width(s) <= maxPx) return s;
        String cut = s;
        while (cut.length() > 4 && mc.font.width(cut + "…") > maxPx) cut = cut.substring(0, cut.length() - 1);
        return cut + "…";
    }
}
