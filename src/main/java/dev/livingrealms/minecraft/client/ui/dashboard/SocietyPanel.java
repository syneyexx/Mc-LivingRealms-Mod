package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.ArrayList;
import java.util.List;

public final class SocietyPanel implements DashboardPanel {
    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {}

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        List<DashboardLine> lines = new ArrayList<>();
        if (snapshot.settlements().isEmpty()) {
            lines.add(DashboardLine.dim("No society data for the current/member realm.", 0));
            return lines;
        }
        double satisfaction = snapshot.settlements().stream().mapToDouble(RealmDashboardSnapshot.SettlementView::societySatisfaction).average().orElse(0);
        double unrest = snapshot.settlements().stream().mapToDouble(RealmDashboardSnapshot.SettlementView::unrest).average().orElse(0);
        lines.add(DashboardLine.header("Civilian conditions"));
        lines.add(DashboardLine.text("Realm satisfaction " + DashboardPanel.pct(satisfaction) + " • average unrest " + DashboardPanel.pct(unrest)));
        for (var s : snapshot.settlements()) {
            String state = s.societySatisfaction() >= .82 && s.unrest() < .25 ? "STABLE"
                    : s.societySatisfaction() >= .60 && s.unrest() < .55 ? "STRAINED" : "CRITICAL";
            lines.add(state.equals("CRITICAL") ? DashboardLine.warn(s.name() + " — " + state) : DashboardLine.text(s.name() + " — " + state));
            lines.add(DashboardLine.dim("Satisfaction " + DashboardPanel.pct(s.societySatisfaction()) + " • prosperity " + DashboardPanel.pct(s.prosperity()) + " • unrest " + DashboardPanel.pct(s.unrest()), 1));
            lines.add(DashboardLine.dim("Food " + DashboardPanel.pct(s.foodSecurity()) + " • housing " + DashboardPanel.pct(s.housingSatisfaction()) + " • safety " + DashboardPanel.pct(s.publicOrder()), 1));
            lines.add(DashboardLine.dim("Employment " + DashboardPanel.pct(s.employment()) + " • goods " + DashboardPanel.pct(s.goodsAccess()), 1));
            if (!s.primaryPressure().equals("BALANCED")) {
                lines.add(DashboardLine.warn("  Main pressure: " + DashboardPanel.titleCase(s.primaryPressure()) + " (" + DashboardPanel.pct(s.pressureSeverity()) + " deficit)"));
                if (s.causeSummary() != null && !s.causeSummary().isBlank()) {
                    lines.add(DashboardLine.dim("  Why: " + s.causeSummary(), 1));
                }
            } else {
                lines.add(DashboardLine.dim("Needs are broadly balanced.", 1));
                if (s.causeSummary() != null && !s.causeSummary().isBlank()) {
                    lines.add(DashboardLine.dim("  " + s.causeSummary(), 1));
                }
            }
        }
        return lines;
    }
}
