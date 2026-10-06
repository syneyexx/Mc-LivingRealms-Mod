package dev.livingrealms;

import dev.livingrealms.sim.construction.HousingCapacity;
import dev.livingrealms.sim.construction.PlayerStructureRegistration;
import dev.livingrealms.sim.construction.RegisteredPlayerStructure;
import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Wave 5: registered player houses contribute housing without mutating player blocks. */
public final class PlayerStructureRegistrationTest {
    private PlayerStructureRegistrationTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0x504C4159L);
        var founded = PlayerSettlementFounder.found(state, "player:builder", "Builder", "Homestead",
                new SimPosition(55_000, 55_000));
        check(founded.success(), founded.reason());
        var settlement = state.findSettlement(founded.settlementId()).orElseThrow();
        check(settlement.origin() == SettlementOrigin.PLAYER_FOUNDED, "player founded");
        check(settlement.developmentMode() == DevelopmentMode.HYBRID, "HYBRID default");
        check(settlement.population() == PlayerSettlementFounder.FOUNDING_POPULATION, "founder camp pop");

        int before = HousingCapacity.calculate(settlement, state);
        int dx = (int) Math.round(settlement.position().x());
        int dz = (int) Math.round(settlement.position().z());
        RegisteredPlayerStructure house = new RegisteredPlayerStructure(
                state.nextId(), settlement.id(), "player:builder", RegisteredPlayerStructure.Role.HOUSE,
                dx, 64, dz, dx + 8, 72, dz + 8, dx + 4, 64, dz, 12, state.clock().day(), 0xABCDL);
        state.addRegisteredPlayerStructure(house);
        HousingCapacity.reconcileCanonical(settlement, state);
        int after = HousingCapacity.calculate(settlement, state);
        check(after > before && after >= 12, "registered house increases housing capacity");

        PlayerStructureRegistration.invalidate(state, house.id(), "test_demolish");
        check(HousingCapacity.calculate(settlement, state) == PlayerSettlementFounder.FOUNDING_HOUSING,
                "invalid house loses capacity back to founding temporary");

        // Re-register via markValid path + reconcile
        house.markValid(state.clock().day(), 0xABCEL);
        house.setCapacity(12);
        HousingCapacity.reconcileCanonical(settlement, state);
        byte[] bytes = SimulationStateCodec.encode(state);
        SimulationState loaded = SimulationStateCodec.decode(bytes);
        RegisteredPlayerStructure restored = loaded.findRegisteredPlayerStructure(house.id()).orElseThrow();
        check(restored.valid() && restored.capacity() == 12, "registration survives schema 21");
        check(restored.role() == RegisteredPlayerStructure.Role.HOUSE, "role preserved");
        System.out.println("PASS PlayerStructureRegistrationTest");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
