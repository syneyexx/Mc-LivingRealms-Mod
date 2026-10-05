package dev.livingrealms.sim.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.Objects;

public record PoliticsSnapshot(RealmDashboardSnapshot.PoliticsView politics) {
    public PoliticsSnapshot {
        politics = politics == null ? RealmDashboardSnapshot.PoliticsView.empty() : politics;
        Objects.requireNonNull(politics);
    }
}
