package dev.livingrealms;

import dev.livingrealms.sim.content.RealmDefinition;
import dev.livingrealms.sim.content.RealmDefinitionLoader;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.SettlementSpacingPolicy;
import dev.livingrealms.sim.world.SimulationState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Realm JSON loading plus role-aware starter placement regression. */
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
            check(r.satellites().size() == 10, r.id() + " must retain 10 authored satellite specs");
            check(r.cultureId() != null && !r.cultureId().isBlank(), r.id() + " cultureId");
            satellites += r.satellites().size();
        }
        check(satellites == 120, "120 authored satellite specs");

        SimulationState state = new SimulationState(0x8EA17EL);
        DemoSeeder.seed(state);
        int surface = state.factions().stream()
                .filter(f -> !f.name().equals("Wizard Trees"))
                .mapToInt(f -> f.settlements().size())
                .sum();
        check(surface >= 36, "starter surface must not regress below legacy fabric count: " + surface);
        long capitals = state.factions().stream()
                .filter(f -> !f.name().equals("Wizard Trees"))
                .flatMap(f -> f.settlements().stream())
                .filter(s -> s.role() == SettlementRole.CAPITAL)
                .count();
        check(capitals == 12, "twelve explicit capital roles");
        check(SettlementDensitySeeder.ensureStarterDensity(state) == 0, "idempotent densifier");

        List<Settlement> all = state.factions().stream()
                .filter(f -> !f.name().equals("Wizard Trees"))
                .flatMap(f -> f.settlements().stream()).toList();
        boolean belowLegacyFloor = false;
        for (int i = 0; i < all.size(); i++) for (int j = i + 1; j < all.size(); j++) {
            Settlement a = all.get(i), b = all.get(j);
            double dist = a.position().distanceTo(b.position());
            double floor = SettlementSpacingPolicy.minimumDistance(a, b);
            check(dist >= floor - 1.0,
                    "role-aware spacing violated: " + a.name() + " ↔ " + b.name()
                            + " = " + Math.round(dist) + " < " + Math.round(floor));
            if (dist < 1_999.0) belowLegacyFloor = true;
        }
        check(belowLegacyFloor, "starter data still behaves like universal 2000-block spacing");
        check(RealmDefinitionLoader.byId("aster") != null, "aster realm present");
        check(RealmDefinitionLoader.byDisplayName("Kingdom of Aster") != null, "aster by display name");

        System.out.println("PASS realm data loader: 12 realm JSON definitions + role-aware starter placement");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
