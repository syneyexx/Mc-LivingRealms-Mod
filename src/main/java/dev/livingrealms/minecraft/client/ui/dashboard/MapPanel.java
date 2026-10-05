package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.minecraft.client.ui.ClientTerrainMapCache;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class MapPanel implements DashboardPanel {
    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {
        host.addWidget(Button.builder(Component.literal("Open world map (M)"),
                        b -> DashboardActionDispatcher.requestOpenMap())
                .bounds(layout.left + layout.panelWidth - 158, Math.max(layout.contentY, layout.footerY - 22), 148, 18).build());
    }

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        return List.of();
    }

    @Override
    public int maxPage(RealmDashboardSnapshot snapshot, int pageLines) {
        return 0;
    }

    @Override
    public void renderCustom(GuiGraphics graphics, Font font, RealmDashboardSnapshot snapshot,
                             DashboardLayout layout, int page) {
        int x = layout.left + 10;
        int y = layout.contentY;
        int w = layout.panelWidth - 20;
        int h = layout.clipHeight();
        var map = snapshot.map();
        graphics.fill(x, y, x + w, y + h, 0xFF0D1117);
        graphics.fill(x, y, x + w, y + 1, 0xFF3A4654);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF3A4654);
        graphics.fill(x, y, x + 1, y + h, 0xFF3A4654);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF3A4654);
        ClientTerrainMapCache.paintIfAvailable(graphics, snapshot, x + 1, y + 1, w - 2, h - 2);
        double spanX = Math.max(1, map.maxX() - map.minX()), spanZ = Math.max(1, map.maxZ() - map.minZ());
        for (var c : map.claims()) drawClaim(graphics, map, c, x, y, w, h, spanX, spanZ, factionColor(c.factionId(), 0x88));
        for (var r : map.routes()) {
            int color = r.operational() ? ("RAIL".equals(r.mode()) ? 0xFFD1B56A : "CARAVAN".equals(r.mode()) ? 0xFF8B7355 : 0xFF88919C) : 0xFF4A4F56;
            drawWorldLine(graphics, map, r.fromX(), r.fromZ(), r.toX(), r.toZ(), x, y, w, h, color);
        }
        for (var f : map.fronts()) drawWorldLine(graphics, map, f.fromX(), f.fromZ(), f.toX(), f.toZ(), x, y, w, h, 0xFFFF625E);
        int labeled = 0;
        for (var s : map.settlements()) {
            int sx = mapX(map, s.x(), x, w), sy = mapY(map, s.z(), y, h), color = factionColor(s.factionId(), 0xFF);
            int size = s.population() >= 10000 ? 4 : s.population() >= 2000 ? 3 : 2;
            graphics.fill(sx - size, sy - size, sx + size + 1, sy + size + 1, color);
            if (labeled < 18 && s.name() != null && !s.name().isBlank()) {
                graphics.drawString(font, s.name(), sx + size + 2, sy - 3, 0xFFE8EEF5, false);
                labeled++;
            }
        }
        for (var a : map.armies()) {
            int sx = mapX(map, a.x(), x, w), sy = mapY(map, a.z(), y, h), color = factionColor(a.factionId(), 0xFF);
            graphics.fill(sx - 2, sy - 4, sx + 3, sy - 2, color);
            graphics.fill(sx - 1, sy - 2, sx + 2, sy + 3, color);
        }
        int px = mapX(map, map.playerX(), x, w), py = mapY(map, map.playerZ(), y, h);
        graphics.fill(px - 4, py, px + 5, py + 1, 0xFFFFFFFF);
        graphics.fill(px, py - 4, px + 1, py + 5, 0xFFFFFFFF);
        graphics.drawString(font, "Geographic map · settlements labeled · press M for full terrain", x + 4, y + 4, 0xFFB9C2CC, false);
        graphics.drawString(font, "You @ X " + DashboardPanel.whole(map.playerX()) + "  Z " + DashboardPanel.whole(map.playerZ())
                        + "  ·  founding needs "
                        + (int) Math.round(dev.livingrealms.sim.player.PlayerSettlementFounder.MIN_SETTLEMENT_SPACING) + "m clearance",
                x + 4, y + h - 11, 0xFFB9C2CC, false);
    }

    private static int mapX(RealmDashboardSnapshot.StrategicMapView map, double worldX, int x, int w) {
        return x + (int) Math.round((worldX - map.minX()) / Math.max(1, map.maxX() - map.minX()) * (w - 1));
    }

    private static int mapY(RealmDashboardSnapshot.StrategicMapView map, double worldZ, int y, int h) {
        return y + (int) Math.round((worldZ - map.minZ()) / Math.max(1, map.maxZ() - map.minZ()) * (h - 1));
    }

    private static void drawWorldLine(GuiGraphics g, RealmDashboardSnapshot.StrategicMapView map,
                                      double ax, double az, double bx, double bz, int x, int y, int w, int h, int color) {
        drawLine(g, mapX(map, ax, x, w), mapY(map, az, y, h), mapX(map, bx, x, w), mapY(map, bz, y, h), color);
    }

    private static void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1, dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1, err = dx + dy;
        for (;;) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private static void drawClaim(GuiGraphics g, RealmDashboardSnapshot.StrategicMapView map,
                                  RealmDashboardSnapshot.MapClaim c, int x, int y, int w, int h,
                                  double spanX, double spanZ, int color) {
        int cx = mapX(map, c.x(), x, w), cy = mapY(map, c.z(), y, h);
        double rx = c.radius() / spanX * (w - 1), ry = c.radius() / spanZ * (h - 1);
        int px = 0, py = 0;
        for (int i = 0; i <= 24; i++) {
            double a = Math.PI * 2 * i / 24.0;
            int nx = cx + (int) Math.round(Math.cos(a) * rx), ny = cy + (int) Math.round(Math.sin(a) * ry);
            if (i > 0) drawLine(g, px, py, nx, ny, color);
            px = nx;
            py = ny;
        }
    }

    private static int factionColor(long id, int alpha) {
        int[] colors = {0xD95C5C, 0x5C8DD9, 0x65B96E, 0xC69A4B, 0x9A6DD1, 0x4CB6B0, 0xD47AA5, 0xA0A85A, 0xD9784A, 0x6C7FD1, 0x6FB09B, 0xB27A52};
        int rgb = colors[Math.floorMod(Long.hashCode(id), colors.length)];
        return (alpha << 24) | rgb;
    }
}
