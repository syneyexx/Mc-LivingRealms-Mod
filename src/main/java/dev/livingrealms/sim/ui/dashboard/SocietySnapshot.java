package dev.livingrealms.sim.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.List;

/** Settlement + society views share settlement rows. */
public record SocietySnapshot(List<RealmDashboardSnapshot.SettlementView> settlements) {
    public SocietySnapshot {
        settlements = List.copyOf(settlements == null ? List.of() : settlements);
    }
}
