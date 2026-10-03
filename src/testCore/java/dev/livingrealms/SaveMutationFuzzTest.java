package dev.livingrealms;

import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.validation.SimulationValidator;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimulationState;
import java.io.UncheckedIOException;

/** Deterministic corruption fuzzing for canonical world-state decoding. */
public final class SaveMutationFuzzTest {
    private static final int MUTATIONS = 768;

    private SaveMutationFuzzTest() {}

    public static void main(String[] args) {
        SimulationState source = new SimulationState(0x4C4956494E47524CL);
        DemoSeeder.seed(source);
        source.advanceDays(73);
        byte[] canonical = SimulationStateCodec.encode(source);

        int rejected = 0;
        int valid = 0;
        long cursor = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < MUTATIONS; i++) {
            cursor = cursor * 6364136223846793005L + 1442695040888963407L;
            int offset = (int) Long.remainderUnsigned(cursor, canonical.length);
            int bit = 1 << (int) ((cursor >>> 17) & 7L);
            byte[] mutated = canonical.clone();
            mutated[offset] ^= (byte) bit;
            try {
                SimulationState decoded = SimulationStateCodec.decode(mutated, source.species());
                SimulationValidator.validate(decoded).throwIfInvalid();
                valid++;
            } catch (UncheckedIOException | IllegalArgumentException | IllegalStateException expected) {
                rejected++;
            } catch (RuntimeException unexpected) {
                throw new AssertionError("decoder leaked unexpected runtime failure at mutation " + i + " offset " + offset, unexpected);
            }
        }
        check(rejected > MUTATIONS / 8, "fuzz corpus unexpectedly rejected too few corruptions: " + rejected);
        check(valid > 0, "fuzz corpus should include at least one semantically valid one-bit mutation");
        System.out.println("PASS save mutation fuzz: " + MUTATIONS + " deterministic bit mutations, rejected=" + rejected + ", still-valid=" + valid);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
