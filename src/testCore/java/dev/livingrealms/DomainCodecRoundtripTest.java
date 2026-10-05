package dev.livingrealms;

import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimulationState;
import java.util.Arrays;

/**
 * Confirms schema-21 domain-codec orchestration preserves byte-identical encode/decode.
 * Complements {@link SaveIntegrityTest} without duplicating corruption coverage.
 */
public final class DomainCodecRoundtripTest {
    private DomainCodecRoundtripTest() {}

    public static void main(String[] args) {
        check(SimulationStateCodec.SCHEMA_VERSION == 21, "SCHEMA_VERSION must remain 21");
        SimulationState state = new SimulationState(0xC0DEC0DECAFEL);
        DemoSeeder.seed(state);
        state.advanceDays(14);
        byte[] first = SimulationStateCodec.encode(state);
        byte[] second = SimulationStateCodec.encode(state);
        check(Arrays.equals(first, second), "domain-codec encode must be deterministic");
        check(SimulationStateCodec.inspectSchema(first) == 21, "encoded schema must be 21");
        SimulationState restored = SimulationStateCodec.decode(first, state.species());
        byte[] third = SimulationStateCodec.encode(restored);
        check(Arrays.equals(first, third), "decode→encode must be byte-identical under schema 21");
        check(restored.summary().equals(state.summary()), "summary must round-trip");
        System.out.println("PASS domain codec roundtrip: schema 21 byte-identical encode/decode via domain codecs");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
