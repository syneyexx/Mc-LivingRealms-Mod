package dev.livingrealms.sim.faction;

/**
 * How Living Realms participates in a settlement's physical growth.
 * Default for player-founded settlements is {@link #HYBRID}.
 */
public enum DevelopmentMode {
    /** Ordinary autonomous Living Realms development. */
    AUTO,
    /** Player-registered buildings count; Living Realms may fill genuine deficits over time. */
    HYBRID,
    /** Living Realms mostly limits itself to roads/public infrastructure unless explicitly enabled. */
    PLAYER_LED
}
