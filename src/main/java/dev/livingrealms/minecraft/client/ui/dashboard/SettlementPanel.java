package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class SettlementPanel implements DashboardPanel {
    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {
        var player = snapshot.player();
        var realm = snapshot.realm();
        if (player.memberFactionId() <= 0 || player.memberFactionId() != realm.factionId() || snapshot.settlements().isEmpty()) return;
        int index = Math.min(snapshot.settlements().size() - 1, page);
        var settlement = snapshot.settlements().get(index);
        DashboardActionCommand.Action[] actions = {
                DashboardActionCommand.Action.SETTLEMENT_BALANCED,
                DashboardActionCommand.Action.SETTLEMENT_FOOD,
                DashboardActionCommand.Action.SETTLEMENT_HOUSING,
                DashboardActionCommand.Action.SETTLEMENT_INDUSTRY,
                DashboardActionCommand.Action.SETTLEMENT_DEFENSE
        };
        String[] labels = {"Balanced", "Food", "Housing", "Industry", "Defense"};
        int bw = Math.max(66, (layout.panelWidth - 28) / 5);
        for (int i = 0; i < actions.length; i++) {
            final DashboardActionCommand.Action action = actions[i];
            host.addWidget(Button.builder(Component.literal(labels[i]),
                            b -> DashboardActionDispatcher.dispatch(action, settlement.id()))
                    .bounds(layout.left + 10 + i * bw, layout.contentY + 118, bw - 3, 18).build());
        }
        int modeY = layout.contentY + 138;
        int mw = Math.max(54, (layout.panelWidth - 28) / 4);
        String[] modes = {"AUTO", "HYBRID", "PLAYER_LED"};
        String[] modeLabels = {"Auto", "Hybrid", "Player"};
        for (int i = 0; i < modes.length; i++) {
            final String mode = modes[i];
            host.addWidget(Button.builder(Component.literal(modeLabels[i]),
                            b -> DashboardActionDispatcher.dispatch(
                                    DashboardActionCommand.Action.SET_DEVELOPMENT_MODE, settlement.id(), mode))
                    .bounds(layout.left + 10 + i * mw, modeY, mw - 3, 16).build());
        }
        host.addWidget(Button.builder(Component.literal("Register House"),
                        b -> DashboardActionDispatcher.dispatch(
                                DashboardActionCommand.Action.REGISTER_BUILDING, settlement.id(), "HOUSE"))
                .bounds(layout.left + 10 + 3 * mw, modeY, mw - 3, 16).build());
    }

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        List<DashboardLine> lines = new ArrayList<>();
        lines.add(DashboardLine.header("How settlements work"));
        lines.add(DashboardLine.dim("Settlements grow when food, housing and order stay healthy. Policy buttons change the construction queue.", 0));
        lines.add(DashboardLine.dim("Found your own realm with the Found button (or /livingrealms found <name>) when not in a faction.", 0));
        lines.add(DashboardLine.dim("Visited frontier lands beyond the starter belt seed sparse outposts so the world never empties.", 0));
        if (snapshot.settlements().isEmpty()) {
            lines.add(DashboardLine.dim("No settlement data for the current/member realm yet — join a kingdom or found one.", 0));
            return lines;
        }
        int index = Math.min(snapshot.settlements().size() - 1, page);
        var s = snapshot.settlements().get(index);
        lines.add(DashboardLine.header("Settlement " + (index + 1) + "/" + snapshot.settlements().size()));
        lines.add(DashboardLine.text(s.name() + " [" + s.tier() + "] — pop " + s.population() + " • " + DashboardPanel.whole(s.distanceBlocks()) + "m"));
        lines.add(DashboardLine.text("Development: " + DashboardPanel.titleCase(s.developmentMode()) + " • policy " + DashboardPanel.titleCase(s.developmentPriority())));
        lines.add(DashboardLine.dim("Origin " + DashboardPanel.titleCase(s.origin()) + " • registered buildings " + s.registeredBuildings(), 1));
        lines.add(DashboardLine.dim("Canonical housing " + s.housing() + " • verified " + s.verifiedHousing()
                + " • deficit " + s.housingDeficit(), 1));
        lines.add(DashboardLine.dim("Prosperity " + DashboardPanel.pct(s.prosperity()) + " • unrest " + DashboardPanel.pct(s.unrest()), 1));
        lines.add(DashboardLine.dim("Food " + DashboardPanel.pct(s.foodSecurity()) + " • order " + DashboardPanel.pct(s.publicOrder()) + " • employment " + DashboardPanel.pct(s.employment()), 1));
        lines.add(DashboardLine.dim("Housing satisfaction " + DashboardPanel.pct(s.housingSatisfaction()) + " • goods " + DashboardPanel.pct(s.goodsAccess()), 1));
        lines.add(DashboardLine.dim("Society satisfaction " + DashboardPanel.pct(s.societySatisfaction()) + " • pressure " + DashboardPanel.titleCase(s.primaryPressure()) + " " + DashboardPanel.pct(s.pressureSeverity()), 1));
        if (s.causeSummary() != null && !s.causeSummary().isBlank()) lines.add(DashboardLine.dim("Why: " + s.causeSummary(), 1));
        if (snapshot.player().memberFactionId() == snapshot.realm().factionId()) {
            lines.add(DashboardLine.dim("Use the policy buttons below; changes affect real construction order.", 0));
        }
        return lines;
    }

    @Override
    public int maxPage(RealmDashboardSnapshot snapshot, int pageLines) {
        return Math.max(0, snapshot.settlements().size() - 1);
    }
}
