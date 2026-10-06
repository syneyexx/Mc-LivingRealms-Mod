package dev.livingrealms;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SettlementSpacingPolicy;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import dev.livingrealms.sim.world.StarterCultureTraits;
import java.util.List;

/** Determinism and bootstrap parity gate for the shared fresh-world starter layout authority. */
public final class StarterCivilizationLayoutPlannerTest {
    private StarterCivilizationLayoutPlannerTest() {}

    public static void main(String[] args) {
        sameSeedProducesSameLayout();
        differentSeedsVaryWithoutBreakingHierarchy();
        simulationBootstrapConsumesExactPlan();
        System.out.println("PASS starter civilization layout: deterministic + seed-varied + bootstrap-parity");
    }

    private static void sameSeedProducesSameLayout() {
        var a = StarterCivilizationLayoutPlanner.plan(0x51A7E5L);
        var b = StarterCivilizationLayoutPlanner.plan(0x51A7E5L);
        check(a.equals(b), "same seed/content must produce identical starter layout");
        check(a.layoutVersion() == StarterCivilizationLayoutPlanner.LAYOUT_VERSION, "layout version");
    }

    private static void differentSeedsVaryWithoutBreakingHierarchy() {
        var a = StarterCivilizationLayoutPlanner.plan(10L);
        var b = StarterCivilizationLayoutPlanner.plan(11L);
        check(!a.equals(b), "different seeds should vary starter layout");
        check(a.realms().size() == 12 && b.realms().size() == 12, "surface realm count");
        for (var realm : a.realms()) {
            long capitals = realm.settlements().stream().filter(s -> s.role() == SettlementRole.CAPITAL).count();
            long towns = realm.settlements().stream().filter(s -> s.role() == SettlementRole.TOWN).count();
            long villages = realm.settlements().stream().filter(s -> s.role() == SettlementRole.VILLAGE).count();
            long hamlets = realm.settlements().stream().filter(s -> s.role() == SettlementRole.HAMLET).count();
            check(capitals == 1, realm.definition().id() + " capital count");
            check(towns >= 2 && towns <= 4, realm.definition().id() + " town count " + towns);
            check(villages >= 6 && villages <= 8, realm.definition().id() + " village count " + villages);
            check(hamlets >= 6 && hamlets <= 14, realm.definition().id() + " hamlet count " + hamlets);
        }
        List<StarterCivilizationLayoutPlanner.SettlementPlan> all = a.settlements();
        for (int i = 0; i < all.size(); i++) for (int j = i + 1; j < all.size(); j++) {
            var left = all.get(i);
            var right = all.get(j);
            double floor = SettlementSpacingPolicy.minimumDistance(left.role(), right.role());
            check(left.position().distanceTo(right.position()) >= floor - 1.0,
                    "starter spacing floor violated: " + left.name() + " / " + right.name());
        }
    }

    private static void simulationBootstrapConsumesExactPlan() {
        long seed = 0xC1A17L;
        var layout = StarterCivilizationLayoutPlanner.plan(seed);
        SimulationState state = new SimulationState(seed);
        DemoSeeder.seed(state);
        for (var realmPlan : layout.realms()) {
            Faction faction = state.findFaction(realmPlan.factionId()).orElseThrow();
            check(faction.name().equals(realmPlan.definition().displayName()), "faction identity mismatch");
            StarterCultureTraits.resolve(realmPlan.definition()).ifPresent(expected -> {
                var actual = state.findFactionCivilization(realmPlan.factionId()).orElseThrow();
                check(close(actual.mercantileTradition(), expected.mercantile()), "mercantile starter trait mismatch");
                check(close(actual.martialTradition(), expected.martial()), "martial starter trait mismatch");
                check(close(actual.agrarianTradition(), expected.agrarian()), "agrarian starter trait mismatch");
                check(close(actual.artisticTradition(), expected.artistic()), "artistic starter trait mismatch");
            });
            for (var starter : realmPlan.settlements()) {
                Settlement settlement = state.findSettlement(starter.id()).orElseThrow();
                check(settlement.name().equals(starter.name()), "settlement name mismatch " + starter.stableKey());
                check(settlement.position().equals(starter.position()), "settlement position mismatch " + starter.stableKey());
                check(settlement.role() == starter.role(), "settlement role mismatch " + starter.stableKey());
            }
        }
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) < 1.0e-9;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
