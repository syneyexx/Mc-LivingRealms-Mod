package dev.livingrealms;

import dev.livingrealms.sim.construction.HousingCapacity;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.DevelopmentModeGuard;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/**
 * Wave 46 — PLAYER_LED must not secretly receive automatic ordinary houses / abstract housing.
 */
public final class DevelopmentModeGuardTest {
    private DevelopmentModeGuardTest() {}

    public static void main(String[] args) {
        guardSemantics();
        playerLedPlannerEmitsNoHouses();
        playerLedFactionEngineNoAbstractHousing();
        hybridAllowsDeficitHouses();
        System.out.println("PASS DevelopmentModeGuard: PLAYER_LED / HYBRID / AUTO house emission audited");
    }

    private static void guardSemantics() {
        Settlement s = new Settlement(1, "Guardtown", new SimPosition(0, 0), 40, 10);
        s.setDevelopmentMode(DevelopmentMode.PLAYER_LED);
        check(!DevelopmentModeGuard.allowsAutoHousing(s), "PLAYER_LED blocks auto housing");
        check(!DevelopmentModeGuard.allowsOrdinaryHouseEmission(s), "PLAYER_LED blocks house emission");
        check(!DevelopmentModeGuard.allowsAbstractHousingGrowth(s), "PLAYER_LED blocks abstract growth");
        check(!DevelopmentModeGuard.allowsAutomaticResidentialDistrictFill(s), "PLAYER_LED blocks residential fill");
        check(DevelopmentModeGuard.allowsPublicInfrastructure(s), "PLAYER_LED still allows public infra");
        check(HousingCapacity.blocksAutoHousing(s), "HousingCapacity aligns with guard");

        s.setDevelopmentMode(DevelopmentMode.AUTO);
        check(DevelopmentModeGuard.allowsAutoHousing(s), "AUTO allows auto housing");
        check(DevelopmentModeGuard.allowsOrdinaryHouseEmission(s), "AUTO allows house emission");

        s.setDevelopmentMode(DevelopmentMode.HYBRID);
        s.setHousing(100);
        check(!DevelopmentModeGuard.allowsOrdinaryHouseEmission(s), "HYBRID without shortage emits no houses");
        s.setHousing(5);
        check(DevelopmentModeGuard.allowsOrdinaryHouseEmission(s), "HYBRID with shortage emits houses");
    }

    private static void playerLedPlannerEmitsNoHouses() {
        SimulationState state = new SimulationState(0xD46A01L);
        Faction faction = new Faction(state.nextId(), "Led Realm", "Player");
        Settlement settlement = new Settlement(state.nextId(), "Playerhold", new SimPosition(50, 50), 120, 20);
        settlement.setDevelopmentMode(DevelopmentMode.PLAYER_LED);
        faction.addSettlement(settlement);
        state.addFaction(faction);
        long houses = SettlementPlanner.plan(faction, settlement).stream()
                .filter(i -> i.role() == StructureRole.HOUSE).count();
        check(houses == 0, "PLAYER_LED planner emits 0 houses, got " + houses);
        long roads = SettlementPlanner.plan(faction, settlement).stream()
                .filter(i -> i.role() == StructureRole.ROAD).count();
        check(roads > 0, "PLAYER_LED still plans roads");
    }

    private static void playerLedFactionEngineNoAbstractHousing() {
        SimulationState state = new SimulationState(0xD46A02L);
        Faction faction = new Faction(state.nextId(), "Led Economy", "Player");
        Settlement settlement = new Settlement(state.nextId(), "Sparsehold", new SimPosition(80, 80), 80, 10);
        settlement.setDevelopmentMode(DevelopmentMode.PLAYER_LED);
        faction.addSettlement(settlement);
        faction.stockpile().add(ResourceType.WOOD, 500);
        faction.stockpile().add(ResourceType.STONE, 500);
        settlement.stockpile().add(ResourceType.WOOD, 200);
        settlement.stockpile().add(ResourceType.STONE, 200);
        state.addFaction(faction);
        int before = settlement.housing();
        state.factionEngine().simulateDay(state, new DeterministicRng(0xABCDEFL));
        check(settlement.housing() == before,
                "PLAYER_LED abstract housing unchanged: before=" + before + " after=" + settlement.housing());
    }

    private static void hybridAllowsDeficitHouses() {
        SimulationState state = new SimulationState(0xD46A03L);
        Faction faction = new Faction(state.nextId(), "Hybrid Realm", "Player");
        Settlement settlement = new Settlement(state.nextId(), "Hybriston", new SimPosition(90, 90), 80, 10);
        settlement.setDevelopmentMode(DevelopmentMode.HYBRID);
        faction.addSettlement(settlement);
        state.addFaction(faction);
        long houses = SettlementPlanner.plan(faction, settlement).stream()
                .filter(i -> i.role() == StructureRole.HOUSE).count();
        check(houses > 0, "HYBRID with shortage emits houses");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
