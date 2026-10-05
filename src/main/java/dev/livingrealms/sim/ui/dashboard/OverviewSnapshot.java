package dev.livingrealms.sim.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.List;
import java.util.Objects;

/** Domain section of the protocol-20 dashboard payload (overview envelope). */
public record OverviewSnapshot(
        long day,
        String worldSummary,
        RealmDashboardSnapshot.JurisdictionView jurisdiction,
        RealmDashboardSnapshot.PlayerView player,
        RealmDashboardSnapshot.RealmView realm,
        RealmDashboardSnapshot.SettingsView settings,
        List<RealmDashboardSnapshot.FactionSummary> factions,
        List<RealmDashboardSnapshot.HistoryView> history
) {
    public OverviewSnapshot {
        Objects.requireNonNull(jurisdiction);
        Objects.requireNonNull(player);
        Objects.requireNonNull(realm);
        Objects.requireNonNull(settings);
        factions = List.copyOf(factions == null ? List.of() : factions);
        history = List.copyOf(history == null ? List.of() : history);
        worldSummary = worldSummary == null ? "" : worldSummary;
    }
}
