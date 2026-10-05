package dev.livingrealms.sim.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.List;

public record LawSnapshot(List<RealmDashboardSnapshot.BountyView> bounties) {
    public LawSnapshot {
        bounties = List.copyOf(bounties == null ? List.of() : bounties);
    }
}
