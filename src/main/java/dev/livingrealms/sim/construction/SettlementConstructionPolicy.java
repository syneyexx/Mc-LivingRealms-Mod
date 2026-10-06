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

    /**
     * Runtime construction gate layered on top of provenance. On a true-worldgen fresh save the
     * authored seed settlements are intentionally quiet for canonical day zero: their initial
     * physical fabric already belongs to chunk generation. Player-founded/special settlements and
     * all later days remain eligible for normal causal construction.
     */
    public static boolean allowsRuntimeConstruction(
            Settlement settlement, boolean starterWorldgenEnabled, long canonicalDay) {
        Objects.requireNonNull(settlement, "settlement");
        if (!allowsAutomaticCoreFabric(settlement)) return false;
        return !(starterWorldgenEnabled
                && canonicalDay == 0
                && settlement.origin() == SettlementOrigin.AUTHORED_SEED);
    }

    public static boolean hasLegacyStreetFabric(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        return settlement.isConstructionCompleted(LEGACY_STREET_FABRIC_MARKER);
    }

    /** True when a pre-graph ROAD completion receipt proves physical legacy street fabric. */
    public static boolean hasLegacyRoadReceipt(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        return settlement.completedConstruction().stream().anyMatch(key -> key.startsWith("road:"));
    }

    /**
     * Persists the compatibility marker without changing identity/origin/position.
     * This is separate from detection so very old morphology migrations may clear obsolete receipts
     * after evidence is captured without losing the v16 freeze decision.
     */
    public static boolean markLegacyStreetFabric(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        if (hasLegacyStreetFabric(settlement)) return false;
        settlement.restoreConstructionCompleted(LEGACY_STREET_FABRIC_MARKER);
        return true;
    }

    /** Convenience path when no earlier migration step can clear the evidence. */
    public static boolean migrateLegacyStreetFabric(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        return hasLegacyRoadReceipt(settlement) && markLegacyStreetFabric(settlement);
    }
}
