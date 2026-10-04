package dev.livingrealms.minecraft.client.ui;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Persistent mini HUD: nearest settlement, jurisdiction, wanted, war-front bearing. */
@EventBusSubscriber(modid = LivingRealms.MOD_ID, value = Dist.CLIENT)
public final class PresenceHudOverlay {
    private PresenceHudOverlay() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        RealmDashboardSnapshot snap = DashboardClientState.latest();
        if (snap == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int x = 8;
        int y = 8;
        var jur = snap.jurisdiction();
        String realm = jur.claimed()
                ? (jur.contested() ? jur.primaryName() + " (contested)" : jur.primaryName())
                : "Wilderness";
        String nearest = nearestSettlement(snap);
        var player = snap.player();
        String wanted = player.wantedLevel();
        String front = warFrontBearing(snap);
        g.drawString(mc.font, nearest, x, y, 0xFFE8EDF2, true);
        y += 11;
        g.drawString(mc.font, "Jurisdiction: " + realm, x, y, 0xFFB8C0CA, true);
        y += 11;
        g.drawString(mc.font, "Wanted: " + wanted, x, y, wanted.equals("NONE") ? 0xFF9EC9A9 : 0xFFE0A0A0, true);
        if (!front.isBlank()) {
            y += 11;
            g.drawString(mc.font, front, x, y, 0xFFFFC857, true);
        }
        var waypoints = ClientWaypointState.active();
        if (!waypoints.isEmpty()) {
            y += 14;
            var wp = waypoints.getFirst();
            double dx = wp.x() - mc.player.getX();
            double dz = wp.z() - mc.player.getZ();
            String bearing = bearingLabel(dx, dz);
            int dist = (int) Math.round(Math.hypot(dx, dz));
            g.drawString(mc.font, "Mark: " + wp.label() + " " + bearing + " " + dist + "m", x, y, 0xFF4DD0E1, true);
        }
    }

    private static String nearestSettlement(RealmDashboardSnapshot snap) {
        var map = snap.map();
        if (map.settlements().isEmpty()) return "Nearest: —";
        RealmDashboardSnapshot.MapSettlement best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (var s : map.settlements()) {
            double d = Math.hypot(s.x() - map.playerX(), s.z() - map.playerZ());
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        if (best == null) return "Nearest: —";
        return "Nearest: " + best.name() + " (" + Math.round(bestD) + "m)";
    }

    private static String warFrontBearing(RealmDashboardSnapshot snap) {
        var fronts = snap.map().fronts();
        if (fronts.isEmpty()) return "";
        var f = fronts.getFirst();
        double mx = (f.fromX() + f.toX()) * .5;
        double mz = (f.fromZ() + f.toZ()) * .5;
        double dx = mx - snap.map().playerX();
        double dz = mz - snap.map().playerZ();
        return "War front " + bearingLabel(dx, dz) + " (" + Math.round(Math.hypot(dx, dz)) + "m)";
    }

    private static String bearingLabel(double dx, double dz) {
        double ang = Math.toDegrees(Math.atan2(dz, dx));
        // Minecraft +Z is south; convert to compass octant.
        double compass = (ang + 90 + 360) % 360;
        String[] labels = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int idx = (int) Math.round(compass / 45.0) % 8;
        return labels[idx];
    }
}
