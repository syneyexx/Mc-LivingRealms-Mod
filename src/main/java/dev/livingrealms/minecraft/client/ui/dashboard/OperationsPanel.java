package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.ArrayList;
import java.util.List;

public final class OperationsPanel implements DashboardPanel {
    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {}

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        List<DashboardLine> lines = new ArrayList<>();
        var ops = snapshot.operations();
        lines.add(DashboardLine.header("Trade shipments"));
        for (var s : ops.shipments()) {
            lines.add(DashboardLine.text("#" + s.id() + " " + DashboardPanel.titleCase(s.resource()) + " "
                    + DashboardPanel.whole(s.amount()) + " • " + DashboardPanel.pct(s.progress())));
            lines.add(DashboardLine.dim(s.seller() + " → " + s.buyer() + " • value " + DashboardPanel.whole(s.value())
                    + " • " + DashboardPanel.whole(s.distanceBlocks()) + "m", 1));
        }
        if (ops.shipments().isEmpty()) lines.add(DashboardLine.dim("No active shipments for this realm.", 0));
        lines.add(DashboardLine.header("Transport network"));
        for (var r : ops.routes()) {
            lines.add(DashboardLine.text("#" + r.id() + " " + r.mode() + " • " + r.from() + " → " + r.to()));
            lines.add(DashboardLine.dim((r.operational() ? "ONLINE" : "OFFLINE") + " • quality " + DashboardPanel.pct(r.quality())
                    + " • security " + DashboardPanel.pct(r.security()) + " • cap " + DashboardPanel.whole(r.capacityPerDay()) + "/day", 1));
        }
        if (ops.routes().isEmpty()) lines.add(DashboardLine.dim("No canonical routes for this realm.", 0));
        lines.add(DashboardLine.header("Industry"));
        for (var site : ops.industry()) {
            int color = ("ACTIVE".equals(site.status()) ? 0xFFE0E0E0 : 0xFFFFB56B);
            lines.add(new DashboardLine("#" + site.id() + " " + DashboardPanel.titleCase(site.kind()) + " L" + site.level()
                    + " @ " + site.settlement(), color, 0));
            lines.add(DashboardLine.dim(site.status() + " • condition " + DashboardPanel.pct(site.condition())
                    + " • utilization " + DashboardPanel.pct(site.utilization())
                    + " • cycles " + site.cycles() + " • downtime " + site.downtimeDays() + "d", 1));
        }
        if (ops.industry().isEmpty()) lines.add(DashboardLine.dim("No industrial sites for this realm.", 0));
        lines.add(DashboardLine.header("Assistance contracts"));
        for (var t : ops.assistanceTasks()) {
            lines.add(DashboardLine.text("#" + t.id() + " " + DashboardPanel.titleCase(t.type()) + " @ " + t.settlement()
                    + " • " + DashboardPanel.pct(t.progress()) + " done"));
            lines.add(DashboardLine.dim("Pressure left " + DashboardPanel.pct(t.remainingPressure())
                    + " • expires day " + t.expiresDay() + " • " + t.cause(), 1));
            lines.add(DashboardLine.dim("Deliver verified goods with /livingrealms assist deliver", 2));
        }
        if (ops.assistanceTasks().isEmpty()) lines.add(DashboardLine.dim("No open assistance contracts for this realm.", 0));
        return lines;
    }
}
