package dev.livingrealms.sim.persistence;

/**
 * Pure decision helpers for Living Realms content-revision migrations.
 * Kept Minecraft-free so core tests can pin each revision gate.
 */
public final class ContentMigrationPolicy {
    private ContentMigrationPolicy() {}

    /** Morphology rebuild for geography-derived streets (revision 10). */
    public static boolean shouldResetMorphology(int contentRevision) {
        return contentRevision < 10;
    }

    /** Wizard Trees faction bootstrap (revision 5). */
    public static boolean shouldEnsureWizardTrees(int contentRevision) {
        return contentRevision < 5;
    }

    /**
     * Starter density / authored Spec placement. Runs once when the save is below the current
     * content revision (add-only densifier).
     */
    public static boolean shouldEnsureDensity(int contentRevision, int currentRevision) {
        return contentRevision < currentRevision;
    }

    /**
     * Revision 16 changes settlement street topology from legacy ROAD-intent-first geometry to
     * graph-first polylines. Saves that already materialized legacy road receipts must keep that
     * physical fabric authoritative instead of reconciling a second incompatible street layout.
     */
    public static boolean shouldFreezeLegacyStreetFabric(int contentRevision) {
        return contentRevision < 16;
    }

    /**
     * Revision 18: only saves that already opted into true civilization worldgen receive the
     * Wizard Trees day-zero completion receipts. Version-0 legacy saves remain runtime-authored.
     */
    public static boolean shouldAdoptWizardWorldgenReceipts(
            int contentRevision, int worldgenVersion, int currentWorldgenVersion) {
        return contentRevision < 18
                && currentWorldgenVersion > 0
                && worldgenVersion == currentWorldgenVersion;
    }

    /** Legacy pre-checksum payload path when integrity token is absent. */
    public static boolean isLegacyIntegrityPath(long expectedIntegrity) {
        return expectedIntegrity == 0L;
    }

    /** Current payload with a non-matching CRC must be rejected. */
    public static boolean shouldRejectCorruptPayload(long expectedIntegrity, long actualIntegrity) {
        return expectedIntegrity != 0L && expectedIntegrity != actualIntegrity;
    }
}
