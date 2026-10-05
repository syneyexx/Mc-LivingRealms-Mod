package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.ArrayList;
import java.util.List;

/**
 * Underworld panel reserved for when a dashboard tab exists.
 * Currently unused by the compositor (no UNDERWORLD tab).
 */
public final class UnderworldPanel implements DashboardPanel {
    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {}

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        List<DashboardLine> lines = new ArrayList<>();
        var u = snapshot.underworld();
        lines.add(DashboardLine.header("Underworld"));
        lines.add(DashboardLine.text("Street cred " + DashboardPanel.one(u.streetCred())
                + " • bribery " + DashboardPanel.one(u.briberySkill())
                + " • contracts done " + u.contractsCompleted()));
        lines.add(DashboardLine.dim(u.blackMarketEligible() ? "Black market: eligible" : "Black market: locked", 0));
        for (var c : u.contracts()) {
            lines.add(DashboardLine.text("#" + c.id() + " " + DashboardPanel.titleCase(c.type())
                    + " • " + c.status() + (c.acceptedByYou() ? " (yours)" : "")));
            lines.add(DashboardLine.dim(c.jurisdiction() + " → " + c.target()
                    + " • reward " + DashboardPanel.whole(c.reward()) + " • " + c.daysLeft() + "d", 1));
        }
        if (u.contracts().isEmpty()) lines.add(DashboardLine.dim("No open underworld contracts.", 0));
        return lines;
    }
}
