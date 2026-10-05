package dev.livingrealms;

import dev.livingrealms.sim.construction.HousingCapacity;
import dev.livingrealms.sim.construction.PlayerStructureRegistration;
import dev.livingrealms.sim.construction.PlayerStructureRevalidation;
import dev.livingrealms.sim.construction.PlayerStructureValidator;
import dev.livingrealms.sim.construction.RegisteredPlayerStructure;
import dev.livingrealms.sim.player.PlayerActorIdentity;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.UUID;

/** Wave 3: dirty-queue revalidation can reduce capacity without force-loading. */
public final class PlayerStructureRevalidationTest {
    private PlayerStructureRevalidationTest() {}

    public static void main(String[] args) {
        String actor = PlayerActorIdentity.of(UUID.fromString("99999999-8888-7777-6666-555555555555"));
        SimulationState state = new SimulationState(0x52455641L);
        var founded = PlayerSettlementFounder.found(state, actor, "Rev", "Revalton",
                new SimPosition(80_000, 80_000));
        check(founded.success(), founded.reason());
        var settlement = state.findSettlement(founded.settlementId()).orElseThrow();
        int dx = (int) Math.round(settlement.position().x());
        int dz = (int) Math.round(settlement.position().z());
        var metrics = new PlayerStructureValidator.SurveyMetrics(
                8, 8, 5, 96, 400, 0.95, 0.9, 6, true, true, false, false);
        var reg = PlayerStructureRegistration.register(
                state, actor, settlement.id(), RegisteredPlayerStructure.Role.HOUSE,
                dx, 64, dz, dx + 7, 70, dz + 7, dx + 3, 64, dz, metrics, 0x77L);
        check(reg.success(), reg.reason());
        int high = HousingCapacity.calculate(settlement, state);
        check(high > PlayerSettlementFounder.FOUNDING_HOUSING, "registered capacity");

        // Block edit inside footprint marks dirty.
        int marked = PlayerStructureRevalidation.markDirtyAt(state, dx + 2, 65, dz + 2);
        check(marked == 1, "structure marked dirty");
        check(state.playerStructureRevalidationPending() == 1, "queued");

        long[] ids = PlayerStructureRevalidation.pollDirty(state, 4);
        check(ids.length == 1 && ids[0] == reg.structureId(), "poll returns structure");

        // Capacity drop after beds removed (still enclosed).
        var reduced = new PlayerStructureValidator.SurveyMetrics(
                8, 8, 5, 96, 400, 0.95, 0.9, 1, true, true, false, false);
        var applied = PlayerStructureRevalidation.applySurvey(state, reg.structureId(), reduced, 0x78L, false);
        check(applied.success(), applied.reason());
        int mid = HousingCapacity.calculate(settlement, state);
        check(mid < high && mid > 0, "capacity decreased but still valid");

        // Total failure invalidates and housing falls to founding temporary.
        var broken = new PlayerStructureValidator.SurveyMetrics(
                8, 8, 5, 96, 400, 0.1, 0.9, 0, false, true, false, false);
        PlayerStructureRevalidation.applySurvey(state, reg.structureId(), broken, 0x79L, false);
        check(HousingCapacity.calculate(settlement, state) == PlayerSettlementFounder.FOUNDING_HOUSING,
                "invalidation restores founding temporary");

        // Deferred unloaded must not invalidate.
        RegisteredPlayerStructure s = state.findRegisteredPlayerStructure(reg.structureId()).orElseThrow();
        s.markValid(state.clock().day(), 0x7AL);
        s.setCapacity(10);
        HousingCapacity.reconcileCanonical(settlement, state);
        int beforeDefer = HousingCapacity.calculate(settlement, state);
        PlayerStructureRevalidation.applySurvey(state, reg.structureId(), broken, 0x7BL, true);
        check(HousingCapacity.calculate(settlement, state) == beforeDefer, "unloaded defer keeps validity");

        System.out.println("PASS PlayerStructureRevalidationTest");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
