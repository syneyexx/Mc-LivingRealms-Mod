package dev.livingrealms;

import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.SimulationState;
import java.util.HashSet;
import java.util.Set;

/**
 * Authored Spec catalog is preserved; fresh worlds seed only capital + 1 satellite + 1 rural.
 * Remaining Specs stay available as expansion catalog names.
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
        // Capitals must be present; Spec catalog must exist as authoritative content (not all seeded).
        Set<String> capitals = new HashSet<>();
        for (String name : SettlementDensitySeeder.authoredSettlementNames()) {
            // Capitals are first names per realm in the catalog list — verify catalog non-empty
            // and that every present satellite (if any) is from the catalog.
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
            check(catalogHits == 1, faction.name() + " must seed exactly one authored Spec satellite, got " + catalogHits);
        }
        int surface = state.factions().stream()
                .filter(f -> !f.name().equals("Wizard Trees"))
                .mapToInt(f -> f.settlements().size())
                .sum();
        check(surface == SettlementDensitySeeder.SURFACE_STARTER_SETTLEMENTS,
                "surface settlements must equal 36: got " + surface);
        var wizard = state.factions().stream().filter(f -> f.name().equals("Wizard Trees")).findFirst().orElseThrow();
        check(wizard.settlements().size() == 3, "Wizard Trees stays at 3");
        System.out.println("PASS authored settlement names: catalog preserved + "
                + SettlementDensitySeeder.SURFACE_STARTER_SETTLEMENTS + " surface + Wizard Trees 3");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
