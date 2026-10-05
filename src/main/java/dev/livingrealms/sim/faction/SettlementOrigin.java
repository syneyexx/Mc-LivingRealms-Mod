package dev.livingrealms.sim.faction;

/**
 * First-class settlement provenance. Once a settlement is {@linkplain Settlement#physicallyAnchored()
 * physically anchored}, ordinary simulation must never relocate it.
 */
public enum SettlementOrigin {
    /** Fresh-world capital / selected authored satellite / rural hamlet from Spec densifier. */
    AUTHORED_SEED,
    /** Player-founded realm capital. Always anchored. */
    PLAYER_FOUNDED,
    /** Causal colony from overpopulation, surplus, strategy, etc. */
    CAUSAL_EXPANSION,
    /** Adopted foreign village/structure at its real physical coordinates. Always anchored. */
    FOREIGN_ADOPTED,
    /** Hidden Wizard Trees civilization. Always anchored. */
    WIZARD_TREES,
    /** Loaded from schema ≤18 (or otherwise pre-provenance) saves. Always anchored. */
    LEGACY
}
