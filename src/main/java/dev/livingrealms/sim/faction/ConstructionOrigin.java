package dev.livingrealms.sim.faction;

/** Provenance of a completed construction key. */
public enum ConstructionOrigin {
    /** Completed by SettlementConstructionMaterializer receipt (or headless test equivalent). */
    MATERIALIZED,
    /** Credited from a foreign village footprint — not Living Realms production. */
    FOREIGN_ADOPTED,
    /** Day-zero deterministic fabric authored by Minecraft chunk/world generation. */
    WORLDGEN
}
