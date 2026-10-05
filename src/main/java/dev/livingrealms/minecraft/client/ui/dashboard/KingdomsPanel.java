package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.ArrayList;
import java.util.List;

public final class KingdomsPanel implements DashboardPanel {
    @Override
    public void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page) {}

    @Override
    public List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page) {
        List<DashboardLine> lines = new ArrayList<>();
        lines.add(DashboardLine.header("Known kingdoms"));
        for (var f : snapshot.factions()) {
            String marker = f.memberRealm() ? "[YOU] " : f.localRealm() ? "[HERE] " : "";
            lines.add(DashboardLine.text(marker + f.name() + " — pop " + f.population() + ", settlements " + f.settlements()));
            lines.add(DashboardLine.dim("Ruler " + f.ruler() + " • treasury " + DashboardPanel.whole(f.treasury())
                    + " • tech " + DashboardPanel.one(f.technology()), 1));
        }
        if (snapshot.factions().isEmpty()) lines.add(DashboardLine.dim("No kingdoms known.", 0));
        return lines;
    }
}
