package dev.livingrealms.sim.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.Objects;

public record SettingsSnapshot(RealmDashboardSnapshot.SettingsView settings) {
    public SettingsSnapshot {
        settings = settings == null ? RealmDashboardSnapshot.SettingsView.defaults() : settings;
        Objects.requireNonNull(settings);
    }
}
