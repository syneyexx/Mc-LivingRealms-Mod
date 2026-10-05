package dev.livingrealms;

import dev.livingrealms.sim.content.RealmDefinition;
import dev.livingrealms.sim.content.RealmDefinitionLoader;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.SimulationState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Wave 8: realm JSON loads 12 kingdoms; densifier still seeds 36 surface at 2000 spacing. */
public final class RealmDataLoaderTest {
    private RealmDataLoaderTest() {}

    public static void main(String[] args) {
        RealmDefinitionLoader.clearCache();
        List<RealmDefinition> realms = RealmDefinitionLoader.loadAll();
        check(realms.size() == 12, "expected 12 realms, got " + realms.size());
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        int satellites = 0;
        for (RealmDefinition r : realms) {
            check(ids.add(r.id()), "duplicate realm id " + r.id());
            check(names.add(r.displayName()), "duplicate realm name " + r.displayName());
            check(r.capitalName() != null && !r.capitalName().isBlank(), "capital required");
            check(r.satellites().size() == 10, r.id() + " must have 10 Spec satellites");
            check(r.cultureId() != null && !r.cultureId().isBlank(), r.id() + " cultureId");
            satellites += r.satellites().size();
        }
        check(satellites == 120, "120 expansion Specs");
        check(SettlementDensitySeeder.TARGET_SETTLEMENTS_PER_REALM == 3, "TARGET=3");
        check(SettlementDensitySeeder.MIN_SETTLEMENT_SPACING == 2000.0, "spacing 2000");
        check(SettlementDensitySeeder.SURFACE_STARTER_SETTLEMENTS == 36, "36 surface");

        SimulationState state = new SimulationState(0x8EA17EL);
        DemoSeeder.seed(state);
        int surface = state.factions().stream()
                .filter(f -> !f.name().equals("Wizard Trees"))
                .mapToInt(f -> f.settlements().size())
                .sum();
        check(surface == 36, "densifier seeds 36 surface, got " + surface);
        check(SettlementDensitySeeder.ensureStarterDensity(state) == 0, "idempotent densifier");

        var all = state.factions().stream().flatMap(f -> f.settlements().stream()).toList();
        for (int i = 0; i < all.size(); i++) for (int j = i + 1; j < all.size(); j++) {
            double dist = all.get(i).position().distanceTo(all.get(j).position());
            check(dist >= SettlementDensitySeeder.MIN_SETTLEMENT_SPACING - 1.0,
                    "spacing violated: " + all.get(i).name() + " ↔ " + all.get(j).name() + " = " + dist);
        }
        check(PlayerSettlementFounder.MIN_SETTLEMENT_SPACING == 2000.0, "founder spacing pin");
        check(RealmDefinitionLoader.byId("aster") != null, "aster realm present");
        check(RealmDefinitionLoader.byDisplayName("Kingdom of Aster") != null, "aster by display name");

        System.out.println("PASS realm data loader: 12 realms JSON + densifier 36 surface @ 2000 spacing");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
