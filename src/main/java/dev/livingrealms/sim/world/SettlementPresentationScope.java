package dev.livingrealms.sim.world;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Transient (non-persisted) presentation scope: which settlements currently have a loaded player
 * nearby. Headless soak / pure {@code advanceDays} leave this empty so implied hinterland
 * production and phantom housing floors remain available.
 */
public final class SettlementPresentationScope {
    private final Set<Long> activatedSettlementIds = new LinkedHashSet<>();

    public void setActivated(Collection<Long> settlementIds) {
        activatedSettlementIds.clear();
        if (settlementIds == null) return;
        for (Long id : settlementIds) {
            if (id != null && id > 0) activatedSettlementIds.add(id);
        }
    }

    public void clear() {
        activatedSettlementIds.clear();
    }

    public boolean anyActivated() {
        return !activatedSettlementIds.isEmpty();
    }

    public boolean isActivated(long settlementId) {
        return settlementId > 0 && activatedSettlementIds.contains(settlementId);
    }

    public Set<Long> activatedIds() {
        return Collections.unmodifiableSet(activatedSettlementIds);
    }
}
