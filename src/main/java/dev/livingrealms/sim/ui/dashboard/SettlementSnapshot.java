package dev.livingrealms.sim.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.List;
import java.util.Objects;

public record SettlementSnapshot(List<RealmDashboardSnapshot.SettlementView> settlements) {
    public SettlementSnapshot {
        settlements = List.copyOf(settlements == null ? List.of() : settlements);
        Objects.requireNonNull(settlements);
    }
}
