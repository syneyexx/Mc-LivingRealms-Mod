package dev.livingrealms;

import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.SimulationState;
import java.util.HashSet;
import java.util.Set;

/** C1: every authored Spec name exists; unique names; count matches densifier target. */
public final class AuthoredSettlementNamesTest {
    private AuthoredSettlementNamesTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0xC1C1L);
        DemoSeeder.seed(state);
        Set<String> present = new HashSet<>();
        for (var faction : state.factions()) for (var settlement : faction.settlements()) {
            check(present.add(settlement.name()), "duplicate settlement name " + settlement.name());
        }
        for (String name : SettlementDensitySeeder.authoredSettlementNames()) {
            check(present.contains(name), "missing authored Spec/capital " + name);
        }
        int surface = state.factions().stream()
                .filter(f -> !f.name().equals("Wizard Trees"))
                .mapToInt(f -> f.settlements().size())
                .sum();
        check(surface == SettlementDensitySeeder.SURFACE_STARTER_SETTLEMENTS,
                "surface settlements must equal 12×TARGET: expected "
                        + SettlementDensitySeeder.SURFACE_STARTER_SETTLEMENTS + " got " + surface);
        var wizard = state.factions().stream().filter(f -> f.name().equals("Wizard Trees")).findFirst().orElseThrow();
        check(wizard.settlements().size() == 3, "Wizard Trees stays at 3");
        System.out.println("PASS authored settlement names: all Specs present + "
                + SettlementDensitySeeder.SURFACE_STARTER_SETTLEMENTS + " surface + Wizard Trees 3");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
