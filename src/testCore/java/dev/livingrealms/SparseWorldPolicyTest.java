package dev.livingrealms;

import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.SettlementSpacingPolicy;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.List;

/** Product gate for deterministic type-aware settlement spacing. */
public final class SparseWorldPolicyTest {
    private SparseWorldPolicyTest() {}

    public static void main(String[] args) {
        freshSeedUsesRoleAwareFloors();
        starterHierarchyAndCapitalDistances();
        deterministicRepeatability();
        playerCapitalFoundingUsesRoleAwareClearance();
        matrixPins();
        System.out.println("PASS SparseWorldPolicyTest: role-aware settlement floors; no universal 2000-block exclusion");
    }

    private static void freshSeedUsesRoleAwareFloors() {
        SimulationState state = new SimulationState(0x2000L);
        DemoSeeder.seed(state);
        check(state.factions().size() == 13, "12 kingdoms + Wizard Trees");

        long surfaceCapitals = state.factions().stream()
                .filter(f -> !f.name().equals("Wizard Trees"))
                .flatMap(f -> f.settlements().stream())
                .filter(s -> s.role() == SettlementRole.CAPITAL).count();
        check(surfaceCapitals == 12, "every surface realm requires exactly one capital role");

        List<Settlement> surface = state.factions().stream()
                .filter(f -> !f.name().equals("Wizard Trees"))
                .flatMap(f -> f.settlements().stream()).toList();
        boolean observedBelowOldGlobalFloor = false;
        for (int i = 0; i < surface.size(); i++) for (int j = i + 1; j < surface.size(); j++) {
            Settlement a = surface.get(i), b = surface.get(j);
            double dist = a.position().distanceTo(b.position());
            double floor = SettlementSpacingPolicy.minimumDistance(a, b);
            check(dist >= floor - 1.0,
                    "role-aware floor violated: " + a.name() + "(" + a.role() + ") ↔ "
                            + b.name() + "(" + b.role() + ") = " + Math.round(dist)
                            + " < " + Math.round(floor));
            if (dist < 1_999.0) observedBelowOldGlobalFloor = true;
        }
        check(observedBelowOldGlobalFloor, "fresh world still behaves as if every settlement had a 2000-block floor");
        check(SettlementDensitySeeder.ensureStarterDensity(state) == 0, "idempotent densifier");

        for (var faction : state.factions()) {
            if (faction.name().equals("Wizard Trees")) continue;
            for (Settlement s : faction.settlements()) {
                check(s.origin() == SettlementOrigin.AUTHORED_SEED,
                        "fresh seed origin for " + s.name());
            }
        }
    }

    private static void starterHierarchyAndCapitalDistances() {
        SimulationState state = new SimulationState(0xC1A11L);
        DemoSeeder.seed(state);
        List<Settlement> capitals = state.factions().stream()
                .filter(f -> !f.name().equals("Wizard Trees"))
                .flatMap(f -> f.settlements().stream())
                .filter(s -> s.role() == SettlementRole.CAPITAL)
                .toList();
        check(capitals.size() == 12, "twelve surface capitals");

        double minNearest = Double.POSITIVE_INFINITY;
        double maxNearest = 0.0;
        for (Settlement capital : capitals) {
            double nearest = capitals.stream()
                    .filter(other -> other.id() != capital.id())
                    .mapToDouble(other -> capital.position().distanceTo(other.position()))
                    .min().orElseThrow();
            check(nearest >= 3_000.0 && nearest <= 4_500.0,
                    capital.name() + " nearest capital outside preferred range: " + Math.round(nearest));
            minNearest = Math.min(minNearest, nearest);
            maxNearest = Math.max(maxNearest, nearest);
        }
        check(maxNearest - minNearest >= 50.0, "capital lattice must not be exact-equal spacing");

        for (var faction : state.factions()) {
            if (faction.name().equals("Wizard Trees")) continue;
            long towns = faction.settlements().stream().filter(s -> s.role() == SettlementRole.TOWN).count();
            long villages = faction.settlements().stream().filter(s -> s.role() == SettlementRole.VILLAGE).count();
            long hamlets = faction.settlements().stream().filter(s -> s.role() == SettlementRole.HAMLET).count();
            check(towns >= 2 && towns <= 4, faction.name() + " towns=" + towns);
            check(villages >= 6 && villages <= 8, faction.name() + " villages=" + villages);
            check(hamlets >= 6 && hamlets <= 14, faction.name() + " hamlets=" + hamlets);
            check(faction.settlements().size() >= 17 && faction.settlements().size() <= 25,
                    faction.name() + " total starters=" + faction.settlements().size());
        }
    }

