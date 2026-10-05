package dev.livingrealms.sim.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.Objects;

/** Economy + logistics operations section. */
public record EconomySnapshot(
        RealmDashboardSnapshot.RealmView realm,
        RealmDashboardSnapshot.OperationsView operations
) {
    public EconomySnapshot {
        Objects.requireNonNull(realm);
        operations = operations == null ? RealmDashboardSnapshot.OperationsView.empty() : operations;
    }
}
