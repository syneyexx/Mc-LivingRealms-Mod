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
    /**
     * Persisted construction-receipt marker written by ContentRevision 16 migration.
     * It intentionally uses the existing receipt set, avoiding a schema change solely for a
     * physical-layout compatibility bit.
     */
    public static final String LEGACY_STREET_FABRIC_MARKER = "migration:legacy_street_fabric:v16";

    private SettlementConstructionPolicy() {}

    public static boolean allowsAutomaticCoreFabric(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        if (hasLegacyStreetFabric(settlement)) return false;
        if (!settlement.physicallyAnchored()) return true;
        return settlement.origin() != SettlementOrigin.FOREIGN_ADOPTED
                && settlement.origin() != SettlementOrigin.LEGACY;
    }

    public static boolean hasLegacyStreetFabric(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        return settlement.isConstructionCompleted(LEGACY_STREET_FABRIC_MARKER);
    }

    /**
     * Marks only settlements that demonstrably completed at least one pre-graph ROAD intent.
     * Identity/origin/position and all existing completion receipts remain untouched.
     */
    public static boolean migrateLegacyStreetFabric(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        if (hasLegacyStreetFabric(settlement)) return false;
        boolean legacyRoadReceipt = settlement.completedConstruction().stream()
                .anyMatch(key -> key.startsWith("road:"));
        if (!legacyRoadReceipt) return false;
        settlement.restoreConstructionCompleted(LEGACY_STREET_FABRIC_MARKER);
        return true;
    }
}
