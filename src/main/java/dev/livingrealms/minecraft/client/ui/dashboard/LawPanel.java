package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class LawPanel implements DashboardPanel {
    public static final int BOUNTIES_PER_PAGE = 4;

    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {
        var player = snapshot.player();
        var jurisdiction = snapshot.jurisdiction();
        if (jurisdiction.claimed() && !jurisdiction.contested() && jurisdiction.primaryFactionId() > 0
                && (!"NONE".equalsIgnoreCase(player.wantedLevel()) || player.bounty() > 0 || player.inCustody())) {
            long fid = jurisdiction.primaryFactionId();
            host.addWidget(Button.builder(Component.literal("Surrender"),
                            b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.SURRENDER, fid))
                    .bounds(layout.left + 10, layout.contentY - 2, 78, 16).build());
            host.addWidget(Button.builder(Component.literal("Pay fine"),
                            b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.PAY_FINE, fid, "25"))
                    .bounds(layout.left + 92, layout.contentY - 2, 78, 16).build());
        }
        int start = Math.min(snapshot.bounties().size(), page * BOUNTIES_PER_PAGE);
        int end = Math.min(snapshot.bounties().size(), start + BOUNTIES_PER_PAGE);
        for (int i = start; i < end; i++) {
            var bounty = snapshot.bounties().get(i);
            int row = i - start;
            String label = bounty.assignedToYou() ? "Abandon" : "Accept";
            host.addWidget(Button.builder(Component.literal(label), b -> {
                var action = bounty.assignedToYou()
                        ? DashboardActionCommand.Action.BOUNTY_ABANDON
                        : DashboardActionCommand.Action.BOUNTY_ACCEPT;
                DashboardActionDispatcher.dispatch(action, bounty.id());
            }).bounds(layout.left + layout.panelWidth - 84, layout.contentY + 7 + row * 33, 66, 18).build());
        }
    }

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        return List.of();
    }

    @Override
    public void renderCustom(GuiGraphics graphics, Font font, RealmDashboardSnapshot snapshot,
                             DashboardLayout layout, int page) {
        int x = layout.left + 11;
        int y = layout.contentY + 2;
        int w = layout.panelWidth - 22;
        var p = snapshot.player();
        var j = snapshot.jurisdiction();
        graphics.drawString(font, j.claimed() ? "Law office — " + j.primaryName() : "No local jurisdiction", x, y, 0xFFF2E8C9, false);
        y += 14;
        graphics.drawString(font, "Wanted: " + p.wantedLevel() + "  bounty " + DashboardPanel.whole(p.bounty())
                + "  infamy " + DashboardPanel.whole(p.globalInfamy()), x, y, 0xFFE0E0E0, false);
        y += 17;
        if (j.contested()) {
            graphics.drawString(font, "Board unavailable in contested territory.", x, y, 0xFFFFB56B, false);
            return;
        }
        if (snapshot.bounties().isEmpty()) {
            graphics.drawString(font, "No open or assigned bounties at this board.", x, y, 0xFFA5A5A5, false);
            return;
        }
        int start = Math.min(snapshot.bounties().size(), page * BOUNTIES_PER_PAGE);
        int end = Math.min(snapshot.bounties().size(), start + BOUNTIES_PER_PAGE);
        for (int i = start; i < end; i++) {
            var b = snapshot.bounties().get(i);
            int rowY = y + (i - start) * 33;
            graphics.drawString(font, "#" + b.id() + "  " + compactTarget(b.targetKey()), x, rowY,
                    b.assignedToYou() ? 0xFFFFD47A : 0xFFE0E0E0, false);
            graphics.drawString(font, "Reward " + DashboardPanel.whole(b.reward()) + " • " + b.status(), x + 8, rowY + 11, 0xFFA5A5A5, false);
        }
        String pageText = (page + 1) + "/" + (maxPage(snapshot, 1) + 1);
        graphics.drawString(font, pageText, x + w - 24, y + BOUNTIES_PER_PAGE * 33, 0xFF888888, false);
    }

    @Override
    public int maxPage(RealmDashboardSnapshot snapshot, int pageLines) {
        return Math.max(0, (snapshot.bounties().size() - 1) / BOUNTIES_PER_PAGE);
    }

    private static String compactTarget(String key) {
        if (key == null || key.isBlank()) return "Unknown target";
        int colon = key.indexOf(':');
        String value = colon >= 0 ? key.substring(colon + 1) : key;
        return value.length() > 18 ? value.substring(0, 18) + "…" : value;
    }
}
