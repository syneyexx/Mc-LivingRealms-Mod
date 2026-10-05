package dev.livingrealms;

import dev.livingrealms.sim.construction.HousingCapacity;
import dev.livingrealms.sim.construction.PlayerStructureRegistration;
import dev.livingrealms.sim.construction.PlayerStructureValidator;
import dev.livingrealms.sim.construction.RegisteredPlayerStructure;
import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.player.PlayerActorIdentity;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.UUID;

/**
 * Wave 4 demolition regression: registered housing must raise capacity, enable immigration pressure
 * relief, then fall when the house is invalidated — including across save/reload.
 */
public final class HousingDemolitionRegressionTest {
    private HousingDemolitionRegressionTest() {}

    public static void main(String[] args) {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String actor = PlayerActorIdentity.of(uuid);
        SimulationState state = new SimulationState(0x484F5553L);
        var founded = PlayerSettlementFounder.found(state, actor, "Builder", "Demolishburg",
                new SimPosition(62_000, 62_000));
        check(founded.success(), founded.reason());
        var settlement = state.findSettlement(founded.settlementId()).orElseThrow();
        check(settlement.developmentMode() == DevelopmentMode.HYBRID, "HYBRID default");

        int foundingHousing = HousingCapacity.calculate(settlement, state);
        check(foundingHousing == PlayerSettlementFounder.FOUNDING_HOUSING, "founding temporary housing");

        int dx = (int) Math.round(settlement.position().x());
        int dz = (int) Math.round(settlement.position().z());
        var metrics = new PlayerStructureValidator.SurveyMetrics(
                8, 8, 4, 96, 400, 0.95, 0.90, 6, true, true, false, false);
        var registered = PlayerStructureRegistration.register(
                state, actor, settlement.id(), RegisteredPlayerStructure.Role.HOUSE,
                dx, 64, dz, dx + 7, 68, dz + 7, dx + 3, 64, dz, metrics, 0xBEEFL);
        check(registered.success(), registered.reason());
        int afterRegister = HousingCapacity.calculate(settlement, state);
        check(afterRegister > foundingHousing, "housing rises after registration");
        check(afterRegister == registered.capacity(), "effective equals registered capacity");
        check(settlement.housing() == afterRegister, "canonical field reconciled");

        // Immigration can use the new capacity (population below housing).
        check(settlement.population() <= afterRegister, "room for residents");
        check(HousingCapacity.housingShortage(settlement, state) == 0, "no shortage with house");

        // Demolish / invalidate.
        var invalidated = PlayerStructureRegistration.invalidate(state, registered.structureId(), "demolished");
        check(invalidated.success(), invalidated.reason());
        int afterDemolish = HousingCapacity.calculate(settlement, state);
        check(afterDemolish == PlayerSettlementFounder.FOUNDING_HOUSING,
                "housing falls back to founding temporary after demolition, got " + afterDemolish);
        check(settlement.housing() == afterDemolish, "canonical field decreased");
        check(HousingCapacity.playerRegistered(settlement, state) == 0, "player registered credit cleared");

        // Save/reload preserves the reduced capacity.
        byte[] bytes = SimulationStateCodec.encode(state);
        SimulationState loaded = SimulationStateCodec.decode(bytes);
        var loadedSettlement = loaded.findSettlement(settlement.id()).orElseThrow();
        check(HousingCapacity.calculate(loadedSettlement, loaded) == PlayerSettlementFounder.FOUNDING_HOUSING,
                "demolished capacity survives reload");
        RegisteredPlayerStructure restored = loaded.findRegisteredPlayerStructure(registered.structureId()).orElseThrow();
        check(!restored.valid() && restored.housingCredit() == 0, "invalid structure persists");

        // Duplicate registration of the same footprint must fail.
        restored.markValid(loaded.clock().day(), 0xBEEFL);
        restored.setCapacity(12);
        HousingCapacity.reconcileCanonical(loadedSettlement, loaded);
        var dup = PlayerStructureRegistration.register(
                loaded, actor, loadedSettlement.id(), RegisteredPlayerStructure.Role.HOUSE,
                dx, 64, dz, dx + 7, 68, dz + 7, dx + 3, 64, dz, metrics, 0xBEEF2L);
        check(!dup.success(), "duplicate registration rejected");

        System.out.println("PASS HousingDemolitionRegressionTest: register↑ demolish↓ save/reload + duplicate reject");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
