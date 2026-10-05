package dev.livingrealms.sim.persistence.codec;

import dev.livingrealms.sim.world.SimulationState;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Diplomacy wire sections.
 * Treaties are the leading contiguous subsection of the schema-4 strategic block;
 * the orchestrator still encodes them via {@link WarfareCodec#writeV4Strategic} to preserve byte layout.
 * Diplomatic marriages live inside {@link SocietyCodec#writeV13Humanity}.
 */
public final class DiplomacyCodec {
    private DiplomacyCodec() {}
}
