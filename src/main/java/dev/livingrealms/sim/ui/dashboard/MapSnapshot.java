package dev.livingrealms.sim.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.Objects;

public record MapSnapshot(RealmDashboardSnapshot.StrategicMapView map) {
    public MapSnapshot {
        map = map == null ? RealmDashboardSnapshot.StrategicMapView.empty() : map;
        Objects.requireNonNull(map);
    }
}
