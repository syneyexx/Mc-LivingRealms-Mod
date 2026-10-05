package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Wave 13: underworld contract board, bribery, and black-market fence.
 * Completion still requires a real {@code CrimeIncident} on the server — never fabricated here.
 */
public final class UnderworldPanel implements DashboardPanel {
    private static final int MAX_ACCEPT_BUTTONS = 4;
    private static final int MAX_FENCE_BUTTONS = 3;

    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {
        var u = snapshot.underworld();
        int y = layout.contentY + 4;
        long bribeFaction = bribeJurisdictionId(snapshot);
        if (bribeFaction > 0) {
            host.addWidget(Button.builder(Component.literal("Bribe officials"),
                            b -> DashboardActionDispatcher.dispatch(
                                    DashboardActionCommand.Action.UNDERWORLD_BRIBE, bribeFaction, "55"))
                    .bounds(layout.left + 10, y, Math.min(120, layout.panelWidth - 20), 16).build());
            y += 18;
        }
        int acceptShown = 0;
        for (var c : u.contracts()) {
            if (acceptShown >= MAX_ACCEPT_BUTTONS) break;
            if (!"AVAILABLE".equalsIgnoreCase(c.status())) continue;
            long contractId = c.id();
            String label = "Accept #" + contractId + " " + shortType(c.type());
            host.addWidget(Button.builder(Component.literal(label),
                            b -> DashboardActionDispatcher.dispatch(
                                    DashboardActionCommand.Action.UNDERWORLD_ACCEPT, contractId))
                    .bounds(layout.left + 10, y, Math.min(200, layout.panelWidth - 20), 16).build());
            y += 18;
            acceptShown++;
        }
        if (u.blackMarketEligible()) {
            int fenceShown = 0;
            for (var lot : u.stolenLots()) {
                if (fenceShown >= MAX_FENCE_BUTTONS) break;
                if (y > layout.contentY + 130) break;
                long lotId = lot.id();
                String label = "Fence #" + lotId + " " + DashboardPanel.titleCase(lot.goodKey());
                host.addWidget(Button.builder(Component.literal(label),
                                b -> DashboardActionDispatcher.dispatch(
                                        DashboardActionCommand.Action.BLACK_MARKET_SELL, lotId))
                        .bounds(layout.left + 10, y, Math.min(220, layout.panelWidth - 20), 16).build());
                y += 18;
                fenceShown++;
            }
        }
    }

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        List<DashboardLine> lines = new ArrayList<>();
        var u = snapshot.underworld();
        lines.add(DashboardLine.header("Underworld"));
        lines.add(DashboardLine.text("Street cred " + DashboardPanel.one(u.streetCred())
                + " • bribery " + DashboardPanel.one(u.briberySkill())
                + " • contracts done " + u.contractsCompleted()));
        lines.add(DashboardLine.dim(u.blackMarketEligible()
                ? "Black market: eligible — fence stolen lots below"
                : "Black market: locked (raise street cred)", 0));
        lines.add(DashboardLine.dim("Jobs complete only via real crimes; the board never fabricates incidents.", 0));
        lines.add(DashboardLine.header("Contract board"));
        for (var c : u.contracts()) {
            String you = c.acceptedByYou() ? " (yours)" : "";
            lines.add(DashboardLine.text("#" + c.id() + " " + DashboardPanel.titleCase(c.type())
                    + " • " + c.status() + you));
            lines.add(DashboardLine.dim(c.jurisdiction() + " → " + c.target()
                    + " • reward " + DashboardPanel.whole(c.reward()) + " • " + c.daysLeft() + "d left", 1));
            if (c.acceptedByYou() && "ACCEPTED".equalsIgnoreCase(c.status())) {
                lines.add(DashboardLine.dim("Objective: commit matching "
                        + DashboardPanel.titleCase(c.type()) + " against " + c.target()
                        + " in " + c.jurisdiction(), 1));
            }
        }
        if (u.contracts().isEmpty()) {
            lines.add(DashboardLine.dim("No open underworld contracts. Corrupt taverns tip when jobs appear.", 0));
        }
        if (!u.stolenLots().isEmpty()) {
            lines.add(DashboardLine.header("Stolen goods ledger"));
            for (var lot : u.stolenLots()) {
                lines.add(DashboardLine.text("#" + lot.id() + " " + DashboardPanel.titleCase(lot.goodKey())
                        + " • value " + DashboardPanel.whole(lot.value())));
            }
        }
        return lines;
    }

    private static long bribeJurisdictionId(RealmDashboardSnapshot snapshot) {
        var j = snapshot.jurisdiction();
        if (j.claimed() && j.primaryFactionId() > 0) return j.primaryFactionId();
        if (snapshot.realm().factionId() > 0) return snapshot.realm().factionId();
        return snapshot.player().memberFactionId();
    }

    private static String shortType(String type) {
        if (type == null || type.isBlank()) return "Job";
        String t = type.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
