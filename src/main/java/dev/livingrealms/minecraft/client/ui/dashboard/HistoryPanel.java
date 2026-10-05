package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.ArrayList;
import java.util.List;

public final class HistoryPanel implements DashboardPanel {
    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {}

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        List<DashboardLine> lines = new ArrayList<>();
        lines.add(DashboardLine.header("Recent world history"));
        for (var h : snapshot.history()) lines.add(DashboardLine.text("D" + h.day() + " • " + h.message()));
        if (snapshot.history().isEmpty()) lines.add(DashboardLine.dim("No recorded history yet.", 0));
        return lines;
    }
}
