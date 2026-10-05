package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import java.util.Objects;

/**
 * Provenance gate for automatic Living Realms block construction.
 *
 * <p>Adopted foreign and legacy anchored settlements are integration points: their existing
 * physical footprint remains authoritative and must not be replaced by a generated LR core.
 * Explicit future integration features may add non-destructive connectors outside that footprint,
 * but ordinary settlement reconciliation is denied here.</p>
 */
public final class SettlementConstructionPolicy {
    private SettlementConstructionPolicy() {}

    public static boolean allowsAutomaticCoreFabric(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        if (!settlement.physicallyAnchored()) return true;
        return settlement.origin() != SettlementOrigin.FOREIGN_ADOPTED
                && settlement.origin() != SettlementOrigin.LEGACY;
    }
}
