package dev.livingrealms;

import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.SimulationState;
import java.util.HashSet;
import java.util.Set;

/**
 * Authored Spec catalog is preserved; fresh worlds materialize all ten authored surface satellites
 * while legacy worlds may still consume absent specs through causal expansion.
 */
public final class AuthoredSettlementNamesTest {
    private AuthoredSettlementNamesTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0xC1C1L);
        DemoSeeder.seed(state);
        Set<String> present = new HashSet<>();
        for (var faction : state.factions()) for (var settlement : faction.settlements()) {
            check(present.add(settlement.name()), "duplicate settlement name " + settlement.name());
        }
        check(SettlementDensitySeeder.authoredSettlementNames().size() == 12 + 120,
                "12 capitals + 10 Specs × 12 realms");
        check(SettlementDensitySeeder.authoredExpansionCatalogNames().size() == 120,
                "expansion catalog retains all 120 Specs");
        for (var faction : state.factions()) {
            if (faction.name().equals("Wizard Trees")) continue;
            for (var settlement : faction.settlements()) {
                if (SettlementDensitySeeder.authoredExpansionCatalogNames().contains(settlement.name())) {
                    // seeded satellite from catalog — ok
                }
            }
            long catalogHits = faction.settlements().stream()
                    .filter(s -> SettlementDensitySeeder.authoredExpansionCatalogNames().contains(s.name()))
                    .count();
            check(catalogHits == SettlementDensitySeeder.AUTHORED_SATELLITES_PER_REALM,
                    faction.name() + " must seed all authored Spec satellites, got " + catalogHits);
        }
        int surface = state.factions().stream()
                .filter(f -> !f.name().equals("Wizard Trees"))
                .mapToInt(f -> f.settlements().size())
                .sum();
        check(surface >= SettlementDensitySeeder.MIN_SURFACE_STARTER_SETTLEMENTS
                        && surface <= SettlementDensitySeeder.MAX_SURFACE_STARTER_SETTLEMENTS,
                "surface starter range: got " + surface);
        var wizard = state.factions().stream().filter(f -> f.name().equals("Wizard Trees")).findFirst().orElseThrow();
        check(wizard.settlements().size() == 3, "Wizard Trees stays at 3");
        System.out.println("PASS authored settlement names: all 120 authored satellites seeded on fresh worlds + bounded rural hamlets + Wizard Trees 3");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