    private static void deterministicRepeatability() {
        SimulationState a = new SimulationState(0xABCDL);
        SimulationState b = new SimulationState(0xABCDL);
        DemoSeeder.seed(a);
        DemoSeeder.seed(b);
        List<Settlement> sa = a.factions().stream().flatMap(f -> f.settlements().stream()).toList();
        List<Settlement> sb = b.factions().stream().flatMap(f -> f.settlements().stream()).toList();
        check(sa.size() == sb.size(), "same count");
        for (int i = 0; i < sa.size(); i++) {
            check(sa.get(i).name().equals(sb.get(i).name()), "name order");
            check(sa.get(i).role() == sb.get(i).role(), "role order");
            check(Math.abs(sa.get(i).position().x() - sb.get(i).position().x()) < 1e-6, "x");
            check(Math.abs(sa.get(i).position().z() - sb.get(i).position().z()) < 1e-6, "z");
        }
    }

    private static void playerCapitalFoundingUsesRoleAwareClearance() {
        Settlement capital = new Settlement(1, "Capital", new SimPosition(0, 0), 4000, 4500,
                SettlementOrigin.AUTHORED_SEED, false, dev.livingrealms.sim.faction.DevelopmentMode.AUTO,
                SettlementRole.CAPITAL);
        Settlement village = new Settlement(2, "Village", new SimPosition(0, 0), 180, 220,
                SettlementOrigin.AUTHORED_SEED, false, dev.livingrealms.sim.faction.DevelopmentMode.AUTO,
                SettlementRole.VILLAGE);
        check(PlayerSettlementFounder.requiredSpacing(capital) == 2500.0, "candidate capital ↔ capital floor");
        check(PlayerSettlementFounder.requiredSpacing(village) == 300.0, "candidate capital ↔ village floor");

        SimulationState state = new SimulationState(0xF00DL);
        DemoSeeder.seed(state);
        Settlement seededCapital = state.factions().getFirst().settlements().stream()
                .filter(s -> s.role() == SettlementRole.CAPITAL).findFirst().orElseThrow();
        var blocked = PlayerSettlementFounder.found(state, "player:near", "Near", "Tooclose", seededCapital.position());
        check(!blocked.success(), "must reject founding on capital");
        check(blocked.reason().contains("2500") || blocked.reason().contains("blocks"),
                "capital founding reason should expose its role-aware clearance: " + blocked.reason());
        var ok = PlayerSettlementFounder.found(state, "player:far", "Far", "Newcamp",
                new SimPosition(seededCapital.position().x() + 25_000, seededCapital.position().z() + 25_000));
        check(ok.success(), "far founding should work: " + ok.reason());
        check(state.findSettlement(ok.settlementId()).orElseThrow().role() == SettlementRole.CAPITAL,
                "player founding camp must retain CAPITAL role despite CAMP population tier");
    }

    private static void matrixPins() {
        check(SettlementSpacingPolicy.minimumDistance(SettlementRole.CAPITAL, SettlementRole.CAPITAL) == 2500.0, "capital-capital");
        check(SettlementSpacingPolicy.minimumDistance(SettlementRole.CAPITAL, SettlementRole.CITY) == 1600.0, "capital-city");
        check(SettlementSpacingPolicy.minimumDistance(SettlementRole.CAPITAL, SettlementRole.TOWN) == 650.0, "capital-town");
        check(SettlementSpacingPolicy.minimumDistance(SettlementRole.CAPITAL, SettlementRole.VILLAGE) == 300.0, "capital-village");
        check(SettlementSpacingPolicy.minimumDistance(SettlementRole.CAPITAL, SettlementRole.HAMLET) == 180.0, "capital-hamlet");
        check(SettlementSpacingPolicy.minimumDistance(SettlementRole.TOWN, SettlementRole.TOWN) == 650.0, "town-town");
        check(SettlementSpacingPolicy.minimumDistance(SettlementRole.VILLAGE, SettlementRole.VILLAGE) == 280.0, "village-village");
        check(SettlementSpacingPolicy.minimumDistance(SettlementRole.VILLAGE, SettlementRole.HAMLET) == 160.0, "village-hamlet");
        check(SettlementSpacingPolicy.minimumDistance(SettlementRole.HAMLET, SettlementRole.HAMLET) == 150.0, "hamlet-hamlet");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
