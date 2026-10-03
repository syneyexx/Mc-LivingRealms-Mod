package dev.livingrealms;

import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimulationState;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Corruption/format-hardening tests for the canonical binary world state. */
public final class SaveIntegrityTest {
    private SaveIntegrityTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0xCAFEF00DL);
        DemoSeeder.seed(state);
        state.advanceDays(31);
        byte[] canonical = SimulationStateCodec.encode(state);
        byte[] canonicalAgain = SimulationStateCodec.encode(state);
        check(Arrays.equals(canonical, canonicalAgain), "same canonical state must encode byte-identically");
        check(SimulationStateCodec.inspectSchema(canonical) == SimulationStateCodec.SCHEMA_VERSION, "payload schema inspection");
        check(SimulationStateCodec.integrityToken(canonical) != 0L, "integrity token must reserve zero for legacy SavedData");
        check(SimulationStateCodec.integrityToken(canonical) == SimulationStateCodec.integrityToken(canonicalAgain), "identical payload must have identical integrity token");
        check(SimulationStateCodec.decode(canonical, state.species()).summary().equals(state.summary()), "valid save must round-trip");

        byte[] badMagic = canonical.clone();
        badMagic[0] ^= 0x7F;
        rejects(badMagic, state, "bad magic");
        rejectsHeaderInspection(badMagic, "bad magic header inspection");

        byte[] oldSchema = canonical.clone();
        ByteBuffer.wrap(oldSchema).putInt(4, SimulationStateCodec.MIN_SUPPORTED_SCHEMA - 1);
        rejects(oldSchema, state, "schema below support floor");

        byte[] futureSchema = canonical.clone();
        ByteBuffer.wrap(futureSchema).putInt(4, SimulationStateCodec.SCHEMA_VERSION + 1);
        rejects(futureSchema, state, "future schema");

        byte[] absurdRegionCount = canonical.clone();
        ByteBuffer.wrap(absurdRegionCount).putInt(32, Integer.MAX_VALUE);
        rejects(absurdRegionCount, state, "absurd collection count");

        // Header(32) + region count(4) + first region id(8) + first UTF-8 length(4) -> first biome byte.
        byte[] malformedUtf8 = canonical.clone();
        malformedUtf8[48] = (byte) 0xC0;
        rejects(malformedUtf8, state, "malformed UTF-8");

        byte[] oversized = new byte[SimulationStateCodec.MAX_STATE_BYTES + 1];
        System.arraycopy(canonical, 0, oversized, 0, Math.min(canonical.length, oversized.length));
        rejects(oversized, state, "payload above global size limit");
        rejectsHeaderInspection(oversized, "payload above global size limit");

        int maxTrim = Math.min(64, canonical.length - 1);
        for (int trim = 1; trim <= maxTrim; trim++) {
            rejects(Arrays.copyOf(canonical, canonical.length - trim), state, "truncated payload -" + trim);
        }

        byte[] bitFlip = canonical.clone();
        bitFlip[bitFlip.length / 2] ^= 0x01;
        check(SimulationStateCodec.integrityToken(bitFlip) != SimulationStateCodec.integrityToken(canonical), "checksum must detect payload bit flip");

        byte[] trailing = Arrays.copyOf(canonical, canonical.length + 4);
        trailing[canonical.length] = 0x4C;
        trailing[canonical.length + 1] = 0x52;
        trailing[canonical.length + 2] = 0x21;
        trailing[canonical.length + 3] = 0x00;
        rejects(trailing, state, "trailing bytes");

        System.out.println("PASS save integrity: deterministic encoding + bounded payload/string + strict UTF-8/magic/schema/count/truncation/trailing-data rejection");
    }

    private static void rejects(byte[] bytes, SimulationState state, String label) {
        boolean rejected = false;
        try {
            SimulationStateCodec.decode(bytes, state.species());
        } catch (UncheckedIOException | IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "corrupt save was accepted: " + label);
    }

    private static void rejectsHeaderInspection(byte[] bytes, String label) {
        boolean rejected = false;
        try { SimulationStateCodec.inspectSchema(bytes); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "corrupt header was accepted: " + label);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
