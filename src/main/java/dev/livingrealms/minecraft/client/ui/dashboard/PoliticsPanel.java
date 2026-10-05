package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class PoliticsPanel implements DashboardPanel {
    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {
        if (snapshot.player().memberFactionId() <= 0 || snapshot.wars().isEmpty() && snapshot.factions().size() < 2) return;
        int y = layout.contentY + 4;
        int shown = 0;
        long selfId = snapshot.player().memberFactionId();
        for (var war : snapshot.wars()) {
            if (shown >= 3) break;
            if (war.attackerFactionId() != selfId && war.defenderFactionId() != selfId) continue;
            long enemy = war.attackerFactionId() == selfId ? war.defenderFactionId() : war.attackerFactionId();
            if (enemy <= 0) continue;
            String name = snapshot.factions().stream().filter(f -> f.id() == enemy)
                    .map(RealmDashboardSnapshot.FactionSummary::name).findFirst().orElse("Enemy");
            host.addWidget(Button.builder(Component.literal("Peace vs " + name),
                            b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.PETITION_PEACE, enemy))
                    .bounds(layout.left + 10, y, Math.min(180, layout.panelWidth - 20), 16).build());
            y += 18;
            shown++;
        }
        for (var f : snapshot.factions()) {
            if (f.id() == selfId || f.localRealm()) continue;
            host.addWidget(Button.builder(Component.literal("Trade pact: " + f.name()),
                            b -> DashboardActionDispatcher.dispatch(DashboardActionCommand.Action.PROPOSE_TRADE_PACT, f.id()))
                    .bounds(layout.left + 10, y, Math.min(180, layout.panelWidth - 20), 16).build());
            break;
        }
    }

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        List<DashboardLine> lines = new ArrayList<>();
        var realm = snapshot.realm();
        var politics = snapshot.politics();
        if (realm.factionId() <= 0) {
            lines.add(DashboardLine.dim("No realm selected. Stand in claimed territory or join a faction.", 0));
            return lines;
        }
        lines.add(DashboardLine.header("Government"));
        lines.add(DashboardLine.text(realm.name() + " — " + realm.governmentType() + " / " + realm.successionLaw()));
        lines.add(DashboardLine.text("Ruler: " + realm.ruler()));
        lines.add(DashboardLine.dim("Stability " + DashboardPanel.pct(realm.stability()) + " • legitimacy " + DashboardPanel.pct(realm.legitimacy()) + " • corruption " + DashboardPanel.pct(realm.corruption()), 1));
        lines.add(DashboardLine.dim("Tax rate " + DashboardPanel.pct(realm.taxRate()) + " • active treaties " + realm.activeTreaties(), 1));
        lines.add(DashboardLine.header("Foreign relations"));
        for (var relation : politics.relations()) {
            int color = switch (relation.status()) {
                case "WAR", "HOSTILE" -> 0xFFFF7B72;
                case "RIVAL" -> 0xFFFFB56B;
                case "FRIENDLY", "ALLIED" -> 0xFF7EE787;
                default -> 0xFFE0E0E0;
            };
            String trade = relation.tradeAgreement() ? " • trade" : "";
            lines.add(new DashboardLine(relation.factionName() + " — " + relation.status() + " • opinion " + DashboardPanel.whole(relation.opinion()) + trade, color, 0));
        }
        if (politics.relations().isEmpty()) lines.add(DashboardLine.dim("No foreign relations recorded.", 0));
        lines.add(DashboardLine.header("Active treaties"));
        for (var treaty : politics.treaties()) {
            lines.add(DashboardLine.text("#" + treaty.id() + " " + DashboardPanel.titleCase(treaty.type()) + " — " + treaty.otherFactionName()));
            lines.add(DashboardLine.dim("Day " + treaty.startDay() + " → " + treaty.endDay(), 1));
        }
        if (politics.treaties().isEmpty()) lines.add(DashboardLine.dim("No active treaties.", 0));
        return lines;
    }
}
