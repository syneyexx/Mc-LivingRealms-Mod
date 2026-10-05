package dev.livingrealms.sim.persistence.codec;

/**
 * Player-agency wire sections.
 * Player standings are the trailing contiguous subsection of {@link WarfareCodec#writeV6NavalAndPlayers};
 * career/influence extensions live inside {@link ConstructionCodec#writeV17FinalProduct}.
 * Kept as an explicit domain type so agency persistence stays discoverable without a schema bump.
 */
public final class PlayerAgencyCodec {
    private PlayerAgencyCodec() {}
}
