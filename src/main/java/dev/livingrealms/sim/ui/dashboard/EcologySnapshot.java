package dev.livingrealms.sim.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.Objects;

public record EcologySnapshot(RealmDashboardSnapshot.EcologyView ecology) {
    public EcologySnapshot {
        ecology = ecology == null ? RealmDashboardSnapshot.EcologyView.empty() : ecology;
        Objects.requireNonNull(ecology);
    }
}
