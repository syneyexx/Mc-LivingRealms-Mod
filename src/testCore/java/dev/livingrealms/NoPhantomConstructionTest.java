package dev.livingrealms;

import dev.livingrealms.sim.construction.ForeignSettlementAdoption;
import dev.livingrealms.sim.construction.SettlementConstructionPolicy;
import dev.livingrealms.sim.faction.ConstructionOrigin;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** E1/E2: seeder writes no completion keys; foreign adoption is non-productive. */
public final class NoPhantomConstructionTest {
    private NoPhantomConstructionTest() {}

    public static void main(String[] args) {
        seederWritesNoKeys();
        freshStarterBootstrapDayIsRuntimeQuiet();
        foreignAdoptedDoesNotProduceGrain();
        System.out.println("PASS no phantom construction: densifier empty + FOREIGN_ADOPTED non-productive");
    }

    private static void seederWritesNoKeys() {
        SimulationState state = new SimulationState(0xE1E1L);
        DemoSeeder.seed(state);
        for (Faction f : state.factions()) {
            if (f.name().equals("Wizard Trees")) continue;
            for (Settlement s : f.settlements()) {
                for (String key : s.completedConstruction()) {
                    check(!key.startsWith("farm:") && !key.startsWith("pasture:") && !key.startsWith("well:"),
                            "seeder phantom key " + key + " on " + s.name());
                }
            }
        }
    }


    private static void freshStarterBootstrapDayIsRuntimeQuiet() {
        Settlement starter = new Settlement(9001, "Starter", new SimPosition(0, 0), 300, 320);
        starter.markPhysicallyAnchored();
        check(!SettlementConstructionPolicy.allowsRuntimeConstruction(starter, true, 0),
                "true-worldgen authored starter must be runtime-quiet on day zero");
        check(SettlementConstructionPolicy.allowsRuntimeConstruction(starter, true, 1),
                "authored starter must re-enter runtime construction after day zero");

        Settlement playerFounded = new Settlement(
                9002, "Player Town", new SimPosition(100, 0), 20, 24,
                dev.livingrealms.sim.faction.SettlementOrigin.PLAYER_FOUNDED,
                true,
                dev.livingrealms.sim.faction.DevelopmentMode.AUTO);
        check(SettlementConstructionPolicy.allowsRuntimeConstruction(playerFounded, true, 0),
                "player-founded settlement must not be blocked by starter day-zero gate");
    }

    private static void foreignAdoptedDoesNotProduceGrain() {
        SimulationState state = new SimulationState(0xE2E2L);
        Faction f = new Faction(state.nextId(), "Adopted Realm", "Mayor");
        Settlement s = new Settlement(state.nextId(), "Adopted Hamlet", new SimPosition(50, 50), 90, 110);
        f.addSettlement(s);
        state.addFaction(f);
        ForeignSettlementAdoption.preserveExistingInfrastructure(state, f, s);
        check(s.countProductionPrefix("farm:") == 0, "FOREIGN_ADOPTED farms must not count for production");
        for (String key : s.completedConstruction()) {
            if (key.startsWith("farm:") || key.startsWith("house:") || key.startsWith("keep:") || key.startsWith("well:")
                    || key.startsWith("market:") || key.startsWith("temple:") || key.equals("foreign:adopted_footprint")) {
                check(s.constructionOrigin(key) == ConstructionOrigin.FOREIGN_ADOPTED, "origin " + key);
            }
        }
        s.stockpile().set(ResourceType.GRAIN, 0);
        s.stockpile().set(ResourceType.BREAD, 0);
        s.stockpile().set(ResourceType.FOOD, 0);
        state.advanceDays(15);
        check(s.stockpile().get(ResourceType.GRAIN) < s.population() * 0.20 * 4,
                "adopted village must not mint farm-scale GRAIN");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
