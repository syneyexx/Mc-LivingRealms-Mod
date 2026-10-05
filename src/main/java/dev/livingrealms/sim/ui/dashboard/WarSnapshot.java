package dev.livingrealms.sim.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.List;

public record WarSnapshot(
        List<RealmDashboardSnapshot.WarView> wars,
        RealmDashboardSnapshot.WarfareView warfare
) {
    public WarSnapshot {
        wars = List.copyOf(wars == null ? List.of() : wars);
        warfare = warfare == null ? RealmDashboardSnapshot.WarfareView.empty() : warfare;
    }
}
