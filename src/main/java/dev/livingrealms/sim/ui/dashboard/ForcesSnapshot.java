package dev.livingrealms.sim.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.Objects;

/** Air/naval force roster section. */
public record ForcesSnapshot(RealmDashboardSnapshot.ForcesView forces) {
    public ForcesSnapshot {
        forces = forces == null ? RealmDashboardSnapshot.ForcesView.empty() : forces;
        Objects.requireNonNull(forces);
    }
}
