package dev.livingrealms;

import dev.livingrealms.sim.persistence.SaveSizeAuditor;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.validation.SimulationValidator;
import dev.livingrealms.sim.world.*;

/** Ten-year deterministic soak with explicit 30/365/3650-day gates and persistence checks. */
public final class LongRunSoakTest {
    private static final int FINAL_DAY = 3650;

    private LongRunSoakTest() {}

    public static void main(String[] args) {
        SimulationState a = new SimulationState(0x1A2B3C4D5E6F7788L);
        DemoSeeder.seed(a);
        SimulationState b = new SimulationState(0x1A2B3C4D5E6F7788L);
        DemoSeeder.seed(b);
        SimulationValidator.validate(a).throwIfInvalid();

        boolean day30 = false;
        boolean day365 = false;
        boolean day3650 = false;
        SaveSizeAuditor.Report size365 = null;
        SaveSizeAuditor.Report size3650 = null;
        for (int day = 1; day <= FINAL_DAY; day++) {
            a.advanceDays(1);
            b.advanceDays(1);

            if (day % 30 == 0 || day == 365 || day == FINAL_DAY) {
                SimulationValidator.validate(a).throwIfInvalid();
                if (!a.summary().equals(b.summary())) {
                    throw new AssertionError("determinism diverged at day " + day);
                }
            }

            if (day == 30 || day == 365 || day == FINAL_DAY || day % 365 == 0) {
                assertRoundTrip(a, day);
            }
            if (day == 365) {
                size365 = SaveSizeAuditor.measure(a);
                assertSaveSize(size365, "day365");
                System.out.println("SAVE_SIZE " + size365.documentLine());
            } else if (day == FINAL_DAY) {
                size3650 = SaveSizeAuditor.measure(a);
                assertSaveSize(size3650, "day3650");
                System.out.println("SAVE_SIZE " + size3650.documentLine());
            }

            day30 |= day == 30;
            day365 |= day == 365;
            day3650 |= day == FINAL_DAY;
        }

        if (!day30 || !day365 || !day3650) throw new AssertionError("required soak checkpoints were not executed");
        if (size365 == null || size3650 == null) throw new AssertionError("required save-size checkpoints were not executed");
        if (size3650.totalBytes() >= size365.totalBytes() * 4L + 4_000_000L) {
            throw new AssertionError("day 3650 save growth unbounded: " + size3650.totalBytes()
                    + " vs day365 " + size365.totalBytes());
        }
        if (a.clock().day() != FINAL_DAY) throw new AssertionError("unexpected soak length " + a.clock().day());

        long groups = a.regions().stream().mapToLong(r -> r.populations().size()).sum();
        long predatorGroups = a.regions().stream().flatMap(r -> r.populations().stream()).filter(g -> {
            var sp = a.species().get(g.speciesId());
            return sp != null && (sp.diet() == dev.livingrealms.sim.ecology.Diet.CARNIVORE
                    || sp.diet() == dev.livingrealms.sim.ecology.Diet.PISCIVORE);
        }).count();
        if (groups < 8 || predatorGroups < 2) {
            throw new AssertionError("long-run biodiversity collapse: groups=" + groups + ", predators=" + predatorGroups);
        }
        System.out.println("PASS 3650-day deterministic soak + persistence/save-size gates + invariant validation + biodiversity floor: " + a.summary());
    }

    private static void assertSaveSize(SaveSizeAuditor.Report report, String label) {
        if (report.totalBytes() >= SimulationStateCodec.MAX_STATE_BYTES) {
            throw new AssertionError(label + " over hard save cap: " + report.totalBytes());
        }
        if (report.totalBytes() >= SaveSizeAuditor.ADVISORY_SOFT_BYTES) {
            throw new AssertionError(label + " over soft save advisory: " + report.totalBytes());
        }
    }

    private static void assertRoundTrip(SimulationState state, int day) {
        byte[] bytes = SimulationStateCodec.encode(state);
        SimulationState restored = SimulationStateCodec.decode(bytes, state.species());
        SimulationValidator.validate(restored).throwIfInvalid();
        if (!state.summary().equals(restored.summary())) {
            throw new AssertionError("round-trip mismatch at day " + day);
        }
    }
}
