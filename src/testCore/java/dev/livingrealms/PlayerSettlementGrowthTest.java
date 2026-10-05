package dev.livingrealms;

import dev.livingrealms.sim.construction.HousingCapacity;
import dev.livingrealms.sim.construction.PlayerStructureRegistration;
import dev.livingrealms.sim.construction.PlayerStructureValidator;
import dev.livingrealms.sim.construction.RegisteredPlayerStructure;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Wave 5: founder camp, registered housing, PLAYER_LED / HYBRID house emission. */
public final class PlayerSettlementGrowthTest {
    private PlayerSettlementGrowthTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0x47524F57L);
        var founded = PlayerSettlementFounder.found(state, "player:growth", "Growth", "Foundercamp",
                new SimPosition(70_000, 70_000));
        check(founded.success(), founded.reason());
        Settlement settlement = state.findSettlement(founded.settlementId()).orElseThrow();
        Faction faction = state.findFaction(founded.factionId()).orElseThrow();
        check(settlement.origin() == SettlementOrigin.PLAYER_FOUNDED, "player founded");
        check(settlement.population() == PlayerSettlementFounder.FOUNDING_POPULATION, "founder camp pop");
        check(settlement.housing() == PlayerSettlementFounder.FOUNDING_HOUSING, "founder camp housing");
        check(settlement.developmentMode() == DevelopmentMode.HYBRID, "HYBRID default");

        // HYBRID with no shortage: planner emits no houses.
        settlement.setHousing(settlement.population() + 10);
        long hybridHouses = SettlementPlanner.plan(faction, settlement).stream()
                .filter(i -> i.role() == StructureRole.HOUSE).count();
        check(hybridHouses == 0, "HYBRID without shortage emits no LR houses");

        // PLAYER_LED never emits houses.
        settlement.setDevelopmentMode(DevelopmentMode.PLAYER_LED);
        settlement.setHousing(2);
        settlement.addPopulation(40);
        long playerLedHouses = SettlementPlanner.plan(faction, settlement).stream()
                .filter(i -> i.role() == StructureRole.HOUSE).count();
        check(playerLedHouses == 0, "PLAYER_LED suppresses house spawn");

        // Registration increases verified housing.
        settlement.setDevelopmentMode(DevelopmentMode.HYBRID);
        int before = HousingCapacity.calculate(settlement, state);
        var metrics = new PlayerStructureValidator.SurveyMetrics(
                8, 8, 5, 96, 400, 0.9, 0.85, 6, true, true, false, false);
        int dx = (int) Math.round(settlement.position().x());
        int dz = (int) Math.round(settlement.position().z());
        var reg = PlayerStructureRegistration.register(
                state, "player:growth", settlement.id(), RegisteredPlayerStructure.Role.HOUSE,
                dx, 64, dz, dx + 8, 72, dz + 8, dx + 4, 64, dz, metrics, 0x11L);
        check(reg.success(), reg.reason());
        int after = HousingCapacity.calculate(settlement, state);
        check(after > before, "registered house increases verified capacity");

        var mode = PlayerStructureRegistration.setDevelopmentMode(
                state, "player:growth", settlement.id(), "PLAYER_LED");
        check(mode.success(), mode.reason());
        check(settlement.developmentMode() == DevelopmentMode.PLAYER_LED, "mode switched");

        // Open shell fails validation.
        var bad = PlayerStructureValidator.validate(RegisteredPlayerStructure.Role.HOUSE,
                new PlayerStructureValidator.SurveyMetrics(8, 8, 5, 80, 400, 0.2, 0.85, 2, false, true, false, false));
        check(!bad.ok(), "open/roofless shell rejected");

        System.out.println("PASS PlayerSettlementGrowthTest");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
