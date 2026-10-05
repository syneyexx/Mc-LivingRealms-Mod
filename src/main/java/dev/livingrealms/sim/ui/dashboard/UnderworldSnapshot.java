package dev.livingrealms.sim.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.Objects;

public record UnderworldSnapshot(RealmDashboardSnapshot.UnderworldView underworld) {
    public UnderworldSnapshot {
        underworld = underworld == null ? RealmDashboardSnapshot.UnderworldView.empty() : underworld;
        Objects.requireNonNull(underworld);
    }
}
